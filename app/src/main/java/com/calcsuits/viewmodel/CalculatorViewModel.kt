package com.calcsuits.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.domain.usecase.CalculateExpressionUseCase
import com.calcsuits.domain.usecase.MathError
import com.calcsuits.domain.usecase.toMathUiText
import com.calcsuits.model.CalculatorAction
import com.calcsuits.model.CalculatorHistoryEntry
import com.calcsuits.model.CalculatorOperation
import com.calcsuits.model.CalculatorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val repository: CalculatorRepository,
    private val calculateUseCase: CalculateExpressionUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(CalculatorState())
    val state = _state.asStateFlow()
    private val mathContext = MathContext(16, RoundingMode.HALF_UP)

    init {
        repository.getHistory().onEach { historyList ->
            _state.update {
                it.copy(history = historyList.map { item ->
                    CalculatorHistoryEntry(item.expression, item.result)
                })
            }
        }.launchIn(viewModelScope)

        repository.getSettings().onEach { settings ->
            _state.update {
                it.copy(
                    isScientificExpanded = settings.isScientificExpanded,
                    useRadians = settings.useRadians,
                    hapticEnabled = settings.hapticEnabled,
                    decimalPrecision = settings.decimalPrecision
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Number -> enterNumber(action.number)
            is CalculatorAction.Decimal -> enterDecimal()
            CalculatorAction.Clear -> clearAll()
            CalculatorAction.Delete -> performDeletion()
            is CalculatorAction.Operation -> enterOperation(action.operation)
            CalculatorAction.Calculate -> performCalculation()
            CalculatorAction.ToggleSign -> toggleSign()
            CalculatorAction.Percentage -> performPercentage()
            CalculatorAction.SquareRoot -> performScientificUnary("sqrt")
            CalculatorAction.Square -> performScientificUnary("pow2")
            is CalculatorAction.ScientificFunction -> performScientificUnary(action.function)
            is CalculatorAction.Constant -> enterConstant(action.symbol)
            CalculatorAction.ToggleScientific -> toggleScientific()
            CalculatorAction.ToggleAngleUnit -> toggleAngleUnit()
            CalculatorAction.MemoryAdd -> updateMemory(true)
            CalculatorAction.MemorySubtract -> updateMemory(false)
            CalculatorAction.MemoryRecall -> recallMemory()
            CalculatorAction.MemoryClear -> _state.update { it.copy(memory = "0") }
            is CalculatorAction.PasteFromClipboard -> pasteNumber(action.content)
            CalculatorAction.ClearHistory -> clearHistory()
            is CalculatorAction.ReuseHistoryResult -> reuseResult(action.result)
            else -> Unit
        }
    }

    private fun clearHistory() = viewModelScope.launch { repository.clearHistory() }

    private fun toggleScientific() = viewModelScope.launch {
        repository.setScientificExpanded(!_state.value.isScientificExpanded)
    }

    private fun toggleAngleUnit() = viewModelScope.launch {
        repository.setUseRadians(!_state.value.useRadians)
    }

    private fun clearAll() {
        _state.update {
            it.copy(
                number1 = "",
                number2 = "",
                operation = null,
                previousExpression = "",
                livePreview = "",
                errorMessage = null,
                justCalculated = false
            )
        }
    }

    private fun enterNumber(number: Int) {
        _state.update { current ->
            val resetState = if (current.errorMessage != null || (current.justCalculated && current.operation == null)) {
                current.copy(number1 = "", number2 = "", operation = null, previousExpression = "", errorMessage = null, justCalculated = false)
            } else {
                current.copy(errorMessage = null)
            }
            val updated = if (resetState.operation == null) {
                val base = if (resetState.number1 == "0") "" else resetState.number1
                if (base.length >= 15) resetState else resetState.copy(number1 = base + number)
            } else {
                val base = if (resetState.number2 == "0") "" else resetState.number2
                if (base.length >= 15) resetState else resetState.copy(number2 = base + number)
            }
            updated.withLivePreview()
        }
    }

    private fun enterConstant(symbol: String) {
        val value = calculateUseCase.constantValue(symbol, _state.value.decimalPrecision)
        _state.update { current ->
            val updated = if (current.operation == null) {
                current.copy(number1 = value, errorMessage = null, justCalculated = false)
            } else {
                current.copy(number2 = value, errorMessage = null, justCalculated = false)
            }
            updated.withLivePreview()
        }
    }

    private fun enterOperation(operation: CalculatorOperation) {
        val current = _state.value
        if (current.errorMessage != null) return
        if (current.number1.isBlank()) {
            if (operation == CalculatorOperation.Subtract) {
                _state.update { it.copy(number1 = "-", justCalculated = false) }
            } else {
                _state.update { it.copy(number1 = "0", operation = operation, justCalculated = false) }
            }
            return
        }
        if (current.number1 == "-") return
        if (current.number2.isNotBlank()) {
            performCalculation()
        }
        _state.update {
            if (it.errorMessage != null) it
            else it.copy(operation = operation, justCalculated = false).withLivePreview()
        }
    }

    private fun performCalculation() {
        val current = _state.value
        if (current.number1.isBlank() || current.number2.isBlank() || current.operation == null) return

        val displayExpr = "${current.number1} ${current.operation.symbol} ${current.number2}"
        calculateUseCase.invoke(
            number1 = current.number1,
            number2 = current.number2,
            operation = current.operation,
            maxDecimals = current.decimalPrecision
        ).fold(
            onSuccess = { result ->
                viewModelScope.launch { repository.saveHistory(displayExpr, result) }
                _state.update {
                    it.copy(
                        number1 = result,
                        number2 = "",
                        operation = null,
                        previousExpression = "$displayExpr =",
                        livePreview = "",
                        errorMessage = null,
                        justCalculated = true
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        number1 = "",
                        number2 = "",
                        operation = null,
                        previousExpression = displayExpr,
                        livePreview = "",
                        errorMessage = error.toMathUiText(MathError.CalculationError),
                        justCalculated = false
                    )
                }
            }
        )
    }

    private fun performScientificUnary(function: String) {
        val current = _state.value
        val numberToProcess = when {
            current.errorMessage != null -> return
            current.operation != null -> current.number2.ifBlank { return }
            else -> current.number1.ifBlank { "0" }
        }

        val result = if (function == "pow2") {
            runCatching {
                val sq = BigDecimal(numberToProcess).pow(2, mathContext)
                calculateUseCase.formatResult(sq, current.decimalPrecision)
            }
        } else {
            calculateUseCase.calculateUnary(
                number = numberToProcess,
                function = function,
                useRadians = current.useRadians,
                maxDecimals = current.decimalPrecision
            )
        }

        result.fold(
            onSuccess = { value ->
                val exprLabel = when (function) {
                    "sqrt" -> "√($numberToProcess)"
                    "pow2" -> "($numberToProcess)²"
                    "fact" -> "($numberToProcess)!"
                    "inv" -> "1/($numberToProcess)"
                    else -> "$function($numberToProcess)"
                }
                viewModelScope.launch { repository.saveHistory(exprLabel, value) }
                _state.update {
                    val updated = if (it.operation == null) {
                        it.copy(
                            number1 = value,
                            previousExpression = "$exprLabel =",
                            errorMessage = null,
                            justCalculated = true
                        )
                    } else {
                        it.copy(number2 = value, errorMessage = null)
                    }
                    updated.withLivePreview()
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        livePreview = "",
                        errorMessage = error.toMathUiText(MathError.InvalidOperation),
                        justCalculated = false
                    )
                }
            }
        )
    }

    private fun enterDecimal() {
        _state.update { current ->
            val baseState = if (current.errorMessage != null || (current.justCalculated && current.operation == null)) {
                current.copy(number1 = "", number2 = "", operation = null, previousExpression = "", errorMessage = null, justCalculated = false)
            } else current

            val updated = if (baseState.operation == null) {
                when {
                    baseState.number1.isBlank() -> baseState.copy(number1 = "0.")
                    baseState.number1 == "-" -> baseState.copy(number1 = "-0.")
                    !baseState.number1.contains('.') -> baseState.copy(number1 = baseState.number1 + '.')
                    else -> baseState
                }
            } else {
                when {
                    baseState.number2.isBlank() -> baseState.copy(number2 = "0.")
                    baseState.number2 == "-" -> baseState.copy(number2 = "-0.")
                    !baseState.number2.contains('.') -> baseState.copy(number2 = baseState.number2 + '.')
                    else -> baseState
                }
            }
            updated.withLivePreview()
        }
    }

    private fun performDeletion() {
        _state.update { current ->
            if (current.errorMessage != null) {
                return@update current.copy(errorMessage = null, number1 = "", number2 = "", operation = null, livePreview = "")
            }
            val updated = when {
                current.number2.isNotBlank() -> current.copy(number2 = current.number2.dropLast(1))
                current.operation != null -> current.copy(operation = null)
                current.number1.isNotBlank() -> current.copy(number1 = current.number1.dropLast(1), justCalculated = false)
                else -> current
            }
            updated.withLivePreview()
        }
    }

    private fun toggleSign() {
        _state.update { current ->
            if (current.errorMessage != null) return@update current
            val updated = if (current.operation == null && current.number1.isNotBlank()) {
                current.copy(
                    number1 = if (current.number1.startsWith('-')) current.number1.drop(1) else "-${current.number1}",
                    justCalculated = false
                )
            } else if (current.number2.isNotBlank()) {
                current.copy(
                    number2 = if (current.number2.startsWith('-')) current.number2.drop(1) else "-${current.number2}"
                )
            } else current
            updated.withLivePreview()
        }
    }

    private fun performPercentage() {
        _state.update { current ->
            if (current.errorMessage != null) return@update current
            val updated = if (current.operation != null && current.number2.isNotBlank()) {
                val n1 = current.number1.toBigDecimalOrNull() ?: return@update current
                val n2 = current.number2.toBigDecimalOrNull() ?: return@update current
                val pct = when (current.operation) {
                    CalculatorOperation.Add, CalculatorOperation.Subtract ->
                        n1.multiply(n2, mathContext).divide(BigDecimal("100"), mathContext)
                    else ->
                        n2.divide(BigDecimal("100"), mathContext)
                }
                current.copy(number2 = calculateUseCase.formatResult(pct, current.decimalPrecision))
            } else if (current.number1.isNotBlank()) {
                val n1 = current.number1.toBigDecimalOrNull() ?: return@update current
                val pct = n1.divide(BigDecimal("100"), mathContext)
                current.copy(number1 = calculateUseCase.formatResult(pct, current.decimalPrecision), justCalculated = false)
            } else current
            updated.withLivePreview()
        }
    }

    private fun updateMemory(add: Boolean) {
        val current = _state.value
        val value = current.number2.ifBlank { current.number1 }.toBigDecimalOrNull() ?: return
        val memory = current.memory.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val result = if (add) memory.add(value, mathContext) else memory.subtract(value, mathContext)
        _state.update { it.copy(memory = calculateUseCase.formatResult(result, current.decimalPrecision)) }
    }

    private fun recallMemory() {
        _state.update { current ->
            val updated = if (current.operation == null) {
                current.copy(number1 = current.memory, errorMessage = null, justCalculated = false)
            } else {
                current.copy(number2 = current.memory, errorMessage = null)
            }
            updated.withLivePreview()
        }
    }

    private fun reuseResult(result: String) {
        val cleaned = result.trim()
        if (cleaned.toBigDecimalOrNull() == null) return
        _state.update { current ->
            val updated = if (current.operation == null) {
                current.copy(number1 = cleaned, errorMessage = null, justCalculated = false)
            } else {
                current.copy(number2 = cleaned, errorMessage = null)
            }
            updated.withLivePreview()
        }
    }

    private fun pasteNumber(content: String) {
        val cleaned = content.trim().filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.toBigDecimalOrNull() == null) return
        reuseResult(cleaned)
    }

    private fun CalculatorState.withLivePreview(): CalculatorState {
        if (number1.isBlank() || number2.isBlank() || operation == null) {
            return copy(livePreview = "")
        }
        val preview = calculateUseCase.invoke(
            number1 = number1,
            number2 = number2,
            operation = operation,
            maxDecimals = decimalPrecision
        ).getOrNull().orEmpty()
        return copy(livePreview = preview)
    }
}
