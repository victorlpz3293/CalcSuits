package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationRow
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.LoanComparisonResult
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.pow

/**
 * Calculates full loan amortization schedules using [BigDecimal] internal accounting.
 *
 * ## Supported Systems
 * - [AmortizationSystem.FRENCH]: Fixed periodic installment, decreasing interest, increasing principal repayment.
 * - [AmortizationSystem.GERMAN]: Constant principal repayment per period, decreasing interest, declining installments.
 * - [AmortizationSystem.AMERICAN]: Retained in the domain engine solely for backwards compatibility.
 *
 * ## Precision & Rounding Policy
 * All internal schedule calculations use [BigDecimal] with [MathContext.DECIMAL128] and
 * [RoundingMode.HALF_UP] at the selected [LoanRequest.currency] minor-unit scale on each period.
 * This prevents IEEE-754 drift and enforces strict ledger invariants on every schedule:
 * - Every row satisfies `payment == principal + interest` at currency precision.
 * - Every row satisfies `openingBalance - principal == balance` and `principal <= openingBalance`.
 * - `sum(schedule.principal) == financedPrincipal` and `schedule.last().balance == 0.0`.
 * - The final installment absorbs any accumulated rounding remainder.
 *
 * ## Down Payment ("Prima")
 * When [LoanRequest.downPayment] is provided (`0 <= downPayment < amount`), it is deducted upfront
 * from [LoanRequest.amount] to compute the net financed principal (`financedPrincipal = amount - downPayment`).
 * Periodic amortization operates exclusively on `financedPrincipal`.
 *
 * ## Rate Convention Assumption
 * Payment periodicity ([PaymentFrequency]) controls installment timing and is explicitly
 * separated from the annual rate basis ([RateConvention]):
 * - [RateConvention.NOMINAL_PER_PAYMENT_PERIOD]: nominal annual rate (TNA) capitalized at payment
 *   periodicity, yielding `i_p = annualRate / periodsPerYear`.
 * - [RateConvention.EFFECTIVE_ANNUAL]: effective annual rate (TEA / EAR) converted to its
 *   mathematically equivalent periodic rate `i_p = (1 + annualRate)^(1 / periodsPerYear) - 1`.
 */
class CalculateLoanUseCase @Inject constructor() {

    private val mathContext = MathContext.DECIMAL128

