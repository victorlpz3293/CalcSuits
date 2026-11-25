package com.calcsuits.features.finance.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.calcsuits.R
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.usecase.CalculateLoanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.pow

enum class FinanceTab(@StringRes val labelRes: Int) {
    LOAN(R.string.finance_tab_loan),
    INTEREST(R.string.finance_tab_interest),
    TAX(R.string.finance_tab_tax)
}

enum class CompoundingFrequency(@StringRes val labelRes: Int, val periodsPerYear: Int) {
    ANNUAL(R.string.finance_freq_annual, 1),
    SEMIANNUAL(R.string.finance_freq_semiannual, 2),
    QUARTERLY(R.string.finance_freq_quarterly, 4),
    MONTHLY(R.string.finance_freq_monthly, 12),
    DAILY(R.string.finance_freq_daily, 365)
}

data class InterestComparisonResult(
    val simpleInterest: Double,
    val simpleTotal: Double,
    val compoundInterest: Double,
    val compoundTotal: Double
)

data class TaxResult(
    val baseAmount: Double,
    val taxAmount: Double,
    val totalAmount: Double
)

data class FinanceState(
    val selectedTab: FinanceTab = FinanceTab.LOAN,
    val amount: String = "10000",
    val interestRate: String = "12",
    val years: String = "3",
    val loanResult: LoanResult? = null,
    val principal: String = "5000",
    val annualRate: String = "8",
    val termYears: String = "5",
    val frequency: CompoundingFrequency = CompoundingFrequency.MONTHLY,
    val interestResult: InterestComparisonResult? = null,
    val taxBaseInput: String = "1000",
    val taxRatePercent: String = "15",
    val taxIncluded: Boolean = false,
    val taxResult: TaxResult? = null
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val calculateLoanUseCase: CalculateLoanUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FinanceState())
    val state = _state.asStateFlow()

    init {
        calculateLoan()
        calculateInterest()
        calculateTax()
    }

    fun onTabSelected(tab: FinanceTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onAmountChange(value: String) {
        _state.update { it.copy(amount = sanitizeDecimal(value)) }
        calculateLoan()
    }

    fun onInterestRateChange(value: String) {
        _state.update { it.copy(interestRate = sanitizeDecimal(value)) }
        calculateLoan()
    }

    fun onYearsChange(value: String) {
        _state.update { it.copy(years = value.filter { c -> c.isDigit() }.take(2)) }
        calculateLoan()
    }

    private fun calculateLoan() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        val rate = s.interestRate.toDoubleOrNull()
        val years = s.years.toIntOrNull()

        if (amount != null && rate != null && years != null && amount > 0 && rate >= 0 && years in 1..50) {
            val result = calculateLoanUseCase(amount, rate, years)
            _state.update { it.copy(loanResult = result) }
        } else {
            _state.update { it.copy(loanResult = null) }
        }
    }

    fun onPrincipalChange(value: String) {
        _state.update { it.copy(principal = sanitizeDecimal(value)) }
        calculateInterest()
    }

    fun onAnnualRateChange(value: String) {
        _state.update { it.copy(annualRate = sanitizeDecimal(value)) }
        calculateInterest()
    }

    fun onTermYearsChange(value: String) {
        _state.update { it.copy(termYears = sanitizeDecimal(value)) }
        calculateInterest()
    }

    fun onFrequencyChange(freq: CompoundingFrequency) {
        _state.update { it.copy(frequency = freq) }
        calculateInterest()
    }

    private fun calculateInterest() {
        val s = _state.value
        val p = s.principal.toDoubleOrNull()
        val rPct = s.annualRate.toDoubleOrNull()
        val t = s.termYears.toDoubleOrNull()

        if (p != null && rPct != null && t != null && p > 0 && rPct >= 0 && t > 0 && t <= 100) {
            val r = rPct / 100.0
            val n = s.frequency.periodsPerYear.toDouble()
            val simpleInterest = p * r * t
            val simpleTotal = p + simpleInterest
            val compoundTotal = p * (1.0 + r / n).pow(n * t)
            val compoundInterest = compoundTotal - p
            if (compoundTotal.isFinite()) {
                _state.update {
                    it.copy(
                        interestResult = InterestComparisonResult(
                            simpleInterest = simpleInterest,
                            simpleTotal = simpleTotal,
                            compoundInterest = compoundInterest,
                            compoundTotal = compoundTotal
                        )
                    )
                }
                return
            }
        }
        _state.update { it.copy(interestResult = null) }
    }

    fun onTaxBaseChange(value: String) {
        _state.update { it.copy(taxBaseInput = sanitizeDecimal(value)) }
        calculateTax()
    }

    fun onTaxRateChange(value: String) {
        _state.update { it.copy(taxRatePercent = sanitizeDecimal(value)) }
        calculateTax()
    }

    fun onTaxIncludedChange(included: Boolean) {
        _state.update { it.copy(taxIncluded = included) }
        calculateTax()
    }

    private fun calculateTax() {
        val s = _state.value
        val input = s.taxBaseInput.toDoubleOrNull()
        val ratePct = s.taxRatePercent.toDoubleOrNull()

        if (input != null && ratePct != null && input >= 0 && ratePct >= 0) {
            val rate = ratePct / 100.0
            val result = if (s.taxIncluded) {
                val base = input / (1.0 + rate)
                val tax = input - base
                TaxResult(baseAmount = base, taxAmount = tax, totalAmount = input)
            } else {
                val tax = input * rate
                TaxResult(baseAmount = input, taxAmount = tax, totalAmount = input + tax)
            }
            _state.update { it.copy(taxResult = result) }
        } else {
            _state.update { it.copy(taxResult = null) }
        }
    }

    private fun sanitizeDecimal(value: String): String {
        var dotSeen = false
        return buildString {
            value.forEach { c ->
                when {
                    c.isDigit() -> append(c)
                    c == '.' && !dotSeen -> {
                        dotSeen = true
                        append(c)
                    }
                }
            }
        }
    }
}
