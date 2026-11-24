package com.calcsuits.model

sealed class CalculatorAction {
    data class Number(val number: Int) : CalculatorAction()
    object Clear : CalculatorAction()
    object Delete : CalculatorAction()
    object Decimal : CalculatorAction()
    object Calculate : CalculatorAction()
    object ToggleSign : CalculatorAction()
    object Percentage : CalculatorAction()
    object SquareRoot : CalculatorAction()
    object Square : CalculatorAction()
    object ToggleScientific : CalculatorAction()
    object ToggleAngleUnit : CalculatorAction()
    data class Operation(val operation: CalculatorOperation) : CalculatorAction()

    // Funciones Científicas y Constantes
    data class ScientificFunction(val function: String) : CalculatorAction()
    data class Constant(val symbol: String) : CalculatorAction()

    // Portapapeles
    data class CopyToClipboard(val content: String) : CalculatorAction()
    data class PasteFromClipboard(val content: String) : CalculatorAction()

    // Memoria
    object MemoryAdd : CalculatorAction()
    object MemorySubtract : CalculatorAction()
    object MemoryRecall : CalculatorAction()
    object MemoryClear : CalculatorAction()

    // Historial
    object ClearHistory : CalculatorAction()
    data class ReuseHistoryResult(val result: String) : CalculatorAction()
}
