package com.calcsuits.model

data class CalculatorHistoryEntry(
    val expression: String,
    val result: String
)

data class CalculatorState(
    val number1: String = "",
    val number2: String = "",
    val operation: CalculatorOperation? = null,
    val previousExpression: String = "",
    val livePreview: String = "",
    val errorMessage: String? = null,
    val memory: String = "0",
    val history: List<CalculatorHistoryEntry> = emptyList(),
    val isScientificExpanded: Boolean = false,
    val useRadians: Boolean = false,
    val hapticEnabled: Boolean = true,
    val decimalPrecision: Int = 8,
    val justCalculated: Boolean = false
)