    /**
     * Backwards-compatible overload: French amortization, monthly payments, term in years,
     * nominal annual rate divided by 12, USD currency, no down payment.
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
        require(request.downPayment.isFinite() && request.downPayment >= 0.0) {
            "Down payment must be finite and non-negative"
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
        val requestedBd = BigDecimal.valueOf(request.amount).setScale(scale, RoundingMode.HALF_UP)
        val downPaymentBd = BigDecimal.valueOf(request.downPayment).setScale(scale, RoundingMode.HALF_UP)
        require(downPaymentBd < requestedBd) {
            "Down payment must be strictly less than loan amount"
        }
        val financedPrincipalBd = requestedBd.subtract(downPaymentBd)
        require(financedPrincipalBd > BigDecimal.ZERO) {
            "Financed principal rounds to zero in ${request.currency.code}"
        }

        val (periodicRateBd, effectiveAnnualRate) = resolveRates(
            annualRatePercent = request.annualRate,
            periodsPerYear = periodsPerYear,
            convention = request.rateConvention
        )

        val scheduleResult = when (request.system) {
            AmortizationSystem.FRENCH -> buildFrenchSchedule(
                principal = financedPrincipalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero,
                issuedAtMillis = request.issuedAtMillis,
                frequency = request.paymentFrequency
            )
            AmortizationSystem.GERMAN -> buildGermanSchedule(
                principal = financedPrincipalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero,
                issuedAtMillis = request.issuedAtMillis,
                frequency = request.paymentFrequency
            )
            AmortizationSystem.AMERICAN -> buildAmericanSchedule(
                principal = financedPrincipalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero,
                issuedAtMillis = request.issuedAtMillis,
                frequency = request.paymentFrequency
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
            requestedAmount = requestedBd.toDouble(),
            downPayment = downPaymentBd.toDouble(),
            principalAmount = financedPrincipalBd.toDouble(),
            annualRatePercent = request.annualRate,
            termValue = request.termValue,
            termUnit = request.termUnit,
            issuedAtMillis = request.issuedAtMillis,
            periodicRate = periodicRateBd.toDouble(),
            effectiveAnnualRate = effectiveAnnualRate,
            theoreticalPeriodicPayment = scheduleResult.theoreticalPeriodicPayment.toDouble(),
            firstPayment = firstPayment,
            lastPayment = lastPayment,
            constantPrincipalPerPeriod = scheduleResult.constantPrincipalPerPeriod?.toDouble(),
            finalBalance = schedule.last().balance,
            totalAdditionalCharges = 0.0,
            totalDisbursement = totalPaymentBd.toDouble()
        )
    }

    /**
     * Compares French (fixed installment) and German (declining installment) systems
     * under identical loan parameters, computing all monetary differences at currency scale.
     */
    fun compare(request: LoanRequest): LoanComparisonResult {
        val french = invoke(request.copy(system = AmortizationSystem.FRENCH))
        val german = invoke(request.copy(system = AmortizationSystem.GERMAN))
        val scale = request.currency.fractionDigits

        fun diff(a: Double, b: Double): Double =
            BigDecimal.valueOf(a)
                .subtract(BigDecimal.valueOf(b))
                .setScale(scale, RoundingMode.HALF_UP)
                .toDouble()

        return LoanComparisonResult(
            french = french,
            german = german,
            interestDifference = diff(french.totalInterest, german.totalInterest),
            totalPaymentDifference = diff(french.totalPayment, german.totalPayment),
            firstPaymentDifference = diff(german.firstPayment, french.firstPayment),
            lastPaymentDifference = diff(french.lastPayment, german.lastPayment)
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
                val earBd = BigDecimal.ONE.add(periodic, mathContext)
                    .pow(periodsPerYear, mathContext)
                    .subtract(BigDecimal.ONE, mathContext)
                periodic to earBd.toDouble()
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
        zero: BigDecimal,
        issuedAtMillis: Long?,
        frequency: PaymentFrequency
    ): InternalSchedule {
        val isZeroRate = periodicRate.compareTo(BigDecimal.ZERO) == 0
        val theoreticalPayment: BigDecimal
        val scheduledPayment: BigDecimal
        if (isZeroRate) {
            theoreticalPayment = principal.divide(BigDecimal(periods), mathContext)
            scheduledPayment = theoreticalPayment.setScale(scale, RoundingMode.HALF_UP)
        } else {
            val onePlusI = BigDecimal.ONE.add(periodicRate, mathContext)
            val powN = try {
                onePlusI.pow(periods, mathContext)
            } catch (e: ArithmeticException) {
                throw IllegalArgumentException("Rate and term combination out of numerical range", e)
            }
            val denominator = powN.subtract(BigDecimal.ONE, mathContext)
            require(denominator > BigDecimal.ZERO) {
                "Rate and term combination out of numerical range"
            }
            val numerator = principal.multiply(periodicRate, mathContext).multiply(powN, mathContext)
            theoreticalPayment = numerator.divide(denominator, mathContext)
            require(theoreticalPayment > BigDecimal.ZERO) {
                "Calculated payment out of numerical range"
            }
            scheduledPayment = theoreticalPayment.setScale(scale, RoundingMode.HALF_UP)
        }

        var balance = principal
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val openingBalance = balance
            val interest = if (isZeroRate || openingBalance.compareTo(BigDecimal.ZERO) == 0) {
                zero
            } else {
                openingBalance.multiply(periodicRate, mathContext).setScale(scale, RoundingMode.HALF_UP)
            }
            val principalPaid: BigDecimal
            val payment: BigDecimal
            if (period == periods) {
                principalPaid = openingBalance
                payment = principalPaid.add(interest)
            } else {
                val targetPrincipal = scheduledPayment.subtract(interest).coerceAtLeast(zero)
                principalPaid = targetPrincipal.min(openingBalance)
                payment = principalPaid.add(interest)
            }
            balance = openingBalance.subtract(principalPaid).coerceAtLeast(zero)
            rows.add(
                AmortizationRow(
                    period = period,
                    openingBalance = openingBalance.toDouble(),
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble(),
                    dueDateMillis = computeDueDateMillis(issuedAtMillis, period, frequency)
                )
            )
        }

        val primaryPayment = if (periods == 1) {
            BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        } else {
            scheduledPayment
        }
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = primaryPayment,
            theoreticalPeriodicPayment = theoreticalPayment
        )
    }

    private fun buildGermanSchedule(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int,
        zero: BigDecimal,
        issuedAtMillis: Long?,
        frequency: PaymentFrequency
    ): InternalSchedule {
        val isZeroRate = periodicRate.compareTo(BigDecimal.ZERO) == 0
        val constantPrincipal = principal.divide(BigDecimal(periods), scale, RoundingMode.HALF_UP)

        var balance = principal
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val openingBalance = balance
            val interest = if (isZeroRate || openingBalance.compareTo(BigDecimal.ZERO) == 0) {
                zero
            } else {
                openingBalance.multiply(periodicRate, mathContext).setScale(scale, RoundingMode.HALF_UP)
            }
            val principalPaid = if (period == periods) {
                openingBalance
            } else {
                constantPrincipal.min(openingBalance)
            }
            val payment = principalPaid.add(interest)
            balance = openingBalance.subtract(principalPaid).coerceAtLeast(zero)
            rows.add(
                AmortizationRow(
                    period = period,
                    openingBalance = openingBalance.toDouble(),
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble(),
                    dueDateMillis = computeDueDateMillis(issuedAtMillis, period, frequency)
                )
            )
        }

        val firstPayment = BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = firstPayment,
            theoreticalPeriodicPayment = firstPayment,
            constantPrincipalPerPeriod = constantPrincipal
        )
    }

    private fun buildAmericanSchedule(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int,
        zero: BigDecimal,
        issuedAtMillis: Long?,
        frequency: PaymentFrequency
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
            val openingBalance = balance
            val isMaturity = period == periods
            val interest = periodicInterest
            val principalPaid = if (isMaturity) openingBalance else zero
            val payment = principalPaid.add(interest)
            balance = if (isMaturity) zero else openingBalance
            rows.add(
                AmortizationRow(
                    period = period,
                    openingBalance = openingBalance.toDouble(),
                    payment = payment.toDouble(),
                    principal = principalPaid.toDouble(),
                    interest = interest.toDouble(),
                    balance = balance.toDouble(),
                    dueDateMillis = computeDueDateMillis(issuedAtMillis, period, frequency)
                )
            )
        }

        val primaryPayment = if (periods == 1) {
            BigDecimal.valueOf(rows.first().payment).setScale(scale, RoundingMode.HALF_UP)
        } else {
            periodicInterest
        }
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = primaryPayment,
            theoreticalPeriodicPayment = primaryPayment
        )
    }

    private fun computeDueDateMillis(
        issuedAtMillis: Long?,
        period: Int,
        frequency: PaymentFrequency
    ): Long? {
        val base = issuedAtMillis ?: return null
        val calendar = Calendar.getInstance().apply { timeInMillis = base }
        when (frequency) {
            PaymentFrequency.MONTHLY -> calendar.add(Calendar.MONTH, period)
            PaymentFrequency.SEMIMONTHLY -> calendar.add(Calendar.DAY_OF_YEAR, period * 15)
            PaymentFrequency.QUARTERLY -> calendar.add(Calendar.MONTH, period * 3)
            PaymentFrequency.SEMIANNUAL -> calendar.add(Calendar.MONTH, period * 6)
            PaymentFrequency.ANNUAL -> calendar.add(Calendar.YEAR, period)
        }
        return calendar.timeInMillis
    }

    private data class InternalSchedule(
        val rows: List<AmortizationRow>,
        val primaryPeriodicPayment: BigDecimal,
        val theoreticalPeriodicPayment: BigDecimal,
        val constantPrincipalPerPeriod: BigDecimal? = null
    )

    companion object {
        const val MAX_PERIODS = 2_400
    }
}
