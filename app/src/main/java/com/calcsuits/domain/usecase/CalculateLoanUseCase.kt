package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationRow
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.pow

/**
 * Calculates full loan amortization schedules for French, German, and American systems.
 *
 * ## Precision & Rounding Scope
 * Public domain outputs remain [Double] to preserve compatibility across the app, while
 * schedule accounting uses [BigDecimal] internally rounded to [LoanRequest.currency] minor units
 * ([RoundingMode.HALF_UP]) on every installment. This eliminates IEEE-754 floating-point drift
 * over long terms and guarantees exact ledger invariants:
 * - Every row satisfies `payment == principal + interest` at currency precision.
 * - `sum(schedule.principal) == roundedPrincipal` and `schedule.last().balance == 0.0`.
 * - The final installment absorbs any accumulated rounding remainder.
 *
 * ## Rate Convention Assumption
 * Payment periodicity ([PaymentFrequency]) controls installment timing and is explicitly
 * separated from the annual rate basis ([RateConvention]):
 * - [RateConvention.NOMINAL_PER_PAYMENT_PERIOD]: assumes a nominal annual rate (TNA) capitalized
 *   at the payment frequency, yielding `i_p = annualRate / periodsPerYear`.
 * - [RateConvention.EFFECTIVE_ANNUAL]: assumes an effective annual rate (TEA / EAR), yielding
 *   the mathematically equivalent periodic rate `i_p = (1 + annualRate)^(1 / periodsPerYear) - 1`.
 */
class CalculateLoanUseCase @Inject constructor() {

    private val mathContext = MathContext.DECIMAL128

    /**
     * Backwards-compatible overload: French amortization, monthly payments, term in years,
     * nominal annual rate divided by 12, USD currency.
     */
    operator fun invoke(amount: Double, annualRate: Double, years: Int): LoanResult =
        invoke(
            LoanRequest(
                amount = amount,
                annualRate = annualRate,
                termValue = years,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH,
                rateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
                currency = FinanceCurrencies.USD
            )
        )

    operator fun invoke(request: LoanRequest): LoanResult {
        require(request.amount.isFinite() && request.amount > 0.0) {
            "Loan amount must be finite and greater than 0"
        }
        require(request.annualRate.isFinite() && request.annualRate >= 0.0) {
            "Annual interest rate must be finite and non-negative"
        }
        require(request.termValue > 0) {
            "Term value must be greater than 0"
        }

        val periodsPerYear = request.paymentFrequency.periodsPerYear
        val numberOfPaymentsLong = when (request.termUnit) {
            LoanTermUnit.YEARS -> request.termValue.toLong() * periodsPerYear.toLong()
            LoanTermUnit.PERIODS -> request.termValue.toLong()
        }
        require(numberOfPaymentsLong in 1L..MAX_PERIODS.toLong()) {
            "Number of payments must be between 1 and $MAX_PERIODS"
        }
        val numberOfPayments = numberOfPaymentsLong.toInt()

        val scale = request.currency.fractionDigits
        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val principalBd = BigDecimal.valueOf(request.amount).setScale(scale, RoundingMode.HALF_UP)
        require(principalBd > BigDecimal.ZERO) {
            "Loan amount rounds to zero in ${request.currency.code}"
        }

        val (periodicRateBd, effectiveAnnualRate) = resolveRates(
            annualRatePercent = request.annualRate,
            periodsPerYear = periodsPerYear,
            convention = request.rateConvention
        )

        val scheduleResult = when (request.system) {
            AmortizationSystem.FRENCH -> buildFrenchSchedule(
                principal = principalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero
            )
            AmortizationSystem.GERMAN -> buildGermanSchedule(
                principal = principalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero
            )
            AmortizationSystem.AMERICAN -> buildAmericanSchedule(
                principal = principalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero
            )
        }

        val schedule = scheduleResult.rows
        var totalInterestBd = zero
        var totalPaymentBd = zero
        schedule.forEach { row ->
            totalInterestBd = totalInterestBd.add(BigDecimal.valueOf(row.interest))
            totalPaymentBd = totalPaymentBd.add(BigDecimal.valueOf(row.payment))
        }
        totalInterestBd = totalInterestBd.setScale(scale, RoundingMode.HALF_UP)
        totalPaymentBd = totalPaymentBd.setScale(scale, RoundingMode.HALF_UP)

        val firstPayment = schedule.first().payment
        val lastPayment = schedule.last().payment
        val primaryPayment = scheduleResult.primaryPeriodicPayment.toDouble()

        return LoanResult(
            monthlyPayment = primaryPayment,
            totalInterest = totalInterestBd.toDouble(),
            totalPayment = totalPaymentBd.toDouble(),
            schedule = schedule,
            system = request.system,
            paymentFrequency = request.paymentFrequency,
            rateConvention = request.rateConvention,
            currency = request.currency,
            principalAmount = principalBd.toDouble(),
            periodicRate = periodicRateBd.toDouble(),
            effectiveAnnualRate = effectiveAnnualRate,
            firstPayment = firstPayment,
            lastPayment = lastPayment,
            constantPrincipalPerPeriod = scheduleResult.constantPrincipalPerPeriod?.toDouble(),
            finalBalance = schedule.last().balance
        )
    }

