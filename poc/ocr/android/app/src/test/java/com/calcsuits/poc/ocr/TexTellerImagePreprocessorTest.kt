package com.calcsuits.poc.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TexTellerImagePreprocessorTest {

    @Test
    fun preprocess_trimsWhiteBorderResizesPreservingAspectAndZeroPads() {
        // 800x200 white canvas with a 600x100 black rectangle centered inside.
        val w = 800
        val h = 200
        val pixels = ByteArray(w * h) { 255.toByte() }
        for (y in 50 until 150) {
            for (x in 100 until 700) {
                pixels[y * w + x] = 0
            }
        }

        val prep = TexTellerImagePreprocessor.preprocess(
            TexTellerImagePreprocessor.GrayImage(w, h, pixels),
            enableGentleDarkStretch = false,
        )

        assertArrayEquals(longArrayOf(1L, 1L, 448L, 448L), prep.shape)
        assertEquals(448 * 448, prep.data.size)
        assertEquals(600, prep.croppedWidth)
        assertEquals(100, prep.croppedHeight)
        assertEquals(448, prep.resizedWidth)
        assertEquals(75, prep.resizedHeight)

        // Top-left active pixel should be normalized black: (0 - MEAN) / STD
        val expectedBlack = (0f - TexTellerImagePreprocessor.MEAN) / TexTellerImagePreprocessor.STD
        assertEquals(expectedBlack, prep.data[0], 0.01f)

        // Bottom padded area outside resizedHeight must be strictly 0.0f
        assertEquals(0f, prep.data[447 * 448 + 447], 0.0f)
    }

    @Test
    fun gentleExposureNormalizeIfDark_stretchesDarkLowContrastImageWithoutBinarizing() {
        val pixels = byteArrayOf(30, 60, 90, 120)
        val raw = TexTellerImagePreprocessor.GrayImage(2, 2, pixels)
        val stretched = TexTellerImagePreprocessor.gentleExposureNormalizeIfDark(raw)

        val values = stretched.pixels.map { it.toInt() and 0xFF }
        assertEquals(0, values[0])
        assertEquals(255, values[3])
        // Intermediate gray tones preserved (NOT hard-binarized to 0/255)
        assertTrue("Expected midtone preservation, got $values", values[1] in 60..120 && values[2] in 140..200)
    }
}
