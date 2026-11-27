package com.calcsuits.features.ai.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Memory-safe image decoder for OCR captures and Photo Picker selections.
 *
 * Enforces:
 * - Two-pass decoding (`inJustDecodeBounds = true` first) to prevent OOM on multi-megapixel camera photos.
 * - Power-of-two `BitmapFactory.Options.inSampleSize` limiting the decoded max dimension to ~1024 px.
 * - Hard guards against invalid, empty, or excessively large dimensions.
 * - Immediate `Bitmap.recycle()` in `finally` after extracting the compact grayscale buffer.
 */
object OcrImageDecoder {
    const val TARGET_MAX_DIMENSION_PX = 1024
    const val MAX_ALLOWED_DIMENSION_PX = 16_384
    const val MAX_ALLOWED_PIXELS = 100_000_000L

    class InvalidImageException(message: String) : IllegalArgumentException(message)

    data class DecodedGrayResult(
        val grayImage: TexTellerImagePreprocessor.GrayImage,
        val originalWidth: Int,
        val originalHeight: Int,
        val sampleSize: Int,
    )

    /**
     * Computes a power-of-two `inSampleSize` so that `max(width, height) / inSampleSize <= maxDimension`.
     */
    fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int = TARGET_MAX_DIMENSION_PX,
    ): Int {
        require(maxDimension > 0) { "maxDimension must be positive." }
        if (width <= 0 || height <= 0) {
            throw InvalidImageException("Image has invalid dimensions: ${width}x${height}")
        }
        if (width > MAX_ALLOWED_DIMENSION_PX || height > MAX_ALLOWED_DIMENSION_PX) {
            throw InvalidImageException(
                "Image dimension exceeds safety limit (${MAX_ALLOWED_DIMENSION_PX}px): ${width}x${height}"
            )
        }
        val totalPixels = width.toLong() * height.toLong()
        if (totalPixels > MAX_ALLOWED_PIXELS) {
            throw InvalidImageException("Image pixel count exceeds safety limit: $totalPixels")
        }

        var sampleSize = 1
        var currentMax = max(width, height)
        while (currentMax > maxDimension) {
            sampleSize *= 2
            currentMax = max(width / sampleSize, height / sampleSize)
        }
        return sampleSize
    }

    fun decodeFileToGray(
        file: File,
        maxDimension: Int = TARGET_MAX_DIMENSION_PX,
    ): DecodedGrayResult {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw InvalidImageException("Image file is missing or empty.")
        }
        return decodeStreamSupplierToGray(
            openStream = { FileInputStream(file) },
            maxDimension = maxDimension,
        )
    }

    fun decodeUriToGray(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = TARGET_MAX_DIMENSION_PX,
    ): DecodedGrayResult {
        return decodeStreamSupplierToGray(
            openStream = {
                contentResolver.openInputStream(uri)
                    ?: throw InvalidImageException("Could not open image URI.")
            },
            maxDimension = maxDimension,
        )
    }

    internal fun decodeStreamSupplierToGray(
        openStream: () -> InputStream,
        maxDimension: Int = TARGET_MAX_DIMENSION_PX,
    ): DecodedGrayResult {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openStream().use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) {
            throw InvalidImageException("Unsupported or corrupted image format.")
        }

        val sampleSize = calculateInSampleSize(origW, origH, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }

        val decodedBitmap = openStream().use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw InvalidImageException("Failed to decode image bitmap.")

        try {
            val gray = TexTellerImagePreprocessor.fromBitmap(decodedBitmap)
            return DecodedGrayResult(
                grayImage = gray,
                originalWidth = origW,
                originalHeight = origH,
                sampleSize = sampleSize,
            )
        } finally {
            if (!decodedBitmap.isRecycled) {
                decodedBitmap.recycle()
            }
        }
    }
}
