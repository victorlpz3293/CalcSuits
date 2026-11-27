package com.calcsuits.features.ai.ocr

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class OcrRecognitionOutput(
    val rawLatex: String,
    val totalMs: Long,
    val preprocessMs: Long,
    val encoderMs: Long,
    val decoderMs: Long,
    val nativeHeapMb: Double,
    val jvmHeapMb: Double,
    val polarityInverted: Boolean,
    val usesKvCache: Boolean,
    val sampleSize: Int,
    val generatedTokens: Int = 0,
)

interface OcrEngineHandle : Closeable {
    val isClosed: Boolean
    fun recognizeGray(
        grayImage: TexTellerImagePreprocessor.GrayImage,
        isCancelled: () -> Boolean,
    ): OcrRecognitionOutput
}

interface OcrRepository {
    fun isModelReady(): Boolean
    fun isEngineLoaded(): Boolean
    suspend fun downloadModels(onProgress: (OcrModelManager.DownloadProgress) -> Unit)
    suspend fun recognizeFromUri(uri: Uri): OcrRecognitionOutput
    suspend fun recognizeFromTempFile(tempFile: File): OcrRecognitionOutput
    suspend fun releaseResources()
}

/**
 * Coordinates strictly local, offline-capable OCR execution with:
 * - Pre-load SHA-256 verification before opening ONNX Runtime sessions.
 * - Background execution on IO/Default dispatchers (never blocks Main thread).
 * - Guaranteed temporary image cleanup in `finally` blocks even on cancellation/errors.
 * - Automatic native session release (`OrtSession.close()`) on inactivity timeout or explicit exit.
 */
@Singleton
class LocalOcrRepository(
    private val context: Context,
    private val modelManager: OcrModelManager,
    private val engineFactory: (File) -> OcrEngineHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) : OcrRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        modelManager: OcrModelManager,
    ) : this(
        context = context,
        modelManager = modelManager,
        engineFactory = { dir -> RealTexTellerEngineHandle(TexTellerOnnxEngine.loadVerified(dir)) },
    )

    private val mutex = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var activeEngine: OcrEngineHandle? = null
    private var idleReleaseJob: Job? = null

    override fun isModelReady(): Boolean =
        modelManager.checkQuickAvailability() is OcrModelManager.Availability.Ready

    override fun isEngineLoaded(): Boolean {
        val current = activeEngine
        return current != null && !current.isClosed
    }

    override suspend fun downloadModels(onProgress: (OcrModelManager.DownloadProgress) -> Unit) {
        releaseResources()
        modelManager.downloadMissingArtifacts(onProgress = onProgress)
    }

    override suspend fun recognizeFromUri(uri: Uri): OcrRecognitionOutput {
        val decoded = withContext(ioDispatcher) {
            OcrImageDecoder.decodeUriToGray(context.contentResolver, uri)
        }
        return runOnGray(decoded)
    }

    override suspend fun recognizeFromTempFile(tempFile: File): OcrRecognitionOutput {
        try {
            val decoded = withContext(ioDispatcher) {
                OcrImageDecoder.decodeFileToGray(tempFile)
            }
            return runOnGray(decoded)
        } finally {
            OcrTempFileManager.deleteSafely(tempFile)
        }
    }

    internal suspend fun recognizeDecodedForTest(
        decoded: OcrImageDecoder.DecodedGrayResult,
    ): OcrRecognitionOutput = runOnGray(decoded)

    private suspend fun runOnGray(
        decoded: OcrImageDecoder.DecodedGrayResult,
    ): OcrRecognitionOutput {
        val callerContext = currentCoroutineContext()
        return mutex.withLock {
            idleReleaseJob?.cancel()
            idleReleaseJob = null

            val engine = ensureEngineLoadedLocked()
            try {
                withContext(computeDispatcher) {
                    engine.recognizeGray(
                        grayImage = decoded.grayImage,
                        isCancelled = { !callerContext.isActive },
                    ).copy(sampleSize = decoded.sampleSize)
                }
            } catch (e: CancellationException) {
                closeEngineLocked()
                throw e
            } finally {
                if (activeEngine != null && !activeEngine!!.isClosed) {
                    scheduleIdleReleaseLocked()
                }
            }
        }
    }

    private suspend fun ensureEngineLoadedLocked(): OcrEngineHandle {
        val existing = activeEngine
        if (existing != null && !existing.isClosed) {
            return existing
        }
        val modelDir = modelManager.getModelsDir()
        val loaded = withContext(ioDispatcher) {
            engineFactory(modelDir)
        }
        activeEngine = loaded
        return loaded
    }

    private fun scheduleIdleReleaseLocked() {
        idleReleaseJob?.cancel()
        if (idleTimeoutMs <= 0L) return
        idleReleaseJob = lifecycleScope.launch {
            delay(idleTimeoutMs)
            releaseResources()
        }
    }

    override suspend fun releaseResources() {
        mutex.withLock {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            closeEngineLocked()
        }
    }

    private fun closeEngineLocked() {
        val engine = activeEngine
        activeEngine = null
        if (engine != null) {
            runCatching { engine.close() }
        }
    }

    private class RealTexTellerEngineHandle(
        private val delegate: TexTellerOnnxEngine,
    ) : OcrEngineHandle {
        override val isClosed: Boolean
            get() = delegate.isClosed

        override fun recognizeGray(
            grayImage: TexTellerImagePreprocessor.GrayImage,
            isCancelled: () -> Boolean,
        ): OcrRecognitionOutput {
            val res = delegate.recognizeGray(
                grayImage = grayImage,
                isCancelled = isCancelled,
            )
            return OcrRecognitionOutput(
                rawLatex = res.rawLatex,
                totalMs = res.totalMs,
                preprocessMs = res.preprocessMs,
                encoderMs = res.encoderMs,
                decoderMs = res.decoderMs,
                nativeHeapMb = res.nativeHeapMb,
                jvmHeapMb = res.jvmHeapMb,
                polarityInverted = res.polarityInverted,
                usesKvCache = res.usesKvCache,
                sampleSize = 1,
                generatedTokens = res.tokenIds.size,
            )
        }

        override fun close() {
            delegate.close()
        }
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 60_000L
    }
}
