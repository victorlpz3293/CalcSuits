package com.calcsuits.poc.ocr

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mirrors TexTeller's official preprocessing pipeline (448x448 grayscale tensor):
 * 1. Convert ARGB to luminance grayscale [0..255]
 * 2. Optional gentle contrast/exposure stretch for dark photos (NO hard binarization)
 * 3. Trim near-white border (< 250)
 * 4. Aspect-preserving bilinear resize so max(w, h) <= 448
 * 5. Normalize: (gray / 255f - MEAN) / STD
 * 6. Zero-pad top-left into [1, 1, 448, 448] FloatArray
 */
object TexTellerImagePreprocessor {
    const val IMG_SIZE = 448
    const val MEAN = 0.9545467f
    const val STD = 0.15394445f

    data class GrayImage(
        val width: Int,
        val height: Int,
        val pixels: ByteArray, // unsigned 0..255 stored in signed bytes
    )

    data class PreprocessedTensor(
        val data: FloatArray,
        val shape: LongArray,
        val croppedWidth: Int,
        val croppedHeight: Int,
        val resizedWidth: Int,
        val resizedHeight: Int,
    )

    fun fromBitmap(bitmap: Bitmap): GrayImage {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        return fromArgbPixels(argb, w, h)
    }

    fun fromArgbPixels(argb: IntArray, width: Int, height: Int): GrayImage {
        require(width > 0 && height > 0) { "Image dimensions must be positive." }
        require(argb.size == width * height) { "ARGB buffer length mismatch." }
        val gray = ByteArray(width * height)
        for (i in argb.indices) {
            val c = argb[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            gray[i] = lum.toByte()
        }
        return GrayImage(width, height, gray)
    }

    /**
     * Gentle exposure/contrast stretch ONLY when image background is dark (mean < 170).
     * Never applies hard thresholding/binarization, which degrades ViT/Swin models.
     */
    fun gentleExposureNormalizeIfDark(img: GrayImage): GrayImage {
        var sum = 0L
        var minVal = 255
        var maxVal = 0
        for (b in img.pixels) {
            val v = b.toInt() and 0xFF
            sum += v
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val mean = sum.toFloat() / img.pixels.size
        if (mean >= 170f || maxVal - minVal < 16) {
            return img
        }
        val out = ByteArray(img.pixels.size)
        val scale = 255f / (maxVal - minVal).toFloat()
        for (i in img.pixels.indices) {
            val v = img.pixels[i].toInt() and 0xFF
            out[i] = ((v - minVal) * scale).roundToInt().coerceIn(0, 255).toByte()
        }
        return GrayImage(img.width, img.height, out)
    }

    fun trimWhiteBorder(img: GrayImage, threshold: Int = 250): GrayImage {
        var xMin = img.width
        var yMin = img.height
        var xMax = -1
        var yMax = -1
        for (y in 0 until img.height) {
            val rowOffset = y * img.width
            for (x in 0 until img.width) {
                val v = img.pixels[rowOffset + x].toInt() and 0xFF
                if (v < threshold) {
                    if (x < xMin) xMin = x
                    if (x > xMax) xMax = x
                    if (y < yMin) yMin = y
                    if (y > yMax) yMax = y
                }
            }
        }
        if (xMax < xMin || yMax < yMin) {
            return img
        }
        val cw = xMax - xMin + 1
        val ch = yMax - yMin + 1
        val out = ByteArray(cw * ch)
        for (y in 0 until ch) {
            val srcRow = (yMin + y) * img.width + xMin
            val dstRow = y * cw
            System.arraycopy(img.pixels, srcRow, out, dstRow, cw)
        }
        return GrayImage(cw, ch, out)
    }

    fun resizeBilinear(img: GrayImage, newW: Int, newH: Int): GrayImage {
        require(newW > 0 && newH > 0) { "Target dimensions must be positive." }
        if (img.width == newW && img.height == newH) return img
        val out = ByteArray(newW * newH)
        val scaleX = img.width.toFloat() / newW.toFloat()
        val scaleY = img.height.toFloat() / newH.toFloat()
        for (y in 0 until newH) {
            val sy = min(img.height - 1f, (y + 0.5f) * scaleY - 0.5f)
            val y0 = max(0, floor(sy.toDouble()).toInt())
            val y1 = min(img.height - 1, y0 + 1)
            val wy = max(0f, sy - y0)
            for (x in 0 until newW) {
                val sx = min(img.width - 1f, (x + 0.5f) * scaleX - 0.5f)
                val x0 = max(0, floor(sx.toDouble()).toInt())
                val x1 = min(img.width - 1, x0 + 1)
                val wx = max(0f, sx - x0)
                val v00 = img.pixels[y0 * img.width + x0].toInt() and 0xFF
                val v10 = img.pixels[y0 * img.width + x1].toInt() and 0xFF
                val v01 = img.pixels[y1 * img.width + x0].toInt() and 0xFF
                val v11 = img.pixels[y1 * img.width + x1].toInt() and 0xFF
                val v = (v00 * (1f - wx) + v10 * wx) * (1f - wy) +
                    (v01 * (1f - wx) + v11 * wx) * wy
                out[y * newW + x] = v.roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return GrayImage(newW, newH, out)
    }

    fun preprocess(
        raw: GrayImage,
        enableGentleDarkStretch: Boolean = true,
    ): PreprocessedTensor {
        val adjusted = if (enableGentleDarkStretch) gentleExposureNormalizeIfDark(raw) else raw
        val cropped = trimWhiteBorder(adjusted)
        var resized = cropped
        val maxDim = max(cropped.width, cropped.height)
        if (maxDim > IMG_SIZE) {
            val scale = IMG_SIZE.toFloat() / maxDim.toFloat()
            val nw = max(1, (cropped.width * scale).roundToInt())
            val nh = max(1, (cropped.height * scale).roundToInt())
            resized = resizeBilinear(cropped, nw, nh)
        }
        val tensor = FloatArray(IMG_SIZE * IMG_SIZE) // zero-initialized padding
        for (y in 0 until resized.height) {
            val srcRow = y * resized.width
            val dstRow = y * IMG_SIZE
            for (x in 0 until resized.width) {
                val grayNorm = (resized.pixels[srcRow + x].toInt() and 0xFF) / 255f
                tensor[dstRow + x] = (grayNorm - MEAN) / STD
            }
        }
        return PreprocessedTensor(
            data = tensor,
            shape = longArrayOf(1L, 1L, IMG_SIZE.toLong(), IMG_SIZE.toLong()),
            croppedWidth = cropped.width,
            croppedHeight = cropped.height,
            resizedWidth = resized.width,
            resizedHeight = resized.height,
        )
    }
}
