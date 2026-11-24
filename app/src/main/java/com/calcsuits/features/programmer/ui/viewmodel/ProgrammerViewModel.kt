package com.calcsuits.features.programmer.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.calcsuits.domain.usecase.programmer.ConvertBaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ProgrammerState(
    val rawValue: Long = 0L,
    val input: String = "0",
    val activeBase: Int = 10,
    val wordSize: Int = 32,
    val hexValue: String = "0",
    val decValue: String = "0",
    val octValue: String = "0",
    val binValue: String = "0000 0000 0000 0000 0000 0000 0000 0000",
    val pendingOperand: Long? = null,
    val pendingOperator: String? = null,
    val pendingExpression: String = "",
    val lastOperation: String? = null
)

@HiltViewModel
class ProgrammerViewModel @Inject constructor(
    private val convertBaseUseCase: ConvertBaseUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ProgrammerState())
    val state = _state.asStateFlow()

    fun selectBase(base: Int) {
        val current = _state.value
        val newInput = convertBaseUseCase.encodeRaw(current.rawValue, base, current.wordSize)
        _state.update { it.copy(activeBase = base, input = newInput) }
    }

    fun selectWordSize(size: Int) {
        val current = _state.value
        val normalized = convertBaseUseCase.normalizeSigned(current.rawValue, size)
        updateFromValue(normalized, size = size, base = current.activeBase)
    }

    fun onKeyPress(digit: String) {
        val current = _state.value
        val validChars = when (current.activeBase) {
            2 -> "01"
            8 -> "01234567"
            10 -> "0123456789"
            16 -> "0123456789ABCDEF"
            else -> "0123456789"
        }
        val upper = digit.uppercase()
        if (upper.length != 1 || upper[0] !in validChars) return

        val nextInput = if (current.input == "0") upper else current.input + upper
        val decoded = convertBaseUseCase.decode(nextInput, current.activeBase) ?: return
        val normalized = convertBaseUseCase.normalizeSigned(decoded, current.wordSize)
        updateFromValue(normalized, explicitInput = nextInput)
    }

    fun onBackspace() {
        val current = _state.value
        val next = current.input.dropLast(1).ifEmpty { "0" }
        val decoded = convertBaseUseCase.decode(next, current.activeBase) ?: 0L
        val normalized = convertBaseUseCase.normalizeSigned(decoded, current.wordSize)
        updateFromValue(normalized, explicitInput = next)
    }

    fun onClear() {
        _state.update {
            it.copy(
                rawValue = 0L,
                input = "0",
                hexValue = "0",
                decValue = "0",
                octValue = "0",
                binValue = convertBaseUseCase.encodeUnsigned(0L, 2, it.wordSize),
                pendingOperand = null,
                pendingOperator = null,
                pendingExpression = "",
                lastOperation = null
            )
        }
    }

    fun toggleBit(bitIndex: Int) {
        val current = _state.value
        if (bitIndex !in 0 until current.wordSize) return
        val flipped = current.rawValue xor (1L shl bitIndex)
        val normalized = convertBaseUseCase.normalizeSigned(flipped, current.wordSize)
        updateFromValue(normalized, lastOp = "Toggle bit #$bitIndex")
    }

    fun onBitwise(operation: String) {
        val current = _state.value
        when (operation) {
            "NOT" -> {
                val res = convertBaseUseCase.normalizeSigned(current.rawValue.inv(), current.wordSize)
                updateFromValue(res, lastOp = "NOT")
            }
            "SHL" -> {
                val res = convertBaseUseCase.normalizeSigned(current.rawValue shl 1, current.wordSize)
                updateFromValue(res, lastOp = "<< 1")
            }
            "SHR" -> {
                val masked = if (current.wordSize == 64) current.rawValue else current.rawValue and convertBaseUseCase.maskFor(current.wordSize)
                val res = convertBaseUseCase.normalizeSigned(masked ushr 1, current.wordSize)
                updateFromValue(res, lastOp = ">> 1")
            }
            "AND", "OR", "XOR", "NAND", "NOR" -> {
                val left = if (current.pendingOperand != null && current.pendingOperator != null) {
                    applyBinary(current.pendingOperand, current.rawValue, current.pendingOperator, current.wordSize)
                } else {
                    current.rawValue
                }
                val label = "${convertBaseUseCase.encodeRaw(left, current.activeBase, current.wordSize)} $operation"
                _state.update {
                    it.copy(
                        pendingOperand = left,
                        pendingOperator = operation,
                        pendingExpression = label,
                        rawValue = 0L,
                        input = "0",
                        hexValue = "0",
                        decValue = "0",
                        octValue = "0",
                        binValue = convertBaseUseCase.encodeUnsigned(0L, 2, current.wordSize)
                    )
                }
            }
            "=" -> {
                val left = current.pendingOperand ?: return
                val op = current.pendingOperator ?: return
                val res = applyBinary(left, current.rawValue, op, current.wordSize)
                val leftStr = convertBaseUseCase.encodeRaw(left, current.activeBase, current.wordSize)
                val rightStr = convertBaseUseCase.encodeRaw(current.rawValue, current.activeBase, current.wordSize)
                _state.update {
                    it.copy(pendingOperand = null, pendingOperator = null, pendingExpression = "")
                }
                updateFromValue(res, lastOp = "$leftStr $op $rightStr")
            }
        }
    }

    private fun applyBinary(a: Long, b: Long, op: String, wordSize: Int): Long {
        val raw = when (op) {
            "AND" -> a and b
            "OR" -> a or b
            "XOR" -> a xor b
            "NAND" -> (a and b).inv()
            "NOR" -> (a or b).inv()
            else -> b
        }
        return convertBaseUseCase.normalizeSigned(raw, wordSize)
    }

    private fun updateFromValue(
        value: Long,
        size: Int = _state.value.wordSize,
        base: Int = _state.value.activeBase,
        explicitInput: String? = null,
        lastOp: String? = _state.value.lastOperation
    ) {
        val rawInput = explicitInput ?: convertBaseUseCase.encodeRaw(value, base, size)
        _state.update {
            it.copy(
                rawValue = value,
                wordSize = size,
                activeBase = base,
                input = rawInput,
                hexValue = convertBaseUseCase.encodeUnsigned(value, 16, size),
                decValue = convertBaseUseCase.normalizeSigned(value, size).toString(),
                octValue = convertBaseUseCase.encodeUnsigned(value, 8, size),
                binValue = convertBaseUseCase.encodeUnsigned(value, 2, size),
                lastOperation = lastOp
            )
        }
    }
}
