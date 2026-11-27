package com.calcsuits.features.ai.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Debug
import android.os.SystemClock
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production on-device ONNX Runtime engine for TexTeller3-ONNX (q4f16) with REAL KV-cache
 * (`decoder_model_merged_q4f16.onnx`, 12 layers x 16 heads x 64 head_dim, `use_cache_branch`).
 *
 * Security & memory invariants:
 * - Verifies SHA-256 of ALL 4 artifacts BEFORE creating any `OrtSession` or parsing tokenizer JSON.
 * - Closes every per-step `OrtSession.Result` and intermediate KV-cache `OnnxTensor` deterministically.
 * - Supports cooperative cancellation between decoding steps.
 * - Idempotent `close()` releases native encoder and decoder sessions immediately.
 */
class TexTellerOnnxEngine private constructor(
    private val env: OrtEnvironment,
    private val encoderSession: OrtSession,
    private val decoderSession: OrtSession,
    private val decoder: ByteLevelBpeDecoder,
    val verifiedArtifacts: Map<String, Sha256Verifier.VerifiedArtifact>,
    val verifyMs: Long,
    val loadMs: Long,
    val providerUsed: String,
    val usesKvCache: Boolean,
) : Closeable {

    private val closed = AtomicBoolean(false)

    data class RecognitionResult(
        val rawLatex: String,
        val tokenIds: List<Int>,
        val preprocessMs: Long,
        val encoderMs: Long,
        val decoderMs: Long,
        val totalMs: Long,
        val nativeHeapMb: Double,
        val jvmHeapMb: Double,
        val stoppedByRepeatGuard: Boolean,
        val polarityInverted: Boolean,
        val borderMedianLuminance: Int,
        val usesKvCache: Boolean,
    )

    val isClosed: Boolean
        get() = closed.get()

    fun recognizeGray(
        grayImage: TexTellerImagePreprocessor.GrayImage,
        maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
        isCancelled: () -> Boolean = { false },
    ): RecognitionResult {
        check(!closed.get()) { "TexTellerOnnxEngine has already been closed." }
        if (isCancelled()) throw CancellationException("OCR inference canceled before preprocessing.")

        val tStart = SystemClock.elapsedRealtime()
        val prep = TexTellerImagePreprocessor.preprocess(
            raw = grayImage,
            enableAutoPolarityInvert = true,
            enableGentleDarkStretch = true,
        )
        val tPre = SystemClock.elapsedRealtime()
        if (isCancelled()) throw CancellationException("OCR inference canceled after preprocessing.")

        val encInputName = encoderSession.inputNames.first()
        val encOutputName = encoderSession.outputNames.first()

        val generated = ArrayList<Int>(maxNewTokens + 1)
        generated.add(decoder.bosTokenId)
        var stoppedByRepeatGuard = false
        var tEncDone = tPre

        OnnxTensor.createTensor(env, FloatBuffer.wrap(prep.data), prep.shape).use { pixelTensor ->
            encoderSession.run(mapOf(encInputName to pixelTensor)).use { encOut ->
                tEncDone = SystemClock.elapsedRealtime()
                if (isCancelled()) throw CancellationException("OCR inference canceled after encoder.")
                val encoderHidden = encOut[encOutputName].get() as OnnxTensor

                stoppedByRepeatGuard = if (usesKvCache) {
                    decodeWithKvCache(
                        encoderHidden = encoderHidden,
                        generated = generated,
                        maxNewTokens = maxNewTokens,
                        isCancelled = isCancelled,
                    )
                } else {
                    decodeWithoutKvCache(
                        encoderHidden = encoderHidden,
                        generated = generated,
                        maxNewTokens = maxNewTokens,
                        isCancelled = isCancelled,
                    )
                }
            }
        }

        val tEnd = SystemClock.elapsedRealtime()
        val rawLatex = decoder.decode(generated.drop(1), skipSpecialTokens = true)
        val runtime = Runtime.getRuntime()
        val jvmHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)

        return RecognitionResult(
            rawLatex = rawLatex,
            tokenIds = generated,
            preprocessMs = tPre - tStart,
            encoderMs = tEncDone - tPre,
            decoderMs = tEnd - tEncDone,
            totalMs = tEnd - tStart,
            nativeHeapMb = nativeHeapMb,
            jvmHeapMb = jvmHeapMb,
            stoppedByRepeatGuard = stoppedByRepeatGuard,
            polarityInverted = prep.polarityInverted,
            borderMedianLuminance = prep.borderMedianLuminance,
            usesKvCache = usesKvCache,
        )
    }

    private fun decodeWithKvCache(
        encoderHidden: OnnxTensor,
        generated: ArrayList<Int>,
        maxNewTokens: Int,
        isCancelled: () -> Boolean,
    ): Boolean {
        val decoderK = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
        val decoderV = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
        val encoderK = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
        val encoderV = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)

        fun closeTensorArray(arr: Array<OnnxTensor?>) {
            for (i in arr.indices) {
                runCatching { arr[i]?.close() }
                arr[i] = null
            }
        }

        var repeatCount = 0
        var stoppedByRepeatGuard = false

        try {
            val emptyShape = longArrayOf(1L, NUM_HEADS.toLong(), 0L, HEAD_DIM.toLong())
            for (layer in 0 until NUM_DECODER_LAYERS) {
                decoderK[layer] = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
                decoderV[layer] = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
                encoderK[layer] = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
                encoderV[layer] = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape)
            }

            val decOutputName = "logits"
            val feeds = HashMap<String, OnnxTensor>(NUM_DECODER_LAYERS * 4 + 4)

            for (step in 0 until maxNewTokens) {
                if (isCancelled()) throw CancellationException("OCR decoding canceled at step $step.")
                val useCache = step > 0
                val lastTokenId = generated.last().toLong()

                val idsTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(lastTokenId)),
                    longArrayOf(1L, 1L),
                )
                val branchTensor = OnnxTensor.createTensor(env, booleanArrayOf(useCache))

                try {
                    feeds.clear()
                    feeds["input_ids"] = idsTensor
                    feeds["encoder_hidden_states"] = encoderHidden
                    feeds["use_cache_branch"] = branchTensor
                    for (layer in 0 until NUM_DECODER_LAYERS) {
                        feeds["past_key_values.$layer.decoder.key"] = decoderK[layer]!!
                        feeds["past_key_values.$layer.decoder.value"] = decoderV[layer]!!
                        feeds["past_key_values.$layer.encoder.key"] = encoderK[layer]!!
                        feeds["past_key_values.$layer.encoder.value"] = encoderV[layer]!!
                    }

                    decoderSession.run(feeds).use { decOut ->
                        val logitsTensor = decOut[decOutputName].get() as OnnxTensor
                        val bestId = argmaxLastToken(logitsTensor, seqLen = 1)

                        if (generated.last() == bestId) {
                            repeatCount++
                        } else {
                            repeatCount = 0
                        }
                        generated.add(bestId)

                        if (bestId == decoder.eosTokenId) {
                            return false
                        }
                        if (repeatCount >= REPEAT_GUARD_LIMIT) {
                            stoppedByRepeatGuard = true
                            return true
                        }

                        // Copy next-step decoder KV tensors before closing decOut to prevent native leaks.
                        val nextDecK = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
                        val nextDecV = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
                        try {
                            for (layer in 0 until NUM_DECODER_LAYERS) {
                                nextDecK[layer] = cloneFloatTensor(
                                    decOut["present.$layer.decoder.key"].get() as OnnxTensor
                                )
                                nextDecV[layer] = cloneFloatTensor(
                                    decOut["present.$layer.decoder.value"].get() as OnnxTensor
                                )
                            }
                            if (!useCache) {
                                val nextEncK = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
                                val nextEncV = arrayOfNulls<OnnxTensor>(NUM_DECODER_LAYERS)
                                try {
                                    for (layer in 0 until NUM_DECODER_LAYERS) {
                                        nextEncK[layer] = cloneFloatTensor(
                                            decOut["present.$layer.encoder.key"].get() as OnnxTensor
                                        )
                                        nextEncV[layer] = cloneFloatTensor(
                                            decOut["present.$layer.encoder.value"].get() as OnnxTensor
                                        )
                                    }
                                    closeTensorArray(encoderK)
                                    closeTensorArray(encoderV)
                                    for (layer in 0 until NUM_DECODER_LAYERS) {
                                        encoderK[layer] = nextEncK[layer]
                                        encoderV[layer] = nextEncV[layer]
                                    }
                                } catch (e: Throwable) {
                                    closeTensorArray(nextEncK)
                                    closeTensorArray(nextEncV)
                                    throw e
                                }
                            }
                            closeTensorArray(decoderK)
                            closeTensorArray(decoderV)
                            for (layer in 0 until NUM_DECODER_LAYERS) {
                                decoderK[layer] = nextDecK[layer]
                                decoderV[layer] = nextDecV[layer]
                            }
                        } catch (e: Throwable) {
                            closeTensorArray(nextDecK)
                            closeTensorArray(nextDecV)
                            throw e
                        }
                    }
                } finally {
                    runCatching { idsTensor.close() }
                    runCatching { branchTensor.close() }
                }
            }
            return stoppedByRepeatGuard
        } finally {
            closeTensorArray(decoderK)
            closeTensorArray(decoderV)
            closeTensorArray(encoderK)
            closeTensorArray(encoderV)
        }
    }

    private fun decodeWithoutKvCache(
        encoderHidden: OnnxTensor,
        generated: ArrayList<Int>,
        maxNewTokens: Int,
        isCancelled: () -> Boolean,
    ): Boolean {
        val decInputNames = decoderSession.inputNames.toList()
        val decOutputName = decoderSession.outputNames.first()
        val inputIdsName = decInputNames.firstOrNull { it.contains("input_ids") } ?: decInputNames[0]
        val hiddenName = decInputNames.firstOrNull { it.contains("encoder") || it.contains("hidden") } ?: decInputNames[1]
        var repeatCount = 0

        for (step in 0 until maxNewTokens) {
            if (isCancelled()) throw CancellationException("OCR decoding canceled at step $step.")
            val seqLen = generated.size
            val idsBuf = LongArray(seqLen) { generated[it].toLong() }
            val bestId = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(idsBuf),
                longArrayOf(1L, seqLen.toLong()),
            ).use { idsTensor ->
                decoderSession.run(
                    mapOf(
                        inputIdsName to idsTensor,
                        hiddenName to encoderHidden,
                    ),
                ).use { decOut ->
                    val logitsTensor = decOut[decOutputName].get() as OnnxTensor
                    argmaxLastToken(logitsTensor, seqLen)
                }
            }
            if (generated.last() == bestId) repeatCount++ else repeatCount = 0
            generated.add(bestId)
            if (bestId == decoder.eosTokenId) return false
            if (repeatCount >= REPEAT_GUARD_LIMIT) return true
        }
        return false
    }

    private fun cloneFloatTensor(source: OnnxTensor): OnnxTensor {
        val shape = source.info.shape
        val srcBuf = source.floatBuffer
        val remaining = srcBuf.remaining()
        val copy = FloatArray(remaining)
        srcBuf.get(copy)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(copy), shape)
    }

    private fun argmaxLastToken(logitsTensor: OnnxTensor, seqLen: Int): Int {
        val shape = logitsTensor.info.shape
        val vocabSize = shape.last().toInt()
        val buf = logitsTensor.floatBuffer
        val offset = (seqLen - 1) * vocabSize
        var bestId = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (v in 0 until vocabSize) {
            val value = buf.get(offset + v)
            if (value > bestVal) {
                bestVal = value
                bestId = v
            }
        }
        return bestId
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { encoderSession.close() }
            runCatching { decoderSession.close() }
        }
    }

    companion object {
        const val DEFAULT_MAX_NEW_TOKENS = 80
        const val REPEAT_GUARD_LIMIT = 5
        const val NUM_DECODER_LAYERS = 12
        const val NUM_HEADS = 16
        const val HEAD_DIM = 64

        fun loadVerified(
            modelDir: File,
            intraOpThreads: Int = 4,
            tryXnnpack: Boolean = true,
        ): TexTellerOnnxEngine {
            val tVerify0 = SystemClock.elapsedRealtime()
            val verified = Sha256Verifier.verifyDirectory(modelDir, TexTellerManifest.EXPECTED_SHA256)
            val verifyMs = SystemClock.elapsedRealtime() - tVerify0

            val tLoad0 = SystemClock.elapsedRealtime()
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(intraOpThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            var providerUsed = "CPU"
            if (tryXnnpack) {
                runCatching {
                    opts.addXnnpack(mapOf("intra_op_num_threads" to intraOpThreads.toString()))
                    providerUsed = "XNNPACK+CPU"
                }
            }

            val encoderFile = verified.getValue(TexTellerManifest.ENCODER_FILE).file
            val decoderFile = verified.getValue(TexTellerManifest.DECODER_MERGED_FILE).file
            val tokenizerFile = verified.getValue(TexTellerManifest.TOKENIZER_FILE).file
            val tokenizerConfigFile = verified.getValue(TexTellerManifest.TOKENIZER_CONFIG_FILE).file

            var encoderSession: OrtSession? = null
            var decoderSession: OrtSession? = null
            try {
                encoderSession = env.createSession(encoderFile.absolutePath, opts)
                decoderSession = env.createSession(decoderFile.absolutePath, opts)
                val bpeDecoder = ByteLevelBpeDecoder.fromFiles(tokenizerFile, tokenizerConfigFile)
                val usesKvCache = decoderSession.inputNames.contains("use_cache_branch")
                val loadMs = SystemClock.elapsedRealtime() - tLoad0
                return TexTellerOnnxEngine(
                    env = env,
                    encoderSession = encoderSession,
                    decoderSession = decoderSession,
                    decoder = bpeDecoder,
                    verifiedArtifacts = verified,
                    verifyMs = verifyMs,
                    loadMs = loadMs,
                    providerUsed = providerUsed,
                    usesKvCache = usesKvCache,
                )
            } catch (e: Throwable) {
                runCatching { encoderSession?.close() }
                runCatching { decoderSession?.close() }
                throw e
            } finally {
                runCatching { opts.close() }
            }
        }
    }
}