    private fun resolveRates(
        annualRatePercent: Double,
        periodsPerYear: Int,
        convention: RateConvention
    ): Pair<BigDecimal, Double> {
        if (annualRatePercent == 0.0) {
            return BigDecimal.ZERO to 0.0
        }
        val annualDecimal = BigDecimal.valueOf(annualRatePercent)
            .divide(BigDecimal(100), mathContext)

        return when (convention) {
            RateConvention.NOMINAL_PER_PAYMENT_PERIOD -> {
                val periodic = annualDecimal.divide(BigDecimal(periodsPerYear), mathContext)
                val ear = (1.0 + periodic.toDouble()).pow(periodsPerYear.toDouble()) - 1.0
                periodic to ear
            }
            RateConvention.EFFECTIVE_ANNUAL -> {
                val annualDouble = annualDecimal.toDouble()
                val periodicDouble = (1.0 + annualDouble).pow(1.0 / periodsPerYear.toDouble()) - 1.0
                require(periodicDouble.isFinite() && periodicDouble >= 0.0) {
                    "Effective periodic rate out of range"
                }
                BigDecimal.valueOf(periodicDouble) to annualDouble
            }
        }
    }

    private fun buildFrenchSchedule(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int,
        zero: BigDecimal
    ): InternalSchedule {
        val isZeroRate = periodicRate.compareTo(BigDecimal.ZERO) == 0
        val scheduledPayment = if (isZeroRate) {
            principal.divide(BigDecimal(periods), scale, RoundingMode.HALF_UP)
        } else {
            val r = periodicRate.toDouble()
            val factor = (1.0 + r).pow(-periods.toDouble())
            val denominator = 1.0 - factor
            require(denominator > 0.0 && denominator.isFinite()) {
                "Rate and term combination out of numerical range"
            }
            val rawPayment = (principal.toDouble() * r) / denominator
            require(rawPayment.isFinite() && rawPayment > 0.0) {
                "Calculated payment out of numerical range"
            }
            BigDecimal.valueOf(rawPayment).setScale(scale, RoundingMode.HALF_UP)
        }

        var balance = principal
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val interest = if (isZeroRate || balance.compareTo(BigDecimal.ZERO) == 0) {
                zero
            } else {
                balance.multiply(periodicRate, mathContext).setScale(scale, RoundingMode.HALF_UP)
            }
            val principalPaid: BigDecimal
            val payment: BigDecimal
            if (period == periods) {
                principalPaid = balance
                payment = principalPaid.add(interest)
            } else {
                val targetPrincipal = scheduledPayment.subtract(interest).coerceAtLeast(zero)
                principalPaid = targetPrincipal.min(balance)
                payment = principalPaid.add(interest)
            }
            balance = balance.subtract(principalPaid).coerceAtLeast(zero)
            rows.add(
                AmortizationRow(
                    period = period,
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble()
                )
            )
        }

        val primaryPayment = if (periods == 1) {
            BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        } else {
            scheduledPayment
        }
        return InternalSchedule(rows = rows, primaryPeriodicPayment = primaryPayment)
    }

    private fun buildGermanSchedule(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int,
        zero: BigDecimal
    ): InternalSchedule {
        val isZeroRate = periodicRate.compareTo(BigDecimal.ZERO) == 0
        val constantPrincipal = principal.divide(BigDecimal(periods), scale, RoundingMode.HALF_UP)

        var balance = principal
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val interest = if (isZeroRate || balance.compareTo(BigDecimal.ZERO) == 0) {
                zero
            } else {
                balance.multiply(periodicRate, mathContext).setScale(scale, RoundingMode.HALF_UP)
            }
            val principalPaid = if (period == periods) {
                balance
            } else {
                constantPrincipal.min(balance)
            }
            val payment = principalPaid.add(interest)
            balance = balance.subtract(principalPaid).coerceAtLeast(zero)
            rows.add(
                AmortizationRow(
                    period = period,
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble()
                )
            )
        }

        val firstPayment = BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = firstPayment,
            constantPrincipalPerPeriod = constantPrincipal
        )
    }

    private fun buildAmericanSchedule(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int,
        zero: BigDecimal
    ): InternalSchedule {
        val isZeroRate = periodicRate.compareTo(BigDecimal.ZERO) == 0
        val periodicInterest = if (isZeroRate) {
            zero
        } else {
            principal.multiply(periodicRate, mathContext).setScale(scale, RoundingMode.HALF_UP)
        }

        var balance = principal
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val isMaturity = period == periods
            val interest = periodicInterest
            val principalPaid = if (isMaturity) balance else zero
            val payment = principalPaid.add(interest)
            balance = if (isMaturity) zero else balance
            rows.add(
                AmortizationRow(
                    period = period,
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble()
                )
            )
        }

        val primaryPayment = if (periods == 1) {
            BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        } else {
            periodicInterest
        }
        return InternalSchedule(rows = rows, primaryPeriodicPayment = primaryPayment)
    }

    private data class InternalSchedule(
        val rows: List<AmortizationRow>,
        val primaryPeriodicPayment: BigDecimal,
        val constantPrincipalPerPeriod: BigDecimal? = null
    )

    companion object {
        const val MAX_PERIODS = 2_400
    }
}
