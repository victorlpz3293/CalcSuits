package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationRow
import com.calcsuits.domain.model.finance.LoanResult
import javax.inject.Inject
import kotlin.math.pow

class CalculateLoanUseCase @Inject constructor() {
    operator fun invoke(amount: Double, annualRate: Double, years: Int): LoanResult {
        require(amount > 0 && annualRate >= 0 && years > 0)
        val monthlyRate = annualRate / 100.0 / 12.0
        val numberOfPayments = years * 12
        val monthlyPayment = if (monthlyRate == 0.0) amount / numberOfPayments else
            (amount * monthlyRate) / (1 - (1 + monthlyRate).pow(-numberOfPayments.toDouble()))

        var balance = amount
        val schedule = buildList {
            for (period in 1..numberOfPayments) {
                val interest = if (monthlyRate == 0.0) 0.0 else balance * monthlyRate
                val principal = if (period == numberOfPayments) balance else monthlyPayment - interest
                val payment = principal + interest
                balance = (balance - principal).coerceAtLeast(0.0)
                add(AmortizationRow(period, payment, principal, interest, balance))
            }
        }
        val totalPayment = schedule.sumOf { it.payment }
        return LoanResult(monthlyPayment, totalPayment - amount, totalPayment, schedule)
    }
}
