package com.calcsuits.features.ai.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app-private storage and optional on-demand HTTPS downloads for TexTeller3-ONNX (q4f16).
 *
 * Security invariants:
 * - Strictly allows only pinned HTTPS URLs from `TexTellerManifest`.
 * - Streams to a `.part` temporary file, computing SHA-256 on the fly.
 * - Verifies exact SHA-256 BEFORE promoting `.part` to the final artifact name.
 * - Deletes `.part` immediately on hash mismatch, network error, or coroutine cancellation.
 * - Never sends any user image, expression, or personal data over the network.
 */
@Singleton
class OcrModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class DownloadProgress(
        val currentFileName: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    sealed class Availability {
        data object Ready : Availability()
        data class MissingOrCorrupted(val missingFiles: List<String>) : Availability()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getModelsDir(): File {
        val dir = File(context.filesDir, MODELS_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Quick check (existence + non-empty size) for UI readiness badge.
     * Full cryptographic SHA-256 verification ALWAYS runs before opening any session in `TexTellerOnnxEngine.loadVerified`.
     */
    fun checkQuickAvailability(modelDir: File = getModelsDir()): Availability {
        val missing = TexTellerManifest.ARTIFACTS
            .filter { spec ->
                val f = File(modelDir, spec.fileName)
                !f.exists() || !f.isFile || f.length() != spec.expectedBytes
            }
            .map { it.fileName }
        return if (missing.isEmpty()) Availability.Ready else Availability.MissingOrCorrupted(missing)
    }

    /**
     * Full cryptographic verification of all pinned artifacts.
     */
    suspend fun verifyInstalledModels(
        modelDir: File = getModelsDir(),
    ): Map<String, Sha256Verifier.VerifiedArtifact> = withContext(Dispatchers.IO) {
        Sha256Verifier.verifyDirectory(modelDir, TexTellerManifest.EXPECTED_SHA256)
    }

    /**
     * Downloads only missing or hash-mismatched pinned artifacts via HTTPS into app-private storage.
     */
    suspend fun downloadMissingArtifacts(
        modelDir: File = getModelsDir(),
        client: OkHttpClient = downloadClient,
        onProgress: (DownloadProgress) -> Unit = {},
    ): Map<String, Sha256Verifier.VerifiedArtifact> = withContext(Dispatchers.IO) {
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        cleanupPartialFiles(modelDir)

        val artifacts = TexTellerManifest.ARTIFACTS
        val totalBytes = TexTellerManifest.TOTAL_BYTES
        var completedBytes = 0L

        artifacts.forEachIndexed { idx, spec ->
            currentCoroutineContext().ensureActive()
            val targetFile = File(modelDir, spec.fileName)
            val alreadyValid = targetFile.exists() &&
                targetFile.length() == spec.expectedBytes &&
                runCatching { Sha256Verifier.verifyFile(targetFile, spec.expectedSha256, spec.fileName) }.isSuccess

            if (alreadyValid) {
                completedBytes += spec.expectedBytes
                onProgress(
                    DownloadProgress(
                        currentFileName = spec.fileName,
                        fileIndex = idx + 1,
                        totalFiles = artifacts.size,
                        downloadedBytes = completedBytes,
                        totalBytes = totalBytes,
                    )
                )
                return@forEachIndexed
            }

            if (!TexTellerManifest.isAllowedDownloadUrl(spec.downloadUrl)) {
                throw Sha256Verifier.IntegrityException("Blocked unauthorized model URL: ${spec.downloadUrl}")
            }

            val partFile = File(modelDir, "${spec.fileName}.part")
            if (partFile.exists()) partFile.delete()

            val request = Request.Builder()
                .url(spec.downloadUrl)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code()} downloading ${spec.fileName}")
                    }
                    val body = response.body() ?: throw IOException("Empty response body for ${spec.fileName}")
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(1024 * 64)
                    var fileBytes = 0L
                    var lastReportAt = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                fileBytes += read

                                val now = System.currentTimeMillis()
                                if (now - lastReportAt >= 120L) {
                                    lastReportAt = now
                                    onProgress(
                                        DownloadProgress(
                                            currentFileName = spec.fileName,
                                            fileIndex = idx + 1,
                                            totalFiles = artifacts.size,
                                            downloadedBytes = (completedBytes + fileBytes).coerceAtMost(totalBytes),
                                            totalBytes = totalBytes,
                                        )
                                    )
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actualHash != spec.expectedSha256) {
                        partFile.delete()
                        throw Sha256Verifier.IntegrityException(
                            "Downloaded ${spec.fileName} failed SHA-256 verification (expected=${spec.expectedSha256}, actual=$actualHash)."
                        )
                    }

                    if (targetFile.exists() && !targetFile.delete()) {
                        partFile.delete()
                        throw IOException("Could not replace existing artifact ${spec.fileName}")
                    }
                    if (!partFile.renameTo(targetFile)) {
                        partFile.delete()
                        throw IOException("Could not promote verified artifact ${spec.fileName}")
                    }
                    completedBytes += fileBytes
                    onProgress(
                        DownloadProgress(
                            currentFileName = spec.fileName,
                            fileIndex = idx + 1,
                            totalFiles = artifacts.size,
                            downloadedBytes = completedBytes.coerceAtMost(totalBytes),
                            totalBytes = totalBytes,
                        )
                    )
                }
            } catch (e: Throwable) {
                if (partFile.exists()) partFile.delete()
                throw e
            }
        }

        Sha256Verifier.verifyDirectory(modelDir, TexTellerManifest.EXPECTED_SHA256)
    }

    fun deleteInstalledModels(modelDir: File = getModelsDir()) {
        if (!modelDir.exists()) return
        modelDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun cleanupPartialFiles(modelDir: File) {
        modelDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part")) {
                runCatching { f.delete() }
            }
        }
    }

    companion object {
        const val MODELS_SUBDIR = "ocr_models/texteller3_q4f16"
    }
}
