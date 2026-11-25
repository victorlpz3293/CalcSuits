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

    private fun enterHex(hex: String) {
        hex.forEach { viewModel.onKeyPress(it.toString()) }
    }
}
