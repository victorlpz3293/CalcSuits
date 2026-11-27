package com.calcsuits.features.ai.ocr

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Strict pre-load SHA-256 verifier for on-device OCR artifacts.
 * Rejects missing files, empty/non-64-hex hashes, and single-byte corruption
 * before ONNX Runtime or tokenizer parsers can open any file.
 */
object Sha256Verifier {
    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    class IntegrityException(message: String) : SecurityException(message)

    data class VerifiedArtifact(
        val name: String,
        val file: File,
        val sha256: String,
        val bytes: Long,
    )

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 64)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyFile(file: File, expectedSha256: String, label: String = file.name): VerifiedArtifact {
        val normalizedExpected = expectedSha256.trim().lowercase()
        if (!HEX_64.matches(normalizedExpected)) {
            throw IntegrityException("Missing or malformed pinned SHA-256 for $label.")
        }
        if (!file.exists() || !file.isFile) {
            throw IntegrityException("Missing required model artifact: $label")
        }
        if (file.length() <= 0L) {
            throw IntegrityException("Model artifact is empty: $label")
        }
        val actual = sha256File(file)
        if (actual != normalizedExpected) {
            throw IntegrityException(
                "SHA-256 integrity mismatch for $label (expected=$normalizedExpected, actual=$actual)."
            )
        }
        return VerifiedArtifact(
            name = label,
            file = file,
            sha256 = actual,
            bytes = file.length(),
        )
    }

    fun verifyDirectory(
        modelDir: File,
        expectedByFileName: Map<String, String> = TexTellerManifest.EXPECTED_SHA256,
    ): Map<String, VerifiedArtifact> {
        if (expectedByFileName.isEmpty()) {
            throw IntegrityException("Expected artifact manifest must not be empty.")
        }
        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IntegrityException("Model directory does not exist: ${modelDir.absolutePath}")
        }
        val verified = LinkedHashMap<String, VerifiedArtifact>(expectedByFileName.size)
        for ((name, expectedHash) in expectedByFileName) {
            val file = File(modelDir, name)
            verified[name] = verifyFile(file, expectedHash, name)
        }
        return verified
    }
}
