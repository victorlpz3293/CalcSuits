package com.calcsuits.domain.model.finance

/**
 * Amortization methods supported by the loan engine.
 */
enum class AmortizationSystem {
    /** Constant periodic installment, decreasing interest, increasing principal amortization. */
    FRENCH,

    /** Constant principal amortization per period, decreasing interest, decreasing installments. */
    GERMAN,

    /** Periodic interest-only payments with full principal repayment at maturity. */
    AMERICAN
}

/**
 * Payment schedule periodicity (number of installments per year).
 *
 * Note: Payment periodicity defines *when* installments are paid and must not be confused
 * with compounding frequency; see [RateConvention] for how the annual rate is mapped to
 * each payment period.
 */
enum class PaymentFrequency(val periodsPerYear: Int) {
    MONTHLY(12),
    SEMIMONTHLY(24),
    QUARTERLY(4),
    SEMIANNUAL(2),
    ANNUAL(1)
}

/**
 * Unit for specifying loan term duration.
 */
enum class LoanTermUnit {
    /** Term expressed in full years; total installments = termValue * paymentFrequency.periodsPerYear. */
    YEARS,

    /** Term expressed directly as total number of installments (periods). */
    PERIODS
}

/**
 * Explicit rate conversion assumption used to derive the periodic rate from the annual rate input.
 */
enum class RateConvention {
    /**
     * Nominal Annual Rate (TNA / APR nominal) compounded at the same frequency as payments:
     * i_period = annualRate / periodsPerYear.
     */
    NOMINAL_PER_PAYMENT_PERIOD,

    /**
     * Effective Annual Rate (TEA / EAR) converted to an equivalent periodic rate:
     * i_period = (1 + annualRate)^(1 / periodsPerYear) - 1.
     */
    EFFECTIVE_ANNUAL
}

data class LoanRequest(
    val amount: Double,
    val annualRate: Double,
    val termValue: Int,
    val termUnit: LoanTermUnit = LoanTermUnit.YEARS,
    val paymentFrequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val system: AmortizationSystem = AmortizationSystem.FRENCH,
    val rateConvention: RateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
    val currency: FinanceCurrency = FinanceCurrencies.USD
)

data class AmortizationRow(
    val period: Int,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val balance: Double
)

data class LoanResult(
    /** Primary periodic payment metric (kept as [monthlyPayment] for backwards compatibility). */
    val monthlyPayment: Double,
    val totalInterest: Double,
    val totalPayment: Double,
    val schedule: List<AmortizationRow> = emptyList(),
    val system: AmortizationSystem = AmortizationSystem.FRENCH,
    val paymentFrequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val rateConvention: RateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
    val currency: FinanceCurrency = FinanceCurrencies.USD,
    val principalAmount: Double = 0.0,
    /** Effective periodic interest rate as a decimal (e.g., 0.01 = 1% per period). */
    val periodicRate: Double = 0.0,
    /** Equivalent effective annual rate as a decimal (e.g., 0.126825 = 12.6825% EAR). */
    val effectiveAnnualRate: Double = 0.0,
    val firstPayment: Double = monthlyPayment,
    val lastPayment: Double = schedule.lastOrNull()?.payment ?: monthlyPayment,
    /** Constant principal installment per period for [AmortizationSystem.GERMAN]; null otherwise. */
    val constantPrincipalPerPeriod: Double? = null,
    val finalBalance: Double = schedule.lastOrNull()?.balance ?: 0.0
)
