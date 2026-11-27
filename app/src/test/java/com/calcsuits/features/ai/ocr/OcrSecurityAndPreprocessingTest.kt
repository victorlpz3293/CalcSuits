package com.calcsuits.features.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrSecurityAndPreprocessingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun sha256VerifierAcceptsValidAndRejectsCorruptedOrMissingFiles() {
        val dir = File(context.cacheDir, "test_sha_${System.nanoTime()}").apply { mkdirs() }
        try {
            val file = File(dir, "sample.bin")
            file.writeBytes("calcsuits-ocr-verified".toByteArray())
            val validHash = Sha256Verifier.sha256File(file)

            val verified = Sha256Verifier.verifyFile(file, validHash)
            assertEquals(validHash, verified.sha256)
            assertEquals(file.length(), verified.bytes)

            // Single-byte tamper must fail immediately
            file.writeBytes("calcsuits-ocr-verifieD".toByteArray())
            try {
                Sha256Verifier.verifyFile(file, validHash)
                fail("Expected IntegrityException on single-byte modification")
            } catch (_: Sha256Verifier.IntegrityException) {
                // Expected
            }

            // Missing file must fail
            try {
                Sha256Verifier.verifyFile(File(dir, "missing.onnx"), validHash)
                fail("Expected IntegrityException on missing file")
            } catch (_: Sha256Verifier.IntegrityException) {
                // Expected
            }

            // Malformed hash must fail
            try {
                Sha256Verifier.verifyFile(file, "not-a-valid-sha256")
                fail("Expected IntegrityException on malformed SHA-256")
            } catch (_: Sha256Verifier.IntegrityException) {
                // Expected
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun manifestAllowsOnlyPinnedHttpsUrls() {
        for (artifact in TexTellerManifest.ARTIFACTS) {
            assertTrue(artifact.downloadUrl.startsWith("https://"))
            assertTrue(TexTellerManifest.isAllowedDownloadUrl(artifact.downloadUrl))
        }
        assertFalse(
            TexTellerManifest.isAllowedDownloadUrl(
                "http://huggingface.co/onnx-community/TexTeller3-ONNX/resolve/${TexTellerManifest.PINNED_REVISION}/tokenizer.json"
            )
        )
        assertFalse(TexTellerManifest.isAllowedDownloadUrl("https://evil.example.com/tokenizer.json"))
        assertFalse(TexTellerManifest.isAllowedDownloadUrl("${TexTellerManifest.BASE_HTTPS_URL}/other.onnx"))
    }

    @Test
    fun byteLevelBpeDecoderDecodesTokensAndSkipsSpecialTokens() {
        val dir = File(context.cacheDir, "test_tok_${System.nanoTime()}").apply { mkdirs() }
        try {
            val tokJson = File(dir, "tokenizer.json")
            tokJson.writeText(
                """
                {
                  "model": {
                    "vocab": {
                      "<s>": 0,
                      "<pad>": 1,
                      "</s>": 2,
                      "x": 10,
                      "^": 11,
                      "2": 12,
                      "Ġ+1": 13
                    }
                  },
                  "added_tokens": [
                    {"id": 0, "content": "<s>", "special": true},
                    {"id": 1, "content": "<pad>", "special": true},
                    {"id": 2, "content": "</s>", "special": true}
                  ]
                }
                """.trimIndent()
            )
            val tokCfg = File(dir, "tokenizer_config.json")
            tokCfg.writeText("""{"bos_token": "<s>", "eos_token": "</s>", "pad_token": "<pad>"}""")

            val decoder = ByteLevelBpeDecoder.fromFiles(tokJson, tokCfg)
            assertEquals(0, decoder.bosTokenId)
            assertEquals(2, decoder.eosTokenId)
            assertEquals("x^2 +1", decoder.decode(listOf(0, 10, 11, 12, 13, 2)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun imageDecoderCalculatesSafeInSampleSizeAndEnforcesDimensionLimits() {
        assertEquals(1, OcrImageDecoder.calculateInSampleSize(800, 600))
        assertEquals(2, OcrImageDecoder.calculateInSampleSize(2000, 1500))
        assertEquals(4, OcrImageDecoder.calculateInSampleSize(4000, 3000))
        assertEquals(8, OcrImageDecoder.calculateInSampleSize(8192, 4096))

        try {
            OcrImageDecoder.calculateInSampleSize(20_000, 1000)
            fail("Expected InvalidImageException for >16384px dimension")
        } catch (_: OcrImageDecoder.InvalidImageException) {
            // Expected
        }

        try {
            OcrImageDecoder.calculateInSampleSize(0, 500)
            fail("Expected InvalidImageException for 0 dimension")
        } catch (_: OcrImageDecoder.InvalidImageException) {
            // Expected
        }
    }

    @Test
    fun imageDecoderDownsamplesLargePngAndRejectsCorruptFiles() {
        val dir = File(context.cacheDir, "test_img_${System.nanoTime()}").apply { mkdirs() }
        try {
            val largeFile = File(dir, "large.png")
            val bmp = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            FileOutputStream(largeFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()

            val decoded = OcrImageDecoder.decodeFileToGray(largeFile)
            assertEquals(2400, decoded.originalWidth)
            assertEquals(1200, decoded.originalHeight)
            assertEquals(4, decoded.sampleSize)
            assertTrue(decoded.grayImage.width <= OcrImageDecoder.TARGET_MAX_DIMENSION_PX)
            assertTrue(decoded.grayImage.height <= OcrImageDecoder.TARGET_MAX_DIMENSION_PX)

            val corruptFile = File(dir, "corrupt.jpg")
            corruptFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            try {
                OcrImageDecoder.decodeFileToGray(corruptFile)
                fail("Expected InvalidImageException for corrupt file")
            } catch (_: OcrImageDecoder.InvalidImageException) {
                // Expected
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun preprocessorInvertsDarkModeBackgroundAndPreservesLightPaper() {
        // Dark-mode screenshot: dark border (lum ~18) with white formula text in center
        val darkBmp = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        Canvas(darkBmp).apply {
            drawColor(Color.rgb(18, 18, 22))
            val paint = Paint().apply { color = Color.WHITE; strokeWidth = 4f }
            drawLine(40f, 40f, 160f, 40f, paint)
        }
        val darkGray = TexTellerImagePreprocessor.fromBitmap(darkBmp)
        darkBmp.recycle()

        val darkPrep = TexTellerImagePreprocessor.preprocess(darkGray)
        assertTrue("Expected borderMedian < 128", darkPrep.borderMedianLuminance < 128)
        assertTrue("Expected automatic polarity inversion on dark background", darkPrep.polarityInverted)
        assertEquals(448 * 448, darkPrep.data.size)
        assertTrue(darkPrep.data.all { it.isFinite() })

        // Light-paper photo: bright border (lum ~248) with dark ink text in center
        val lightBmp = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        Canvas(lightBmp).apply {
            drawColor(Color.rgb(248, 248, 248))
            val paint = Paint().apply { color = Color.BLACK; strokeWidth = 4f }
            drawLine(40f, 40f, 160f, 40f, paint)
        }
        val lightGray = TexTellerImagePreprocessor.fromBitmap(lightBmp)
        lightBmp.recycle()

        val lightPrep = TexTellerImagePreprocessor.preprocess(lightGray)
        assertTrue("Expected borderMedian >= 128", lightPrep.borderMedianLuminance >= 128)
        assertFalse("Must NOT invert light paper background", lightPrep.polarityInverted)
    }

    @Test
    fun tempFileManagerCreatesAndCleansStaleCapturesInPrivateCacheDir() {
        val dir = OcrTempFileManager.getTempDir(context)
        val stale = File(dir, "scan_stale_test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(stale.exists())

        val deleted = OcrTempFileManager.cleanupStaleFiles(context)
        assertTrue(deleted >= 1)
        assertFalse(stale.exists())
    }

    @Test
    fun localOcrRepositoryCleansTempFileAndReleasesNativeEngineOnExitAndIdle() = runBlocking {
        val closedCount = AtomicInteger(0)
        val fakeEngine = object : OcrEngineHandle {
            override var isClosed: Boolean = false
                private set

            override fun recognizeGray(
                grayImage: TexTellerImagePreprocessor.GrayImage,
                isCancelled: () -> Boolean,
            ): OcrRecognitionOutput {
                return OcrRecognitionOutput(
                    rawLatex = """\[x^2-5x+6=0\]""",
                    totalMs = 120,
                    preprocessMs = 15,
                    encoderMs = 45,
                    decoderMs = 60,
                    nativeHeapMb = 120.0,
                    jvmHeapMb = 40.0,
                    polarityInverted = false,
                    usesKvCache = true,
                    sampleSize = 1,
                    generatedTokens = 11,
                )
            }

            override fun close() {
                if (!isClosed) {
                    isClosed = true
                    closedCount.incrementAndGet()
                }
            }
        }

        val repo = LocalOcrRepository(
            context = context,
            modelManager = OcrModelManager(context),
            engineFactory = { fakeEngine },
            idleTimeoutMs = 150L,
        )

        // Create a real temp PNG in private cache and run OCR on it
        val tempDir = OcrTempFileManager.getTempDir(context)
        val tempImg = File(tempDir, "scan_unit_test.png")
        val bmp = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        FileOutputStream(tempImg).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bmp.recycle()
        assertTrue(tempImg.exists())

        val output = repo.recognizeFromTempFile(tempImg)
        assertEquals("""\[x^2-5x+6=0\]""", output.rawLatex)
        assertTrue(output.usesKvCache)
        assertFalse("Temp capture file MUST be deleted after OCR", tempImg.exists())
        assertTrue(repo.isEngineLoaded())

        // Idle timeout should auto-release native session
        delay(350L)
        assertFalse("Engine should be released after idle timeout", repo.isEngineLoaded())
        assertEquals(1, closedCount.get())

        // Corrupt temp file MUST ALSO be deleted in finally even when decode fails
        val corruptTemp = File(tempDir, "scan_corrupt_test.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        try {
            repo.recognizeFromTempFile(corruptTemp)
            fail("Expected InvalidImageException")
        } catch (_: OcrImageDecoder.InvalidImageException) {
            // Expected
        }
        assertFalse("Corrupt temp file MUST be deleted on error", corruptTemp.exists())

        // Cancellation test: engine closed on cancel
        val started = CompletableDeferred<Unit>()
        val cancelClosed = AtomicInteger(0)
        val slowEngine = object : OcrEngineHandle {
            override var isClosed: Boolean = false
                private set

            override fun recognizeGray(
                grayImage: TexTellerImagePreprocessor.GrayImage,
                isCancelled: () -> Boolean,
            ): OcrRecognitionOutput {
                started.complete(Unit)
                while (!isCancelled()) {
                    Thread.sleep(10)
                }
                throw CancellationException("Canceled during decoding")
            }

            override fun close() {
                if (!isClosed) {
                    isClosed = true
                    cancelClosed.incrementAndGet()
                }
            }
        }
        val cancelRepo = LocalOcrRepository(
            context = context,
            modelManager = OcrModelManager(context),
            engineFactory = { slowEngine },
            idleTimeoutMs = 60_000L,
        )
        val gray = TexTellerImagePreprocessor.GrayImage(16, 16, ByteArray(256) { 255.toByte() })
        val job = launch {
            cancelRepo.recognizeDecodedForTest(OcrImageDecoder.DecodedGrayResult(gray, 16, 16, 1))
        }
        started.await()
        job.cancel()
        job.join()
        assertEquals(1, cancelClosed.get())
        assertFalse(cancelRepo.isEngineLoaded())
    }
}
