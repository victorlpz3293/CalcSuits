package com.calcsuits.features.programmer.ui.viewmodel

import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.usecase.programmer.ConvertBaseUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProgrammerViewModelTest {

    private lateinit var viewModel: ProgrammerViewModel

    @Before
    fun setUp() {
        viewModel = ProgrammerViewModel(ConvertBaseUseCase())
    }

    @Test
    fun switchingBasesAndEnteringDigitsKeepsAllRepresentationsSynchronized() {
        viewModel.selectWordSize(16)
        viewModel.selectBase(16)
        viewModel.onKeyPress("A")
        viewModel.onKeyPress("F")

        val s = viewModel.state.value
        assertEquals(175L, s.rawValue)
        assertEquals("AF", s.hexValue)
        assertEquals("175", s.decValue)
        assertEquals("257", s.octValue)
        assertEquals("0000 0000 1010 1111", s.binValue)

        // Switching to binary ignores non-binary keys
        viewModel.selectBase(2)
        viewModel.onKeyPress("5")
        assertEquals(175L, viewModel.state.value.rawValue)
    }

    @Test
    fun wordSizeSelectionAppliesTwosComplementAndOverflow() {
        viewModel.selectWordSize(8)
        viewModel.selectBase(16)
        viewModel.onKeyPress("F")
        viewModel.onKeyPress("F")

        assertEquals(-1L, viewModel.state.value.rawValue)
        assertEquals("-1", viewModel.state.value.decValue)
        assertEquals("FF", viewModel.state.value.hexValue)
        assertEquals("1111 1111", viewModel.state.value.binValue)

        // Overflow 8-bit: 256 wraps to 0
        viewModel.onClear()
        viewModel.selectBase(10)
        "256".forEach { viewModel.onKeyPress(it.toString()) }
        assertEquals(0L, viewModel.state.value.rawValue)
        assertEquals("0", viewModel.state.value.decValue)
        assertEquals("0", viewModel.state.value.hexValue)

        // Downsizing from 16-bit 0x0080 (128) to 8-bit sign-extends to -128
        viewModel.onClear()
        viewModel.selectWordSize(16)
        viewModel.selectBase(16)
        viewModel.onKeyPress("8")
        viewModel.onKeyPress("0")
        assertEquals(128L, viewModel.state.value.rawValue)

        viewModel.selectWordSize(8)
        assertEquals(-128L, viewModel.state.value.rawValue)
        assertEquals("-128", viewModel.state.value.decValue)
    }

    @Test
    fun bitwiseUnaryAndShiftOperationsRespectWordSize() {
        viewModel.selectWordSize(8)
        viewModel.selectBase(16)
        viewModel.onKeyPress("0")
        viewModel.onBitwise("NOT")
        assertEquals(-1L, viewModel.state.value.rawValue)
        assertEquals("FF", viewModel.state.value.hexValue)
        assertEquals(UiText.DynamicString("NOT"), viewModel.state.value.lastOperation)

        // 0x80 (-128 in 8-bit) << 1 overflows out of 8 bits to 0
        viewModel.onClear()
        viewModel.selectWordSize(8)
        viewModel.selectBase(16)
        viewModel.onKeyPress("8")
        viewModel.onKeyPress("0")
        viewModel.onBitwise("SHL")
        assertEquals(0L, viewModel.state.value.rawValue)
        assertEquals(UiText.DynamicString("<< 1"), viewModel.state.value.lastOperation)

        // 0x80 >> 1 performs unsigned logical shift to 0x40 (64)
        viewModel.onClear()
        viewModel.selectBase(16)
        viewModel.onKeyPress("8")
        viewModel.onKeyPress("0")
        viewModel.onBitwise("SHR")
        assertEquals(64L, viewModel.state.value.rawValue)
        assertEquals("40", viewModel.state.value.hexValue)
        assertEquals(UiText.DynamicString(">> 1"), viewModel.state.value.lastOperation)
    }

    @Test
    fun bitwiseBinaryOperationsAndChainingWorkCorrectly() {
        viewModel.selectWordSize(8)
        viewModel.selectBase(16)

        // 0xF0 AND 0x3C = 0x30
        enterHex("F0")
        viewModel.onBitwise("AND")
        enterHex("3C")
        viewModel.onBitwise("=")
        assertEquals(0x30L, viewModel.state.value.rawValue)
        assertEquals("30", viewModel.state.value.hexValue)

        // 0x30 OR 0x05 = 0x35
        viewModel.onBitwise("OR")
        enterHex("5")
        viewModel.onBitwise("=")
        assertEquals(0x35L, viewModel.state.value.rawValue)

        // 0x35 XOR 0xFF = 0xCA (-54 in signed 8-bit)
        viewModel.onBitwise("XOR")
        enterHex("FF")
        viewModel.onBitwise("=")
        assertEquals("CA", viewModel.state.value.hexValue)
        assertEquals(-54L, viewModel.state.value.rawValue)

        // NAND & NOR in 8-bit: (0x0F NAND 0x0F) = 0xF0 (-16)
        viewModel.onClear()
        enterHex("F")
        viewModel.onBitwise("NAND")
        enterHex("F")
        viewModel.onBitwise("=")
        assertEquals("F0", viewModel.state.value.hexValue)
        assertEquals(-16L, viewModel.state.value.rawValue)

        // (0x0F NOR 0xF0) = 0x00
        viewModel.onClear()
        enterHex("F")
        viewModel.onBitwise("NOR")
        enterHex("F0")
        viewModel.onBitwise("=")
        assertEquals(0L, viewModel.state.value.rawValue)
    }

    @Test
    fun toggleBitBackspaceAndClearUpdateStateAndTypedLastOperation() {
        viewModel.selectWordSize(8)
        viewModel.toggleBit(3)
        assertEquals(8L, viewModel.state.value.rawValue)
        assertEquals(UiText.StringResource(R.string.prog_last_op_toggle_bit, 3), viewModel.state.value.lastOperation)

        // Toggling MSB (bit 7) sets sign bit in 8-bit two's complement
        viewModel.toggleBit(7)
        assertEquals(-120L, viewModel.state.value.rawValue)

        // Out-of-range bit is ignored
        viewModel.toggleBit(8)
        assertEquals(-120L, viewModel.state.value.rawValue)

        viewModel.onClear()
        assertEquals(0L, viewModel.state.value.rawValue)
        assertEquals("0", viewModel.state.value.input)
        assertNull(viewModel.state.value.lastOperation)

        viewModel.selectBase(10)
        viewModel.onKeyPress("4")
        viewModel.onKeyPress("2")
        viewModel.onBackspace()
        assertEquals(4L, viewModel.state.value.rawValue)
        viewModel.onBackspace()
        assertEquals(0L, viewModel.state.value.rawValue)
    }

    @Test
    fun bitMatrixLayoutCoversAll8_16_32And64BitsInExactMsbToLsbOrder() {
        listOf(8, 16, 32, 64).forEach { size ->
            val rows = com.calcsuits.features.programmer.ui.screens.buildBitMatrixRows(size)
            assertEquals("Word size $size should produce ${size / 8} byte rows", size / 8, rows.size)
            val flatIndices = rows.flatten()
            assertEquals("Word size $size must expose all $size bits", size, flatIndices.size)
            assertEquals((size - 1 downTo 0).toList(), flatIndices)
            rows.forEach { row -> assertEquals(8, row.size) }
        }
    }

    @Test
    fun togglingBits0_31_32And63In64BitWordUpdatesAndPreservesAllOtherBits() {
        viewModel.selectWordSize(64)
        viewModel.selectBase(16)

        // Toggle bit 0 ON -> 1
        viewModel.toggleBit(0)
        assertEquals(1L, viewModel.state.value.rawValue)
        assertEquals("1", viewModel.state.value.hexValue)
        assertEquals("1", viewModel.state.value.decValue)
        assertEquals("1", viewModel.state.value.octValue)
        assertEquals(true, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 0))

        // Toggle bit 0 OFF -> 0
        viewModel.toggleBit(0)
        assertEquals(0L, viewModel.state.value.rawValue)
        assertEquals(false, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 0))

        // Toggle bit 31 ON -> 0x0000_0000_8000_0000 (positive 2,147,483,648 in 64-bit)
        viewModel.toggleBit(31)
        assertEquals(1L shl 31, viewModel.state.value.rawValue)
        assertEquals("2147483648", viewModel.state.value.decValue)
        assertEquals("8000 0000", viewModel.state.value.hexValue)
        assertEquals("20000000000", viewModel.state.value.octValue)

        // Toggle bit 32 ON while bit 31 stays ON
        viewModel.toggleBit(32)
        val expected31And32 = (1L shl 31) or (1L shl 32)
        assertEquals(expected31And32, viewModel.state.value.rawValue)
        assertEquals("1 8000 0000", viewModel.state.value.hexValue)
        assertEquals("6442450944", viewModel.state.value.decValue)
        assertEquals("60000000000", viewModel.state.value.octValue)

        // Toggle bit 63 ON (sign bit of 64-bit two's complement) while bits 31 & 32 stay ON
        viewModel.toggleBit(63)
        val expected31_32_63 = expected31And32 or Long.MIN_VALUE
        assertEquals(expected31_32_63, viewModel.state.value.rawValue)
        assertEquals("8000 0001 8000 0000", viewModel.state.value.hexValue)
        assertEquals(expected31_32_63.toString(), viewModel.state.value.decValue)

        // Toggle bit 0 ON too, then verify every single one of the 64 bit positions matches rawValue and binValue
        viewModel.toggleBit(0)
        val expectedAll = expected31_32_63 or 1L
        val state = viewModel.state.value
        assertEquals(expectedAll, state.rawValue)
        val compactBin = state.binValue.replace(" ", "")
        assertEquals(64, compactBin.length)
        for (bit in 0..63) {
            val expectedSet = bit in setOf(0, 31, 32, 63)
            assertEquals("Matrix bit $bit mismatch", expectedSet, com.calcsuits.features.programmer.ui.screens.isBitSet(state.rawValue, bit))
            val binChar = compactBin[63 - bit]
            assertEquals("BIN display bit $bit mismatch", if (expectedSet) '1' else '0', binChar)
        }

        // Toggle bit 32 OFF -> bits 0, 31, 63 MUST remain intact
        viewModel.toggleBit(32)
        val afterClear32 = expectedAll xor (1L shl 32)
        assertEquals(afterClear32, viewModel.state.value.rawValue)
        assertEquals(true, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 0))
        assertEquals(true, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 31))
        assertEquals(false, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 32))
        assertEquals(true, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 63))

        // Toggle bit 63 OFF -> returns to positive (bit 31 | bit 0)
        viewModel.toggleBit(63)
        assertEquals((1L shl 31) or 1L, viewModel.state.value.rawValue)
        assertEquals(false, com.calcsuits.features.programmer.ui.screens.isBitSet(viewModel.state.value.rawValue, 63))

        // Toggle bit 31 OFF -> leaves only bit 0
        viewModel.toggleBit(31)
        assertEquals(1L, viewModel.state.value.rawValue)
    }

    @Test
    fun togglingBit31In32BitWordSetsNegativeSignAndIgnoresBit32() {
        viewModel.selectWordSize(32)
        viewModel.toggleBit(31)
        assertEquals(-2_147_483_648L, viewModel.state.value.rawValue)
        assertEquals("8000 0000", viewModel.state.value.hexValue)
        assertEquals("-2147483648", viewModel.state.value.decValue)

        // Bit 32 is out of range for 32-bit words
        viewModel.toggleBit(32)
        assertEquals(-2_147_483_648L, viewModel.state.value.rawValue)

        viewModel.toggleBit(31)
        assertEquals(0L, viewModel.state.value.rawValue)
    }

    @Test
    fun allBitwiseOperationsWorkAcrossFull64BitWordsAndNegativeBackspaceResetsCleanly() {
        viewModel.selectWordSize(64)
        viewModel.selectBase(16)

        // NOT 0 = 0xFFFF_FFFF_FFFF_FFFF (-1L)
        viewModel.onBitwise("NOT")
        assertEquals(-1L, viewModel.state.value.rawValue)
        assertEquals("FFFF FFFF FFFF FFFF", viewModel.state.value.hexValue)
        assertEquals("-1", viewModel.state.value.decValue)

        // Backspace in decimal on "-1" resets cleanly to "0" instead of lingering "-"
        viewModel.selectBase(10)
        viewModel.onBackspace()
        assertEquals(0L, viewModel.state.value.rawValue)
        assertEquals("0", viewModel.state.value.input)
        assertEquals("0", viewModel.state.value.decValue)

        // SHL bit 31 -> bit 32; SHL bit 63 -> 0 (overflow out of 64 bits)
        viewModel.onClear()
        viewModel.selectWordSize(64)
        viewModel.toggleBit(31)
        viewModel.onBitwise("SHL")
        assertEquals(1L shl 32, viewModel.state.value.rawValue)

        viewModel.onClear()
        viewModel.toggleBit(63)
        viewModel.onBitwise("SHL")
        assertEquals(0L, viewModel.state.value.rawValue)

        // SHR logical unsigned shift on bit 63 -> bit 62 (0x4000_0000_0000_0000)
        viewModel.onClear()
        viewModel.toggleBit(63)
        viewModel.onBitwise("SHR")
        assertEquals(1L shl 62, viewModel.state.value.rawValue)
        assertEquals("4000 0000 0000 0000", viewModel.state.value.hexValue)

        // Binary operations across high 32 bits (bits 32..63)
        viewModel.onClear()
        viewModel.selectBase(16)
        enterHex("F00000000000000F")
        viewModel.onBitwise("AND")
        enterHex("80000000000000FF")
        viewModel.onBitwise("=")
        assertEquals(Long.MIN_VALUE or 0x0FL, viewModel.state.value.rawValue)
        assertEquals("8000 0000 0000 000F", viewModel.state.value.hexValue)

        viewModel.onBitwise("OR")
        enterHex("0000000100000000")
        viewModel.onBitwise("=")
        assertEquals("8000 0001 0000 000F", viewModel.state.value.hexValue)

        viewModel.onBitwise("XOR")
        enterHex("800000000000000F")
        viewModel.onBitwise("=")
        assertEquals(1L shl 32, viewModel.state.value.rawValue)
        assertEquals("1 0000 0000", viewModel.state.value.hexValue)

        // NAND & NOR on 64-bit words
        viewModel.onClear()
        enterHex("FFFFFFFF00000000")
        viewModel.onBitwise("NAND")
        enterHex("FFFFFFFF00000000")
        viewModel.onBitwise("=")
        assertEquals(0x0000_0000_FFFF_FFFFL, viewModel.state.value.rawValue)
        assertEquals("FFFF FFFF", viewModel.state.value.hexValue)

        viewModel.onClear()
        enterHex("FFFFFFFF00000000")
        viewModel.onBitwise("NOR")
        enterHex("00000000FFFFFFFF")
        viewModel.onBitwise("=")
        assertEquals(0L, viewModel.state.value.rawValue)
    }

    private fun enterHex(hex: String) {
        hex.forEach { viewModel.onKeyPress(it.toString()) }
    }
}
