package com.calcsuits.poc.ocr

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Verifies cryptographic integrity of all model and tokenizer files BEFORE
 * passing any path to ONNX Runtime or the JSON parser.
 *
 * Rejects missing files, corrupted files, empty hashes, and unexpected extra files
 * inside the model directory.
 */
object Sha256Verifier {

    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

    data class VerifiedArtifact(
        val fileName: String,
        val file: File,
        val sizeBytes: Long,
        val sha256: String,
    )

    class IntegrityException(message: String) : SecurityException(message)

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { b -> "%02x".format(b) }
    }

    fun verifyDirectory(
        modelDir: File,
        expectedHashes: Map<String, String> = TexTellerManifest.EXPECTED_SHA256,
    ): Map<String, VerifiedArtifact> {
        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IntegrityException("Model directory does not exist: ${modelDir.absolutePath}")
        }
        if (expectedHashes.isEmpty()) {
            throw IntegrityException("Expected SHA-256 manifest is empty.")
        }

        val entries = modelDir.listFiles() ?: emptyArray()
        for (entry in entries) {
            if (entry.isDirectory) {
                throw IntegrityException("Unexpected subdirectory in model dir: ${entry.name}")
            }
            if (!expectedHashes.containsKey(entry.name)) {
                throw IntegrityException("Undeclared file in model dir: ${entry.name}")
            }
        }

        val verified = LinkedHashMap<String, VerifiedArtifact>(expectedHashes.size)
        for ((fileName, expectedRaw) in expectedHashes) {
            val expected = expectedRaw.lowercase()
            if (!SHA256_REGEX.matches(expected)) {
                throw IntegrityException("Invalid SHA-256 hash format declared for $fileName")
            }
            val file = File(modelDir, fileName)
            if (!file.exists() || !file.isFile) {
                throw IntegrityException("Missing required model artifact: $fileName")
            }
            val actual = computeSha256(file)
            if (actual != expected) {
                throw IntegrityException(
                    "SHA-256 mismatch for $fileName: expected $expected, got $actual",
                )
            }
            verified[fileName] = VerifiedArtifact(
                fileName = fileName,
                file = file,
                sizeBytes = file.length(),
                sha256 = actual,
            )
        }
        return verified
    }
}
