package com.calcsuits.poc.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256VerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifyDirectory_acceptsAllExactPinnedHashes() {
        val dir = tempFolder.newFolder("valid_models")
        val f1 = File(dir, "encoder.onnx").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val f2 = File(dir, "tokenizer.json").apply { writeText("{\"vocab\":{}}") }
        val expected = mapOf(
            "encoder.onnx" to Sha256Verifier.computeSha256(f1),
            "tokenizer.json" to Sha256Verifier.computeSha256(f2),
        )

        val verified = Sha256Verifier.verifyDirectory(dir, expected)
        assertEquals(2, verified.size)
        assertEquals(4L, verified.getValue("encoder.onnx").sizeBytes)
        assertEquals(expected.getValue("tokenizer.json"), verified.getValue("tokenizer.json").sha256)
    }

    @Test
    fun verifyDirectory_rejectsCorruptedSingleByte() {
        val dir = tempFolder.newFolder("corrupt_models")
        val f1 = File(dir, "encoder.onnx").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val goodHash = Sha256Verifier.computeSha256(f1)
        f1.writeBytes(byteArrayOf(1, 2, 3, 5))

        assertThrows(Sha256Verifier.IntegrityException::class.java) {
            Sha256Verifier.verifyDirectory(dir, mapOf("encoder.onnx" to goodHash))
        }
    }

    @Test
    fun verifyDirectory_rejectsMissingRequiredFile() {
        val dir = tempFolder.newFolder("missing_models")
        val expected = mapOf(
            "encoder.onnx" to "a".repeat(64),
        )

        assertThrows(Sha256Verifier.IntegrityException::class.java) {
            Sha256Verifier.verifyDirectory(dir, expected)
        }
    }

    @Test
    fun verifyDirectory_rejectsUnexpectedShadowFile() {
        val dir = tempFolder.newFolder("shadow_models")
        val f1 = File(dir, "encoder.onnx").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        File(dir, "rogue_model.onnx").writeBytes(byteArrayOf(0, 0, 0))
        val expected = mapOf("encoder.onnx" to Sha256Verifier.computeSha256(f1))

        assertThrows(Sha256Verifier.IntegrityException::class.java) {
            Sha256Verifier.verifyDirectory(dir, expected)
        }
    }

    @Test
    fun verifyDirectory_rejectsInvalidHashFormat() {
        val dir = tempFolder.newFolder("bad_hash_models")
        File(dir, "encoder.onnx").writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(Sha256Verifier.IntegrityException::class.java) {
            Sha256Verifier.verifyDirectory(dir, mapOf("encoder.onnx" to "not-a-sha256"))
        }
    }

    @Test
    fun manifest_hasAllFourPinned64HexHashes() {
        val hex64 = Regex("^[0-9a-f]{64}$")
        assertEquals(4, TexTellerManifest.EXPECTED_SHA256.size)
        for ((name, hash) in TexTellerManifest.EXPECTED_SHA256) {
            assertEquals("Invalid SHA-256 for $name", true, hex64.matches(hash))
        }
    }
}
