package com.calcsuits.domain.model.finance

import java.math.BigDecimal
import java.math.RoundingMode

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
    ANNUAL(1);

    fun toCompoundingFrequency(): RateCompoundingFrequency = when (this) {
        MONTHLY -> RateCompoundingFrequency.MONTHLY
        SEMIMONTHLY -> RateCompoundingFrequency.SEMIMONTHLY
        QUARTERLY -> RateCompoundingFrequency.QUARTERLY
        SEMIANNUAL -> RateCompoundingFrequency.SEMIANNUAL
        ANNUAL -> RateCompoundingFrequency.ANNUAL
    }
}

/**
 * Nominal rate compounding frequency (number of capitalization periods per year for TNA).
 * Kept explicitly separate from [PaymentFrequency] so operations whose capitalization schedule
 * differs from their installment schedule (e.g., 24% TNA compounded monthly with quarterly
 * installments) are represented and calculated accurately without conflating both periodicities.
 */
enum class RateCompoundingFrequency(val periodsPerYear: Int) {
    MONTHLY(12),
    SEMIMONTHLY(24),
    QUARTERLY(4),
    SEMIANNUAL(2),
    ANNUAL(1),
    DAILY(365)
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
     * Nominal Annual Rate (TNA / APR nominal) compounded [RateCompoundingFrequency.periodsPerYear]
     * times per year (`c`): `EAR = (1 + TNA / c)^c - 1`, and effective rate per payment period (`p`)
     * is `i_p = TNA / c` when `c == p` or `i_p = (1 + EAR)^(1 / p) - 1` when `c != p`.
     */
    NOMINAL_PER_PAYMENT_PERIOD,

    /**
     * Effective Annual Rate (TEA / EAR) converted to an equivalent periodic rate for payment
     * periodicity `p`: `i_p = (1 + EAR)^(1 / p) - 1`.
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
    /**
     * Optional simulation issue civil date represented canonically as `00:00:00.000 UTC` epoch
     * milliseconds (see `CalculateLoanUseCase.civilDateUtcMillis`). Used to project due dates
     * deterministically independent of device timezone changes.
     */
    val issuedAtMillis: Long? = null,
    /**
     * Optional compounding frequency for [RateConvention.NOMINAL_PER_PAYMENT_PERIOD] (TNA).
     * When `null`, defaults to matching [paymentFrequency] for backwards compatibility.
     * Ignored when [rateConvention] is [RateConvention.EFFECTIVE_ANNUAL] (TEA).
     */
    val compoundingFrequency: RateCompoundingFrequency? = null
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
    /**
     * Optional projected due civil date encoded canonically as `00:00:00.000 UTC` epoch
     * milliseconds when the simulation provides an issue date.
     */
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
    /**
     * Sum of all periodic financial installments (`principalAmount + totalInterest`),
     * strictly excluding any upfront [downPayment].
     */
    val totalPayment: Double,
    val schedule: List<AmortizationRow> = emptyList(),
    val system: AmortizationSystem = AmortizationSystem.FRENCH,
    val paymentFrequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val rateConvention: RateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
    val compoundingFrequency: RateCompoundingFrequency = paymentFrequency.toCompoundingFrequency(),
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
    /** Optional simulation issue civil date encoded as `00:00:00.000 UTC` epoch milliseconds. */
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
    /** Total periodic client disbursement (`totalPayment + totalAdditionalCharges`), excluding upfront [downPayment]. */
    val totalDisbursement: Double = totalPayment + totalAdditionalCharges
) {
    /**
     * Total cash outflow across the full transaction including any upfront [downPayment]
     * plus all periodic installments and additional charges (`downPayment + totalDisbursement`),
     * computed in [BigDecimal] at [currency] scale.
     */
    fun totalOutflowWithDownPayment(): Double =
        BigDecimal.valueOf(downPayment)
            .add(BigDecimal.valueOf(totalDisbursement))
            .setScale(currency.fractionDigits, RoundingMode.HALF_UP)
            .toDouble()
}

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
