package com.calcsuits.features.finance.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.calcsuits.R
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateCompoundingFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.domain.usecase.CalculateLoanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.pow

enum class FinanceTab(
    val key: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val summaryRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    LOAN(
        key = "loan",
        labelRes = R.string.finance_tab_loan,
        summaryRes = R.string.finance_card_loan_summary,
        descriptionRes = R.string.finance_card_loan_desc,
        icon = Icons.Rounded.AccountBalance
    ),
    INTEREST(
        key = "interest",
        labelRes = R.string.finance_tab_interest,
        summaryRes = R.string.finance_card_interest_summary,
        descriptionRes = R.string.finance_card_interest_desc,
        icon = Icons.AutoMirrored.Rounded.TrendingUp
    ),
    TAX(
        key = "tax",
        labelRes = R.string.finance_tab_tax,
        summaryRes = R.string.finance_card_tax_summary,
        descriptionRes = R.string.finance_card_tax_desc,
        icon = Icons.AutoMirrored.Rounded.ReceiptLong
    )
}

enum class CompoundingFrequency(@param:StringRes val labelRes: Int, val periodsPerYear: Int) {
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
    val totalAmount: Double,
    val appliedRatePercent: Double = 0.0
)

data class FinanceState(
    val selectedTab: FinanceTab? = null,
    val selectedCurrency: FinanceCurrency = FinanceCurrencies.USD,
    val amortizationSystem: AmortizationSystem = AmortizationSystem.FRENCH,
    val paymentFrequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val compoundingFrequency: RateCompoundingFrequency = RateCompoundingFrequency.MONTHLY,
    val compoundingFollowsPayment: Boolean = true,
    val loanTermUnit: LoanTermUnit = LoanTermUnit.YEARS,
    val rateConvention: RateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
    val amount: String = "10000",
    val downPayment: String = "",
    val downPaymentError: Boolean = false,
    val interestRate: String = "12",
    val years: String = "3",
    val loanResult: LoanResult? = null,
    val principal: String = "5000",
    val annualRate: String = "8",
    val termYears: String = "5",
    val frequency: CompoundingFrequency = CompoundingFrequency.MONTHLY,
    val interestResult: InterestComparisonResult? = null,
    val taxBaseInput: String = "1000",
    val taxRatePercent: String = "0",
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

    fun onBackToHub() {
        _state.update { it.copy(selectedTab = null) }
    }

    fun onCurrencySelected(currency: FinanceCurrency) {
        _state.update { it.copy(selectedCurrency = currency) }
        calculateLoan()
        calculateTax()
    }

    fun onAmortizationSystemSelected(system: AmortizationSystem) {
        if (system !in AmortizationSystem.simulatorSystems) return
        if (_state.value.amortizationSystem == system && _state.value.loanResult?.system == system) return
        _state.update { it.copy(amortizationSystem = system) }
        calculateLoan()
    }

    fun onPaymentFrequencySelected(frequency: PaymentFrequency) {
        _state.update {
            it.copy(
                paymentFrequency = frequency,
                compoundingFrequency = if (it.compoundingFollowsPayment) {
                    frequency.toCompoundingFrequency()
                } else {
                    it.compoundingFrequency
                }
            )
        }
        calculateLoan()
    }

    fun onCompoundingFrequencySelected(frequency: RateCompoundingFrequency) {
        _state.update {
            it.copy(
                compoundingFrequency = frequency,
                compoundingFollowsPayment = false
            )
        }
        calculateLoan()
    }

    fun onLoanTermUnitSelected(unit: LoanTermUnit) {
        val current = _state.value
        if (current.loanTermUnit == unit) return
        val convertedTerm = current.years.toIntOrNull()?.let { value ->
            when (unit) {
                LoanTermUnit.PERIODS -> (value * current.paymentFrequency.periodsPerYear)
                    .coerceIn(1, MAX_LOAN_PERIODS)
                    .toString()
                LoanTermUnit.YEARS -> ((value + current.paymentFrequency.periodsPerYear - 1) /
                    current.paymentFrequency.periodsPerYear)
                    .coerceIn(1, MAX_LOAN_YEARS)
                    .toString()
            }
        } ?: current.years
        _state.update { it.copy(loanTermUnit = unit, years = convertedTerm) }
        calculateLoan()
    }

    fun onRateConventionSelected(convention: RateConvention) {
        _state.update { it.copy(rateConvention = convention) }
        calculateLoan()
    }

    fun onAmountChange(value: String) {
        _state.update { it.copy(amount = sanitizeDecimal(value)) }
        calculateLoan()
    }

    fun onDownPaymentChange(value: String) {
        _state.update { it.copy(downPayment = sanitizeDecimal(value)) }
        calculateLoan()
    }

    fun onInterestRateChange(value: String) {
        _state.update { it.copy(interestRate = sanitizeDecimal(value)) }
        calculateLoan()
    }

    fun onYearsChange(value: String) {
        val maxDigits = if (_state.value.loanTermUnit == LoanTermUnit.YEARS) 2 else 4
        _state.update { it.copy(years = value.filter { c -> c.isDigit() }.take(maxDigits)) }
        calculateLoan()
    }

    private fun calculateLoan() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        val downPayment = if (s.downPayment.isBlank()) 0.0 else s.downPayment.toDoubleOrNull()
        val rate = s.interestRate.toDoubleOrNull()
        val term = s.years.toIntOrNull()

        val validTerm = when (s.loanTermUnit) {
            LoanTermUnit.YEARS -> term != null && term in 1..MAX_LOAN_YEARS
            LoanTermUnit.PERIODS -> term != null && term in 1..MAX_LOAN_PERIODS
        }

        val isDownPaymentTooHigh = amount != null && amount > 0.0 && downPayment != null && downPayment >= amount
        if (isDownPaymentTooHigh) {
            _state.update {
                it.copy(
                    downPaymentError = true,
                    loanResult = null
                )
            }
            return
        }

        if (amount != null && downPayment != null && rate != null && term != null &&
            amount > 0.0 && downPayment >= 0.0 && downPayment < amount &&
            rate >= 0.0 && rate <= MAX_ANNUAL_RATE_PERCENT && validTerm
        ) {
            val activeSystem = if (s.amortizationSystem in AmortizationSystem.simulatorSystems) {
                s.amortizationSystem
            } else {
                AmortizationSystem.FRENCH
            }
            val request = LoanRequest(
                amount = amount,
                downPayment = downPayment,
                annualRate = rate,
                termValue = term,
                termUnit = s.loanTermUnit,
                paymentFrequency = s.paymentFrequency,
                system = activeSystem,
                rateConvention = s.rateConvention,
                currency = s.selectedCurrency,
                issuedAtMillis = CalculateLoanUseCase.todayCivilDateUtcMillis(),
                compoundingFrequency = s.compoundingFrequency
            )
            val activeResult = runCatching { calculateLoanUseCase(request) }.getOrNull()
            _state.update {
                it.copy(
                    amortizationSystem = activeSystem,
                    downPaymentError = false,
                    loanResult = activeResult
                )
            }
        } else {
            _state.update {
                it.copy(
                    downPaymentError = false,
                    loanResult = null
                )
            }
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

        if (input != null && ratePct != null && input >= 0.0 && ratePct >= 0.0) {
            val mc = MathContext.DECIMAL128
            val inputBd = BigDecimal.valueOf(input)
            val rateBd = BigDecimal.valueOf(ratePct).divide(BigDecimal(100), mc)
            val scale = s.selectedCurrency.fractionDigits

            val result = if (s.taxIncluded) {
                val baseBd = inputBd.divide(BigDecimal.ONE.add(rateBd, mc), mc)
                val roundedTotal = inputBd.setScale(scale, RoundingMode.HALF_UP)
                val roundedBase = baseBd.setScale(scale, RoundingMode.HALF_UP)
                val roundedTax = roundedTotal.subtract(roundedBase)
                TaxResult(
                    baseAmount = roundedBase.toDouble(),
                    taxAmount = roundedTax.toDouble(),
                    totalAmount = roundedTotal.toDouble(),
                    appliedRatePercent = ratePct
                )
            } else {
                val roundedBase = inputBd.setScale(scale, RoundingMode.HALF_UP)
                val roundedTax = roundedBase.multiply(rateBd, mc).setScale(scale, RoundingMode.HALF_UP)
                val roundedTotal = roundedBase.add(roundedTax)
                TaxResult(
                    baseAmount = roundedBase.toDouble(),
                    taxAmount = roundedTax.toDouble(),
                    totalAmount = roundedTotal.toDouble(),
                    appliedRatePercent = ratePct
                )
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

    companion object {
        const val MAX_LOAN_YEARS = 50
        const val MAX_LOAN_PERIODS = 1_200
        const val MAX_ANNUAL_RATE_PERCENT = 1_000.0
    }
}
