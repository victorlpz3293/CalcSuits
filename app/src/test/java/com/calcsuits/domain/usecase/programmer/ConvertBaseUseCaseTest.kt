package com.calcsuits.domain.usecase.programmer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConvertBaseUseCaseTest {

    private lateinit var useCase: ConvertBaseUseCase

    @Before
    fun setUp() {
        useCase = ConvertBaseUseCase()
    }

    @Test
    fun convertsBetweenDecBinHexAndOctAccurately() {
        val dec = useCase.decode("255", 10)!!
        assertEquals(255L, dec)
        assertEquals("FF", useCase.encodeRaw(dec, 16, 16))
        assertEquals("377", useCase.encodeRaw(dec, 8, 16))
        assertEquals("11111111", useCase.encodeRaw(dec, 2, 16))
        assertEquals("0000 0000 1111 1111", useCase.encodeUnsigned(dec, 2, 16))

        assertEquals(255L, useCase.decode("FF", 16))
        assertEquals(255L, useCase.decode("377", 8))
        assertEquals(255L, useCase.decode("1111 1111", 2))

        val cafe = useCase.decode("CAFE", 16)!!
        assertEquals(51_966L, cafe)
        assertEquals("145376", useCase.encodeRaw(cafe, 8, 32))
        assertEquals("CAFE", useCase.encodeUnsigned(cafe, 16, 32))
    }

    @Test
    fun twosComplementAndOverflowFor8BitWordSize() {
        assertEquals(127L, useCase.normalizeSigned(127L, 8))
        assertEquals(-128L, useCase.normalizeSigned(128L, 8))
        assertEquals(-1L, useCase.normalizeSigned(255L, 8))
        assertEquals(0L, useCase.normalizeSigned(256L, 8))
        assertEquals(1L, useCase.normalizeSigned(257L, 8))

        assertEquals("FF", useCase.encodeUnsigned(-1L, 16, 8))
        assertEquals("377", useCase.encodeUnsigned(-1L, 8, 8))
        assertEquals("1111 1111", useCase.encodeUnsigned(-1L, 2, 8))
        assertEquals("-1", useCase.encodeUnsigned(-1L, 10, 8))

        assertEquals("80", useCase.encodeUnsigned(-128L, 16, 8))
        assertEquals("1000 0000", useCase.encodeUnsigned(-128L, 2, 8))
    }

    @Test
    fun twosComplementAndOverflowFor16BitWordSize() {
        assertEquals(32_767L, useCase.normalizeSigned(32_767L, 16))
        assertEquals(-32_768L, useCase.normalizeSigned(32_768L, 16))
        assertEquals(-1L, useCase.normalizeSigned(0xFFFFL, 16))
        assertEquals(0L, useCase.normalizeSigned(0x10000L, 16))

        assertEquals("FFFF", useCase.encodeUnsigned(-1L, 16, 16))
        assertEquals("177777", useCase.encodeUnsigned(-1L, 8, 16))
        assertEquals("1111 1111 1111 1111", useCase.encodeUnsigned(-1L, 2, 16))
    }

    @Test
    fun twosComplementAndOverflowFor32BitWordSize() {
        assertEquals(2_147_483_647L, useCase.normalizeSigned(0x7FFF_FFFFL, 32))
        assertEquals(-2_147_483_648L, useCase.normalizeSigned(0x8000_0000L, 32))
        assertEquals(-1L, useCase.normalizeSigned(0xFFFF_FFFFL, 32))
        assertEquals(0L, useCase.normalizeSigned(0x1_0000_0000L, 32))

        assertEquals("FFFF FFFF", useCase.encodeUnsigned(-1L, 16, 32))
        assertEquals("37777777777", useCase.encodeUnsigned(-1L, 8, 32))
    }

    @Test
    fun twosComplementAndUnsignedDecodeFor64BitWordSize() {
        assertEquals(Long.MAX_VALUE, useCase.normalizeSigned(Long.MAX_VALUE, 64))
        assertEquals(Long.MIN_VALUE, useCase.normalizeSigned(Long.MIN_VALUE, 64))
        assertEquals(-1L, useCase.decode("FFFFFFFFFFFFFFFF", 16))
        assertEquals("FFFF FFFF FFFF FFFF", useCase.encodeUnsigned(-1L, 16, 64))
        assertEquals("1777777777777777777777", useCase.encodeUnsigned(-1L, 8, 64))
        assertEquals("-1", useCase.encodeUnsigned(-1L, 10, 64))
    }

    @Test
    fun decodeHandlesEmptyAndInvalidInputsSafely() {
        assertEquals(0L, useCase.decode("", 10))
        assertEquals(0L, useCase.decode("   ", 16))
        assertNull(useCase.decode("12G", 16))
        assertNull(useCase.decode("2", 2))
        assertNull(useCase.decode("8", 8))
    }
}
