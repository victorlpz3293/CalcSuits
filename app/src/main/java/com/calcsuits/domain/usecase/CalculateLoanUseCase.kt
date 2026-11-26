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
import java.util.GregorianCalendar
import java.util.TimeZone
import javax.inject.Inject

/**
 * Calculates full loan amortization schedules using [BigDecimal] internal accounting.
 *
 * ## Supported Systems
 * - [AmortizationSystem.FRENCH]: Fixed periodic installment, decreasing interest, increasing principal repayment.
 * - [AmortizationSystem.GERMAN]: Constant principal repayment per period, decreasing interest, declining installments.
 * - [AmortizationSystem.AMERICAN]: Retained in the domain engine solely for backwards compatibility.
 *
 * ## Precision & Rounding Policy
 * All internal schedule calculations, totals, and comparative differences use [BigDecimal] with
 * [MathContext.DECIMAL128] and [RoundingMode.HALF_UP] at the selected [LoanRequest.currency]
 * minor-unit scale on each period. Conversion to `Double` occurs strictly at the public DTO
 * boundary ([AmortizationRow], [LoanResult], [LoanComparisonResult]).
 * This prevents IEEE-754 drift and enforces strict ledger invariants on every schedule:
 * - Every row satisfies `payment == principal + interest` at currency precision.
 * - Every row satisfies `openingBalance - principal == balance` and `principal <= openingBalance`.
 * - `sum(schedule.principal) == financedPrincipal` and `schedule.last().balance == 0.0`.
 * - The final installment absorbs any accumulated rounding remainder.
 *
 * ## Civil Date Convention (API 24+ compatible, timezone-independent)
 * Because CalcSuits targets `minSdk = 24` without core library desugaring, civil calendar dates
 * (`issuedAtMillis`, `dueDateMillis`) are represented canonically as **`00:00:00.000 UTC`** via
 * [GregorianCalendar] configured with [UTC_TIME_ZONE]. Callers capturing "today" from the device
 * clock should convert the local civil `(year, month, day)` through [todayCivilDateUtcMillis] so
 * due-date projection and UI formatting remain completely invariant to timezone or DST shifts.
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
 * - [RateConvention.EFFECTIVE_ANNUAL]: effective annual rate (TEA / EAR) converted in
 *   [BigDecimal] ([MathContext.DECIMAL128]) to its mathematically equivalent periodic rate
 *   `i_p = (1 + annualRate)^(1 / periodsPerYear) - 1`.
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

    operator fun invoke(request: LoanRequest): LoanResult =
        computeInternal(request).result

    /**
     * Compares French (fixed installment) and German (declining installment) systems
     * under identical loan parameters, computing all monetary differences directly from
     * internal [BigDecimal] accumulators at currency scale.
     */
    fun compare(request: LoanRequest): LoanComparisonResult {
        val french = computeInternal(request.copy(system = AmortizationSystem.FRENCH))
        val german = computeInternal(request.copy(system = AmortizationSystem.GERMAN))
        val scale = request.currency.fractionDigits

        fun diff(a: BigDecimal, b: BigDecimal): Double =
            a.subtract(b).setScale(scale, RoundingMode.HALF_UP).toDouble()

        return LoanComparisonResult(
            french = french.result,
            german = german.result,
            interestDifference = diff(french.totalInterestBd, german.totalInterestBd),
            totalPaymentDifference = diff(french.totalPaymentBd, german.totalPaymentBd),
            firstPaymentDifference = diff(german.firstPaymentBd, french.firstPaymentBd),
            lastPaymentDifference = diff(french.lastPaymentBd, german.lastPaymentBd)
        )
    }

    private fun computeInternal(request: LoanRequest): ComputedLoan {
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

        val normalizedIssuedAtMillis = request.issuedAtMillis?.let { normalizeCivilDateUtcMillis(it) }

        val (periodicRateBd, effectiveAnnualRateBd) = resolveRates(
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
                issuedAtMillis = normalizedIssuedAtMillis,
                frequency = request.paymentFrequency
            )
            AmortizationSystem.GERMAN -> buildGermanSchedule(
                principal = financedPrincipalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero,
                issuedAtMillis = normalizedIssuedAtMillis,
                frequency = request.paymentFrequency
            )
            AmortizationSystem.AMERICAN -> buildAmericanSchedule(
                principal = financedPrincipalBd,
                periodicRate = periodicRateBd,
                periods = numberOfPayments,
                scale = scale,
                zero = zero,
                issuedAtMillis = normalizedIssuedAtMillis,
                frequency = request.paymentFrequency
            )
        }

        val totalInterestBd = scheduleResult.totalInterest.setScale(scale, RoundingMode.HALF_UP)
        val totalPaymentBd = scheduleResult.totalPayment.setScale(scale, RoundingMode.HALF_UP)
        val firstPaymentBd = scheduleResult.firstPayment.setScale(scale, RoundingMode.HALF_UP)
        val lastPaymentBd = scheduleResult.lastPayment.setScale(scale, RoundingMode.HALF_UP)
        val finalBalanceBd = scheduleResult.finalBalance.setScale(scale, RoundingMode.HALF_UP)

        val loanResult = LoanResult(
            monthlyPayment = scheduleResult.primaryPeriodicPayment.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            totalPayment = totalPaymentBd.toDouble(),
            schedule = scheduleResult.rows,
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
            issuedAtMillis = normalizedIssuedAtMillis,
            periodicRate = periodicRateBd.toDouble(),
            effectiveAnnualRate = effectiveAnnualRateBd.toDouble(),
            theoreticalPeriodicPayment = scheduleResult.theoreticalPeriodicPayment.toDouble(),
            firstPayment = firstPaymentBd.toDouble(),
            lastPayment = lastPaymentBd.toDouble(),
            constantPrincipalPerPeriod = scheduleResult.constantPrincipalPerPeriod?.toDouble(),
            finalBalance = finalBalanceBd.toDouble(),
            totalAdditionalCharges = 0.0,
            totalDisbursement = totalPaymentBd.toDouble()
        )

        return ComputedLoan(
            result = loanResult,
            totalInterestBd = totalInterestBd,
            totalPaymentBd = totalPaymentBd,
            firstPaymentBd = firstPaymentBd,
            lastPaymentBd = lastPaymentBd
        )
    }

    internal fun computeEffectivePeriodicRate(
        annualRatePercent: Double,
        periodsPerYear: Int
    ): BigDecimal {
        require(annualRatePercent.isFinite() && annualRatePercent >= 0.0) {
            "Annual interest rate must be finite and non-negative"
        }
        require(periodsPerYear >= 1) {
            "Periods per year must be >= 1"
        }
        if (annualRatePercent == 0.0) return BigDecimal.ZERO
        val annualDecimal = BigDecimal.valueOf(annualRatePercent)
            .divide(BigDecimal(100), mathContext)
        if (periodsPerYear == 1) return annualDecimal
        val base = BigDecimal.ONE.add(annualDecimal, mathContext)
        val root = nthRootBigDecimal(base, periodsPerYear)
        val periodic = root.subtract(BigDecimal.ONE, mathContext)
        require(periodic >= BigDecimal.ZERO) {
            "Effective periodic rate out of range"
        }
        return periodic
    }

    private fun resolveRates(
        annualRatePercent: Double,
        periodsPerYear: Int,
        convention: RateConvention
    ): Pair<BigDecimal, BigDecimal> {
        if (annualRatePercent == 0.0) {
            return BigDecimal.ZERO to BigDecimal.ZERO
        }
        val annualDecimal = BigDecimal.valueOf(annualRatePercent)
            .divide(BigDecimal(100), mathContext)

        return when (convention) {
            RateConvention.NOMINAL_PER_PAYMENT_PERIOD -> {
                val periodic = annualDecimal.divide(BigDecimal(periodsPerYear), mathContext)
                val earBd = BigDecimal.ONE.add(periodic, mathContext)
                    .pow(periodsPerYear, mathContext)
                    .subtract(BigDecimal.ONE, mathContext)
                periodic to earBd
            }
            RateConvention.EFFECTIVE_ANNUAL -> {
                val periodic = computeEffectivePeriodicRate(annualRatePercent, periodsPerYear)
                periodic to annualDecimal
            }
        }
    }

    /**
     * Produces a pure-[BigDecimal] upper-bound initial seed `x_0 >= value^(1 / degree)` without
     * any `Double` or `Math.pow` dependency. Because `f(x) = x^degree - value` is strictly convex
     * on `(0, +inf)` for `degree >= 2`, starting at or above the true root guarantees monotone
     * quadratic Newton-Raphson convergence from above across arbitrary decimal magnitudes.
     */
    internal fun initialNthRootSeed(value: BigDecimal, degree: Int): BigDecimal {
        require(degree >= 1) { "Root degree must be >= 1" }
        require(value > BigDecimal.ZERO) { "Root operand must be strictly positive" }
        if (degree == 1 || value.compareTo(BigDecimal.ONE) == 0) return value

        val base10Exponent = value.precision() - value.scale() - 1
        return when {
            // For 1 < value < 10 (0% < TEA < 900%), Bernoulli's inequality guarantees
            // (1 + (value - 1) / m)^m >= value, yielding a tight upper bound.
            value > BigDecimal.ONE && base10Exponent <= 0 -> {
                val delta = value.subtract(BigDecimal.ONE, mathContext)
                BigDecimal.ONE.add(delta.divide(BigDecimal(degree), mathContext), mathContext)
            }
            // For value >= 10, value < 10^(e + 1), so 10^ceil((e + 1) / m) > value^(1 / m).
            value >= BigDecimal.TEN -> {
                val shift = (base10Exponent + degree) / degree
                BigDecimal.ONE.movePointRight(shift)
            }
            // For 0 < value < 1, value < 10^(e + 1) <= 1, so 10^(-floor((-e - 1) / m)) > value^(1 / m).
            else -> {
                val shift = (-base10Exponent - 1) / degree
                BigDecimal.ONE.movePointLeft(shift)
            }
        }
    }

    /**
     * Computes `value^(1 / degree)` purely in [BigDecimal] ([MathContext.DECIMAL128])
     * using Newton-Raphson iteration: `x_{k+1} = ((m - 1) * x_k + value / x_k^(m - 1)) / m`.
     *
     * Verifies both step convergence (`|x_{k+1} - x_k| <= tolerance * |x_{k+1}|`) and an
     * independent residual bound (`|x_{k+1}^m - value|`) before returning. If convergence is not
     * achieved within [maxIterations], throws [ArithmeticException] rather than silently returning
     * an unconverged iterate.
     */
    internal fun nthRootBigDecimal(
        value: BigDecimal,
        degree: Int,
        maxIterations: Int = MAX_NEWTON_ITERATIONS,
        tolerance: BigDecimal = NEWTON_CONVERGENCE_TOLERANCE
    ): BigDecimal {
        require(degree >= 1) { "Root degree must be >= 1" }
        require(value > BigDecimal.ZERO) { "Root operand must be strictly positive" }
        require(maxIterations >= 1) { "Max Newton iterations must be >= 1" }
        require(tolerance > BigDecimal.ZERO) { "Convergence tolerance must be strictly positive" }
        if (degree == 1 || value.compareTo(BigDecimal.ONE) == 0) return value

        val m = BigDecimal(degree)
        val mMinus1 = BigDecimal(degree - 1)
        var current = initialNthRootSeed(value, degree)
        val residualScaleFactor = m.multiply(RESIDUAL_SAFETY_MULTIPLIER, mathContext)

        for (iteration in 1..maxIterations) {
            val prevPow = current.pow(degree - 1, mathContext)
            val numerator = mMinus1.multiply(current, mathContext)
                .add(value.divide(prevPow, mathContext), mathContext)
            val next = numerator.divide(m, mathContext)

            val stepDelta = next.subtract(current, mathContext).abs()
            val scaledTolerance = tolerance.multiply(next.abs(), mathContext)
            if (stepDelta <= scaledTolerance || next.compareTo(current) == 0) {
                val residual = next.pow(degree, mathContext).subtract(value, mathContext).abs()
                val residualLimit = tolerance
                    .multiply(residualScaleFactor, mathContext)
                    .multiply(value.abs(), mathContext)
                if (residual <= residualLimit) {
                    return next
                }
            }
            current = next
        }
        throw ArithmeticException(
            "Newton-Raphson nth-root did not converge within $maxIterations iterations " +
                "(degree=$degree, tolerance=$tolerance)"
        )
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
        var totalInterest = zero
        var totalPayment = zero
        var firstPayment = zero
        var lastPayment = zero
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
            totalInterest = totalInterest.add(interest)
            totalPayment = totalPayment.add(payment)
            if (period == 1) firstPayment = payment
            if (period == periods) lastPayment = payment

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

        val primaryPayment = if (periods == 1) firstPayment else scheduledPayment
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = primaryPayment,
            theoreticalPeriodicPayment = theoreticalPayment,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            firstPayment = firstPayment,
            lastPayment = lastPayment,
            finalBalance = balance
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
        var totalInterest = zero
        var totalPayment = zero
        var firstPayment = zero
        var lastPayment = zero
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
            totalInterest = totalInterest.add(interest)
            totalPayment = totalPayment.add(payment)
            if (period == 1) firstPayment = payment
            if (period == periods) lastPayment = payment

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

        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = firstPayment,
            theoreticalPeriodicPayment = firstPayment,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            firstPayment = firstPayment,
            lastPayment = lastPayment,
            finalBalance = balance,
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
        var totalInterest = zero
        var totalPayment = zero
        var firstPayment = zero
        var lastPayment = zero
        val rows = ArrayList<AmortizationRow>(periods)
        for (period in 1..periods) {
            val openingBalance = balance
            val isMaturity = period == periods
            val interest = periodicInterest
            val principalPaid = if (isMaturity) openingBalance else zero
            val payment = principalPaid.add(interest)
            balance = if (isMaturity) zero else openingBalance
            totalInterest = totalInterest.add(interest)
            totalPayment = totalPayment.add(payment)
            if (period == 1) firstPayment = payment
            if (period == periods) lastPayment = payment

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

        val primaryPayment = if (periods == 1) firstPayment else periodicInterest
        return InternalSchedule(
            rows = rows,
            primaryPeriodicPayment = primaryPayment,
            theoreticalPeriodicPayment = primaryPayment,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            firstPayment = firstPayment,
            lastPayment = lastPayment,
            finalBalance = balance
        )
    }

    private fun computeDueDateMillis(
        issuedAtMillis: Long?,
        period: Int,
        frequency: PaymentFrequency
    ): Long? {
        val base = issuedAtMillis ?: return null
        val calendar = GregorianCalendar(UTC_TIME_ZONE).apply {
            timeInMillis = base
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
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
        val totalInterest: BigDecimal,
        val totalPayment: BigDecimal,
        val firstPayment: BigDecimal,
        val lastPayment: BigDecimal,
        val finalBalance: BigDecimal,
        val constantPrincipalPerPeriod: BigDecimal? = null
    )

    private data class ComputedLoan(
        val result: LoanResult,
        val totalInterestBd: BigDecimal,
        val totalPaymentBd: BigDecimal,
        val firstPaymentBd: BigDecimal,
        val lastPaymentBd: BigDecimal
    )

    companion object {
        const val MAX_PERIODS = 2_400
        internal const val MAX_NEWTON_ITERATIONS = 128
        internal val NEWTON_CONVERGENCE_TOLERANCE = BigDecimal("1E-31")
        private val RESIDUAL_SAFETY_MULTIPLIER = BigDecimal(100)

        /** Fixed UTC timezone used for all canonical civil-date representations (`00:00:00.000 UTC`). */
        val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

        /**
         * Encodes a civil calendar date `(year, monthZeroBased, dayOfMonth)` as canonical
         * `00:00:00.000 UTC` epoch milliseconds.
         */
        fun civilDateUtcMillis(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long =
            GregorianCalendar(UTC_TIME_ZONE).apply {
                clear()
                set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        /**
         * Captures today's local civil date from [localCalendar] and encodes it into canonical
         * `00:00:00.000 UTC` epoch milliseconds, eliminating device-timezone drift on subsequent
         * due-date projections and UI date formatting.
         */
        fun todayCivilDateUtcMillis(localCalendar: Calendar = Calendar.getInstance()): Long =
            civilDateUtcMillis(
                year = localCalendar.get(Calendar.YEAR),
                monthZeroBased = localCalendar.get(Calendar.MONTH),
                dayOfMonth = localCalendar.get(Calendar.DAY_OF_MONTH)
            )

        /**
         * Normalizes an epoch millisecond value interpreted in UTC to its canonical
         * `00:00:00.000 UTC` civil date boundary.
         */
        fun normalizeCivilDateUtcMillis(epochMillis: Long): Long {
            val utc = GregorianCalendar(UTC_TIME_ZONE).apply { timeInMillis = epochMillis }
            return civilDateUtcMillis(
                year = utc.get(Calendar.YEAR),
                monthZeroBased = utc.get(Calendar.MONTH),
                dayOfMonth = utc.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
