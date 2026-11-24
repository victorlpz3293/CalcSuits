package com.calcsuits.model

sealed class CalculatorOperation(val symbol: String) {
    object Add : CalculatorOperation("+")
    object Subtract : CalculatorOperation("-")
    object Multiply : CalculatorOperation("×")
    object Divide : CalculatorOperation("÷")
    object Power : CalculatorOperation("^")
    object Modulo : CalculatorOperation("mod")
}
