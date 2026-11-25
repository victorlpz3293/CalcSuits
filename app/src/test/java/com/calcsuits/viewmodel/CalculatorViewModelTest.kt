package com.calcsuits.viewmodel

import com.calcsuits.R
import com.calcsuits.data.local.datastore.AppSettings
import com.calcsuits.domain.model.History
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.domain.usecase.CalculateExpressionUseCase
import com.calcsuits.model.CalculatorAction
import com.calcsuits.model.CalculatorOperation
import com.calcsuits.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCalculatorRepository
    private lateinit var viewModel: CalculatorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCalculatorRepository()
        viewModel = CalculatorViewModel(repository, CalculateExpressionUseCase())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun memoryOperationsAddSubtractRecallClearAndSurviveAc() = runTest {
        enterDigits("15")
        viewModel.onAction(CalculatorAction.MemoryAdd)
        assertEquals("15", viewModel.state.value.memory)

        viewModel.onAction(CalculatorAction.Clear)
        assertEquals("15", viewModel.state.value.memory)

        enterDigits("5")
        viewModel.onAction(CalculatorAction.MemoryAdd)
        assertEquals("20", viewModel.state.value.memory)

        viewModel.onAction(CalculatorAction.Clear)
        enterDigits("8")
        viewModel.onAction(CalculatorAction.MemorySubtract)
        assertEquals("12", viewModel.state.value.memory)

        // Recall into first operand
        viewModel.onAction(CalculatorAction.Clear)
        viewModel.onAction(CalculatorAction.MemoryRecall)
        assertEquals("12", viewModel.state.value.number1)

        // Recall into second operand and calculate
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Add))
        viewModel.onAction(CalculatorAction.MemoryRecall)
        assertEquals("12", viewModel.state.value.number2)
        assertEquals("24", viewModel.state.value.livePreview)
        viewModel.onAction(CalculatorAction.Calculate)
        assertEquals("24", viewModel.state.value.number1)

        // Memory clear
        viewModel.onAction(CalculatorAction.MemoryClear)
        assertEquals("0", viewModel.state.value.memory)
    }

    @Test
    fun chainingOperationsCalculatesIntermediateResultsAndStartsFreshOnNewDigit() = runTest {
        // 2 + 3 × 4 = 20
        enterDigits("2")
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Add))
        enterDigits("3")
        assertEquals("5", viewModel.state.value.livePreview)

        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Multiply))
        assertEquals("5", viewModel.state.value.number1)
        assertEquals("", viewModel.state.value.number2)
        assertEquals(CalculatorOperation.Multiply, viewModel.state.value.operation)

        enterDigits("4")
        viewModel.onAction(CalculatorAction.Calculate)
        assertEquals("20", viewModel.state.value.number1)
        assertNull(viewModel.state.value.operation)
        assertEquals(2, viewModel.state.value.history.size)

        // Continuing with an operator uses previous result: 20 - 5 = 15
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Subtract))
        enterDigits("5")
        viewModel.onAction(CalculatorAction.Calculate)
        assertEquals("15", viewModel.state.value.number1)

        // Pressing a digit right after '=' starts a fresh number
        viewModel.onAction(CalculatorAction.Number(9))
        assertEquals("9", viewModel.state.value.number1)
        assertEquals("", viewModel.state.value.previousExpression)
    }

    @Test
    fun clearAllResetsExpressionPreviewAndErrors() = runTest {
        enterDigits("12")
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Add))
        enterDigits("34")
        assertEquals("46", viewModel.state.value.livePreview)

        viewModel.onAction(CalculatorAction.Clear)
        val s = viewModel.state.value
        assertEquals("", s.number1)
        assertEquals("", s.number2)
        assertNull(s.operation)
        assertEquals("", s.previousExpression)
        assertEquals("", s.livePreview)
        assertNull(s.errorMessage)
    }

    @Test
    fun deleteRemovesSecondOperandThenOperationThenFirstOperandAndClearsErrors() = runTest {
        enterDigits("12")
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Add))
        enterDigits("34")

        viewModel.onAction(CalculatorAction.Delete)
        assertEquals("3", viewModel.state.value.number2)
        assertEquals("15", viewModel.state.value.livePreview)

        viewModel.onAction(CalculatorAction.Delete)
        assertEquals("", viewModel.state.value.number2)
        assertEquals(CalculatorOperation.Add, viewModel.state.value.operation)

        viewModel.onAction(CalculatorAction.Delete)
        assertNull(viewModel.state.value.operation)
        assertEquals("12", viewModel.state.value.number1)

        viewModel.onAction(CalculatorAction.Delete)
        assertEquals("1", viewModel.state.value.number1)

        viewModel.onAction(CalculatorAction.Delete)
        assertEquals("", viewModel.state.value.number1)

        // Delete clears error state
        enterDigits("5")
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Divide))
        enterDigits("0")
        viewModel.onAction(CalculatorAction.Calculate)
        assertEquals(UiText.StringResource(R.string.error_division_by_zero), viewModel.state.value.errorMessage)

        viewModel.onAction(CalculatorAction.Delete)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals("", viewModel.state.value.number1)
    }

    @Test
    fun percentageAndErrorSequencesProduceExpectedResultsAndTypedUiText() = runTest {
        // 200 + 10% = 220
        enterDigits("200")
        viewModel.onAction(CalculatorAction.Operation(CalculatorOperation.Add))
        enterDigits("10")
        viewModel.onAction(CalculatorAction.Percentage)
        assertEquals("20", viewModel.state.value.number2)
        viewModel.onAction(CalculatorAction.Calculate)
        assertEquals("220", viewModel.state.value.number1)

        // Unary negative root produces typed UiText error
        viewModel.onAction(CalculatorAction.Clear)
        enterDigits("9")
        viewModel.onAction(CalculatorAction.ToggleSign)
        viewModel.onAction(CalculatorAction.SquareRoot)
        assertEquals(UiText.StringResource(R.string.error_negative_root), viewModel.state.value.errorMessage)
    }

    private fun enterDigits(digits: String) {
        digits.forEach { ch ->
            when (ch) {
                '.' -> viewModel.onAction(CalculatorAction.Decimal)
                else -> viewModel.onAction(CalculatorAction.Number(ch.digitToInt()))
            }
        }
    }

    private class FakeCalculatorRepository : CalculatorRepository {
        private val historyFlow = MutableStateFlow<List<History>>(emptyList())
        private val settingsFlow = MutableStateFlow(AppSettings())

        override fun getHistory(): Flow<List<History>> = historyFlow

        override suspend fun saveHistory(expression: String, result: String) {
            historyFlow.update { current ->
                listOf(
                    History(
                        id = (current.size + 1).toLong(),
                        expression = expression,
                        result = result,
                        timestamp = current.size.toLong()
                    )
                ) + current
            }
        }

        override suspend fun clearHistory() {
            historyFlow.value = emptyList()
        }

        override fun getSettings(): Flow<AppSettings> = settingsFlow

        override fun isScientificExpanded(): Flow<Boolean> = settingsFlow.map { it.isScientificExpanded }

        override suspend fun setScientificExpanded(isExpanded: Boolean) {
            settingsFlow.update { it.copy(isScientificExpanded = isExpanded) }
        }

        override suspend fun setThemeMode(mode: AppThemeMode) {
            settingsFlow.update { it.copy(themeMode = mode) }
        }

        override suspend fun setUseOledBlack(enabled: Boolean) {
            settingsFlow.update { it.copy(useOledBlack = enabled) }
        }

        override suspend fun setUseRadians(useRadians: Boolean) {
            settingsFlow.update { it.copy(useRadians = useRadians) }
        }

        override suspend fun setDecimalPrecision(precision: Int) {
            settingsFlow.update { it.copy(decimalPrecision = precision) }
        }

        override suspend fun setHapticEnabled(enabled: Boolean) {
            settingsFlow.update { it.copy(hapticEnabled = enabled) }
        }
    }
}
