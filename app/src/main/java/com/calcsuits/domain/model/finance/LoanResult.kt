package com.calcsuits.domain.model.finance

/**
 * Amortization methods supported by the loan engine.
 *
 * The Loan Simulator UI exposes [simulatorSystems] ([FRENCH] and [GERMAN]).
 * [AMERICAN] is preserved in the domain enum solely for backwards and historical compatibility.
 */
enum class AmortizationSystem {
    /** Fixed periodic installment, decreasing interest, increasing principal repayment (French). */
    FRENCH,

    /** Constant principal repayment per period, decreasing interest, declining installments (German). */
    GERMAN,

    /** Legacy domain entry preserved for historical compatibility; not offered in the simulator UI. */
    AMERICAN;

    companion object {
        /** Amortization systems active in the Loan Simulator. */
        val simulatorSystems: List<AmortizationSystem> = listOf(FRENCH, GERMAN)
    }
}

/**
 * Operating mode for the Loan Simulator.
 */
enum class LoanSimulationMode {
    /** Simulates a single selected amortization system ([AmortizationSystem.FRENCH] or [AmortizationSystem.GERMAN]). */
    INDIVIDUAL,

    /** Compares French and German amortization side by side under identical loan parameters. */
    COMPARISON
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
     * Nominal Annual Rate (TNA / APR nominal) capitalized at the same frequency as payments:
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
    /** Requested loan amount prior to any optional down payment. */
    val amount: Double,
    val annualRate: Double,
    val termValue: Int,
    val termUnit: LoanTermUnit = LoanTermUnit.YEARS,
    val paymentFrequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val system: AmortizationSystem = AmortizationSystem.FRENCH,
    val rateConvention: RateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
    val currency: FinanceCurrency = FinanceCurrencies.USD,
    /**
     * Optional upfront down payment ("prima") deducted from [amount] to determine the
     * financed principal. Distinct from periodic principal repayment.
     */
    val downPayment: Double = 0.0,
    /** Optional simulation issue timestamp in epoch milliseconds used to project installment due dates. */
    val issuedAtMillis: Long? = null
)

data class AmortizationRow(
    val period: Int,
    /** Remaining principal balance immediately before this installment is applied. */
    val openingBalance: Double = 0.0,
    /** Total financial installment (`principal + interest`), strictly excluding non-financial charges. */
    val payment: Double,
    /** Principal repayment applied toward reducing the loan balance in this period. */
    val principal: Double,
    /** Interest accrued on [openingBalance] during this period. */
    val interest: Double,
    /** Remaining principal balance after applying [principal] (`openingBalance - principal`). */
    val balance: Double,
    /** Optional projected due date in epoch milliseconds when the simulation provides an issue date. */
    val dueDateMillis: Long? = null,
    /**
     * Architectural extension hook for future non-financial charges (e.g., insurance or fees).
     * Always `0.0` in the pure financial engine; kept strictly separate from [payment], [interest],
     * [principal], and [balance].
     */
    val additionalCharges: Double = 0.0,
    /** Total client disbursement (`payment + additionalCharges`). Equals [payment] when no extra charges apply. */
    val totalDisbursement: Double = payment + additionalCharges
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
    /** Gross loan amount requested before deducting [downPayment]. */
    val requestedAmount: Double = 0.0,
    /** Upfront down payment deducted from [requestedAmount]. */
    val downPayment: Double = 0.0,
    /** Net financed principal (`requestedAmount - downPayment`) amortized across [schedule]. */
    val principalAmount: Double = 0.0,
    /** Annual interest rate input expressed in percent (e.g., `36.0` for `36%`). */
    val annualRatePercent: Double = 0.0,
    /** Term quantity entered by the user (interpreted via [termUnit]). */
    val termValue: Int = schedule.size,
    val termUnit: LoanTermUnit = LoanTermUnit.PERIODS,
    /** Optional simulation issue date in epoch milliseconds. */
    val issuedAtMillis: Long? = null,
    /** Effective periodic interest rate as a decimal (e.g., 0.09 = 9% per period). */
    val periodicRate: Double = 0.0,
    /** Equivalent effective annual rate as a decimal (e.g., 0.41158 = 41.158% EAR). */
    val effectiveAnnualRate: Double = 0.0,
    /** Full-precision theoretical periodic installment prior to currency-scale rounding. */
    val theoreticalPeriodicPayment: Double = monthlyPayment,
    val firstPayment: Double = monthlyPayment,
    val lastPayment: Double = schedule.lastOrNull()?.payment ?: monthlyPayment,
    /** Constant principal installment per period for [AmortizationSystem.GERMAN]; null otherwise. */
    val constantPrincipalPerPeriod: Double? = null,
    val finalBalance: Double = schedule.lastOrNull()?.balance ?: 0.0,
    /** Architectural hook for future total non-financial charges; always `0.0` in pure financial mode. */
    val totalAdditionalCharges: Double = 0.0,
    /** Total client disbursement (`totalPayment + totalAdditionalCharges`). */
    val totalDisbursement: Double = totalPayment + totalAdditionalCharges
)

/**
 * Side-by-side comparison of French (fixed installment) and German (declining installment)
 * amortization under identical financed principal, currency, rate, periodicity, and term.
 */
data class LoanComparisonResult(
    val french: LoanResult,
    val german: LoanResult,
    /** `french.totalInterest - german.totalInterest` (positive when German incurs lower total interest). */
    val interestDifference: Double,
    /** `french.totalPayment - german.totalPayment` (matches [interestDifference] for identical principal). */
    val totalPaymentDifference: Double,
    /** `german.firstPayment - french.firstPayment` (positive when German starts with a higher installment). */
    val firstPaymentDifference: Double,
    /** `french.lastPayment - german.lastPayment` (positive when German finishes with a lower installment). */
    val lastPaymentDifference: Double
)
