package com.calcsuits.domain.model.finance

data class AmortizationRow(
    val period: Int,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val balance: Double
)

data class LoanResult(
    val monthlyPayment: Double,
    val totalInterest: Double,
    val totalPayment: Double,
    val schedule: List<AmortizationRow> = emptyList()
)
