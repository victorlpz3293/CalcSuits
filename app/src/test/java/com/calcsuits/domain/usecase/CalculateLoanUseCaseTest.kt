package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.ui.util.formatCompactMoneyAxis
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatShortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow

class CalculateLoanUseCaseTest {

    private lateinit var useCase: CalculateLoanUseCase

    @Before
    fun setUp() {
        useCase = CalculateLoanUseCase()
    }

    /**
     * Acceptance Case 1 (INATEC 2013 Reference — German Amortization):
     * Capital = 600,000 NIO, TNA = 36%, Quarterly (m = 4, i = 9%), Term = 2 years (n = 8).
     */
    @Test
    fun inatecReferenceGermanQuarterlyLoanMatchesExactScheduleAndTotals() {
        val result = useCase(
            LoanRequest(
                amount = 600_000.0,
                annualRate = 36.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.QUARTERLY,
                system = AmortizationSystem.GERMAN,
                rateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
                currency = FinanceCurrencies.NIO
            )
        )

        assertEquals(AmortizationSystem.GERMAN, result.system)
        assertEquals(FinanceCurrencies.NIO, result.currency)
        assertEquals(0.09, result.periodicRate, 1e-12)
        assertEquals(8, result.schedule.size)
        assertEquals(75_000.0, result.constantPrincipalPerPeriod!!, 1e-6)
        assertEquals(243_000.0, result.totalInterest, 1e-6)
        assertEquals(600_000.0, result.principalAmount, 1e-6)
        assertEquals(843_000.0, result.totalPayment, 1e-6)
        assertEquals(0.0, result.finalBalance, 1e-9)

        data class ExpectedRow(
            val period: Int,
            val opening: Double,
            val interest: Double,
            val principal: Double,
            val payment: Double,
            val closing: Double
        )

        val expectedRows = listOf(
            ExpectedRow(1, 600_000.0, 54_000.0, 75_000.0, 129_000.0, 525_000.0),
            ExpectedRow(2, 525_000.0, 47_250.0, 75_000.0, 122_250.0, 450_000.0),
            ExpectedRow(3, 450_000.0, 40_500.0, 75_000.0, 115_500.0, 375_000.0),
            ExpectedRow(4, 375_000.0, 33_750.0, 75_000.0, 108_750.0, 300_000.0),
            ExpectedRow(5, 300_000.0, 27_000.0, 75_000.0, 102_000.0, 225_000.0),
            ExpectedRow(6, 225_000.0, 20_250.0, 75_000.0, 95_250.0, 150_000.0),
            ExpectedRow(7, 150_000.0, 13_500.0, 75_000.0, 88_500.0, 75_000.0),
            ExpectedRow(8, 75_000.0, 6_750.0, 75_000.0, 81_750.0, 0.0)
        )

        expectedRows.forEachIndexed { idx, expected ->
            val row = result.schedule[idx]
            assertEquals(expected.period, row.period)
            assertEquals(expected.opening, row.openingBalance, 1e-6)
            assertEquals(expected.interest, row.interest, 1e-6)
            assertEquals(expected.principal, row.principal, 1e-6)
            assertEquals(expected.payment, row.payment, 1e-6)
            assertEquals(expected.closing, row.balance, 1e-6)
        }

        assertLedgerInvariants(result, expectedPrincipal = 600_000.0, scale = 2)
    }

    /**
     * Acceptance Case 2 (INATEC 2013 Reference — French Amortization):
     * Capital = 600,000 NIO, TNA = 36%, Quarterly (m = 4, i = 9%), Term = 2 years (n = 8).
     */
    @Test
    fun inatecReferenceFrenchQuarterlyLoanMatchesTheoreticalAnnuityAndExactClosure() {
        val result = useCase(
            LoanRequest(
                amount = 600_000.0,
                annualRate = 36.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.QUARTERLY,
                system = AmortizationSystem.FRENCH,
                rateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
                currency = FinanceCurrencies.NIO
            )
        )

        assertEquals(AmortizationSystem.FRENCH, result.system)
        assertEquals(0.09, result.periodicRate, 1e-12)
        assertEquals(8, result.schedule.size)
        // Theoretical high-precision constant payment ≈ 108,404.6267 -> presented 108,404.63
        assertEquals(108_404.6267, result.theoreticalPeriodicPayment, 0.0005)
        assertEquals(108_404.63, result.monthlyPayment, 1e-6)
        assertEquals(108_404.63, result.firstPayment, 1e-6)
        assertEquals(600_000.0, result.principalAmount, 1e-6)
        assertEquals(267_237.01, result.totalInterest, 0.02)
        assertEquals(867_237.01, result.totalPayment, 0.02)
        assertEquals(0.0, result.finalBalance, 1e-9)

        // Strictly decreasing interest and increasing principal across all 8 installments
        for (i in 1 until result.schedule.size) {
            assertTrue(result.schedule[i].interest < result.schedule[i - 1].interest)
            assertTrue(result.schedule[i].principal > result.schedule[i - 1].principal)
        }

        assertLedgerInvariants(result, expectedPrincipal = 600_000.0, scale = 2)
    }

    @Test
    fun compareSystemsEvaluatesFrenchAndGermanUnderIdenticalParametersAndComputesExactDifferences() {
        val request = LoanRequest(
            amount = 600_000.0,
            annualRate = 36.0,
            termValue = 2,
            termUnit = LoanTermUnit.YEARS,
            paymentFrequency = PaymentFrequency.QUARTERLY,
            rateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD,
            currency = FinanceCurrencies.NIO
        )
        val comparison = useCase.compare(request)

        assertEquals(AmortizationSystem.FRENCH, comparison.french.system)
        assertEquals(AmortizationSystem.GERMAN, comparison.german.system)
        assertEquals(comparison.french.principalAmount, comparison.german.principalAmount, 1e-9)
        assertEquals(comparison.french.schedule.size, comparison.german.schedule.size)

        // German pays less total interest than French, starts higher, finishes lower
        assertTrue(comparison.interestDifference > 0.0)
        assertEquals(
            comparison.french.totalInterest - comparison.german.totalInterest,
            comparison.interestDifference,
            1e-6
        )
        assertEquals(comparison.interestDifference, comparison.totalPaymentDifference, 1e-6)
        assertEquals(
            comparison.german.firstPayment - comparison.french.firstPayment,
            comparison.firstPaymentDifference,
            1e-6
        )
        assertEquals(
            comparison.french.lastPayment - comparison.german.lastPayment,
            comparison.lastPaymentDifference,
            1e-6
        )
        assertTrue(comparison.firstPaymentDifference > 0.0)
        assertTrue(comparison.lastPaymentDifference > 0.0)
    }

    @Test
    fun downPaymentIsDeductedFromRequestedAmountToComputeFinancedPrincipal() {
        val withDownPayment = useCase(
            LoanRequest(
                amount = 100_000.0,
                downPayment = 20_000.0,
                annualRate = 12.0,
                termValue = 24,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH,
                currency = FinanceCurrencies.USD
            )
        )
        val directPrincipal = useCase(
            LoanRequest(
                amount = 80_000.0,
                downPayment = 0.0,
                annualRate = 12.0,
                termValue = 24,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH,
                currency = FinanceCurrencies.USD
            )
        )

        assertEquals(100_000.0, withDownPayment.requestedAmount, 1e-6)
        assertEquals(20_000.0, withDownPayment.downPayment, 1e-6)
        assertEquals(80_000.0, withDownPayment.principalAmount, 1e-6)
        assertEquals(80_000.0, withDownPayment.schedule.first().openingBalance, 1e-6)
        assertEquals(directPrincipal.monthlyPayment, withDownPayment.monthlyPayment, 1e-6)
        assertEquals(directPrincipal.totalInterest, withDownPayment.totalInterest, 1e-6)
        assertEquals(directPrincipal.totalPayment, withDownPayment.totalPayment, 1e-6)
        assertLedgerInvariants(withDownPayment, expectedPrincipal = 80_000.0, scale = 2)
    }

    @Test
    fun loan10000At12PercentFor3YearsMatchesFrenchValuesAndExactAccountingInvariants() {
        val result = useCase(amount = 10_000.0, annualRate = 12.0, years = 3)

        assertEquals(AmortizationSystem.FRENCH, result.system)
        assertEquals(PaymentFrequency.MONTHLY, result.paymentFrequency)
        assertEquals(RateConvention.NOMINAL_PER_PAYMENT_PERIOD, result.rateConvention)
        assertEquals(332.14, result.monthlyPayment, 1e-6)
        assertEquals(1_957.15, result.totalInterest, 0.05)
        assertEquals(11_957.15, result.totalPayment, 0.05)
        assertEquals(36, result.schedule.size)
        assertEquals(1, result.schedule.first().period)
        assertEquals(36, result.schedule.last().period)
        assertEquals(0.0, result.finalBalance, 1e-9)
        assertNull(result.constantPrincipalPerPeriod)
        assertEquals(0.0, result.totalAdditionalCharges, 1e-9)
        assertEquals(result.totalPayment, result.totalDisbursement, 1e-9)

        assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = 2)

        for (i in 1 until result.schedule.size) {
            assertTrue(result.schedule[i].interest < result.schedule[i - 1].interest)
            assertTrue(result.schedule[i].principal > result.schedule[i - 1].principal)
        }
    }

    @Test
    fun zeroInterestRateAcrossFrenchAndGermanProducesZeroInterestAndFullRepayment() {
        val french = useCase(
            LoanRequest(
                amount = 12_000.0,
                annualRate = 0.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH
            )
        )
        assertEquals(500.0, french.monthlyPayment, 1e-6)
        assertEquals(0.0, french.totalInterest, 1e-9)
        assertEquals(12_000.0, french.totalPayment, 1e-6)
        assertEquals(24, french.schedule.size)
        assertLedgerInvariants(french, expectedPrincipal = 12_000.0, scale = 2)
        french.schedule.forEach { row ->
            assertEquals(0.0, row.interest, 1e-9)
            assertEquals(500.0, row.principal, 1e-6)
        }

        val german = useCase(
            LoanRequest(
                amount = 12_000.0,
                annualRate = 0.0,
                termValue = 24,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.GERMAN
            )
        )
        assertEquals(0.0, german.totalInterest, 1e-9)
        assertEquals(500.0, german.constantPrincipalPerPeriod!!, 1e-6)
        assertLedgerInvariants(german, expectedPrincipal = 12_000.0, scale = 2)
    }

    @Test
    fun germanSystemHasConstantPrincipalDecreasingInterestAndDecreasingInstallments() {
        val result = useCase(
            LoanRequest(
                amount = 12_000.0,
                annualRate = 12.0,
                termValue = 12,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.GERMAN
            )
        )

        assertEquals(12, result.schedule.size)
        assertEquals(1_000.0, result.constantPrincipalPerPeriod!!, 1e-6)
        assertEquals(1_120.0, result.firstPayment, 1e-6)
        assertEquals(1_010.0, result.lastPayment, 1e-6)
        assertEquals(780.0, result.totalInterest, 1e-6)
        assertEquals(12_780.0, result.totalPayment, 1e-6)
        assertLedgerInvariants(result, expectedPrincipal = 12_000.0, scale = 2)

        for (i in 1 until result.schedule.size) {
            assertEquals(1_000.0, result.schedule[i].principal, 1e-6)
            assertTrue(result.schedule[i].interest < result.schedule[i - 1].interest)
            assertTrue(result.schedule[i].payment < result.schedule[i - 1].payment)
        }
    }

    @Test
    fun legacyAmericanEnumBranchRemainsCompatibleInDomainEngine() {
        val result = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 12,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.AMERICAN
            )
        )

        assertEquals(12, result.schedule.size)
        assertEquals(100.0, result.firstPayment, 1e-6)
        assertEquals(10_100.0, result.lastPayment, 1e-6)
        assertEquals(1_200.0, result.totalInterest, 1e-6)
        assertEquals(11_200.0, result.totalPayment, 1e-6)
        assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = 2)
    }

    @Test
    fun simulatorTermsFromSinglePeriodUpTo1200PeriodsCloseBalanceExactlyToZero() {
        val testTerms = listOf(1, 12, 24, 36, 120, 360, 1_200)
        for (system in AmortizationSystem.simulatorSystems) {
            for (periods in testTerms) {
                val loan = useCase(
                    LoanRequest(
                        amount = 150_000.0,
                        annualRate = 8.75,
                        termValue = periods,
                        termUnit = LoanTermUnit.PERIODS,
                        paymentFrequency = PaymentFrequency.MONTHLY,
                        system = system
                    )
                )
                assertEquals(periods, loan.schedule.size)
                assertEquals(0.0, loan.finalBalance, 1e-9)
                assertLedgerInvariants(loan, expectedPrincipal = 150_000.0, scale = 2)
            }
        }
    }

    @Test
    fun allFivePeriodicitiesCalculateCorrectScheduleAndMonotonicDueDatesWhenIssuedDateProvided() {
        val issuedAt = 1_764_115_200_000L // Fixed deterministic epoch timestamp
        for (frequency in PaymentFrequency.entries) {
            for (system in AmortizationSystem.simulatorSystems) {
                val result = useCase(
                    LoanRequest(
                        amount = 50_000.0,
                        annualRate = 12.0,
                        termValue = 2,
                        termUnit = LoanTermUnit.YEARS,
                        paymentFrequency = frequency,
                        system = system,
                        issuedAtMillis = issuedAt
                    )
                )
                assertEquals(2 * frequency.periodsPerYear, result.schedule.size)
                assertEquals(issuedAt, result.issuedAtMillis)
                var previousDate = issuedAt
                result.schedule.forEach { row ->
                    assertNotNull(row.dueDateMillis)
                    assertTrue(row.dueDateMillis!! > previousDate)
                    previousDate = row.dueDateMillis!!
                }
                assertLedgerInvariants(result, expectedPrincipal = 50_000.0, scale = 2)
            }
        }
    }

    @Test
    fun dueDatesFromJan28To31InNonLeapAndLeapYearsAvoidCumulativeDrift() {
        fun utcYmd(millis: Long): Triple<Int, Int, Int> =
            GregorianCalendar(CalculateLoanUseCase.UTC_TIME_ZONE).apply { timeInMillis = millis }.let {
                Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
            }

        // Non-leap year (2025): Jan 28, 29, 30, 31 across 4 monthly periods (Feb, Mar, Apr, May)
        val nonLeapCases = mapOf(
            28 to listOf(
                Triple(2025, Calendar.FEBRUARY, 28),
                Triple(2025, Calendar.MARCH, 28),
                Triple(2025, Calendar.APRIL, 28),
                Triple(2025, Calendar.MAY, 28)
            ),
            29 to listOf(
                Triple(2025, Calendar.FEBRUARY, 28),
                Triple(2025, Calendar.MARCH, 29),
                Triple(2025, Calendar.APRIL, 29),
                Triple(2025, Calendar.MAY, 29)
            ),
            30 to listOf(
                Triple(2025, Calendar.FEBRUARY, 28),
                Triple(2025, Calendar.MARCH, 30),
                Triple(2025, Calendar.APRIL, 30),
                Triple(2025, Calendar.MAY, 30)
            ),
            31 to listOf(
                Triple(2025, Calendar.FEBRUARY, 28),
                Triple(2025, Calendar.MARCH, 31),
                Triple(2025, Calendar.APRIL, 30),
                Triple(2025, Calendar.MAY, 31)
            )
        )
        nonLeapCases.forEach { (day, expectedDates) ->
            val result = useCase(
                LoanRequest(
                    amount = 10_000.0,
                    annualRate = 12.0,
                    termValue = 4,
                    termUnit = LoanTermUnit.PERIODS,
                    paymentFrequency = PaymentFrequency.MONTHLY,
                    issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.JANUARY, day)
                )
            )
            assertEquals(
                "Non-leap Jan $day monthly schedule mismatch",
                expectedDates,
                result.schedule.map { utcYmd(it.dueDateMillis!!) }
            )
        }

        // Leap year (2024): Jan 28, 29, 30, 31 across 4 monthly periods (Feb 29 preserved, no drift)
        val leapCases = mapOf(
            28 to listOf(
                Triple(2024, Calendar.FEBRUARY, 28),
                Triple(2024, Calendar.MARCH, 28),
                Triple(2024, Calendar.APRIL, 28),
                Triple(2024, Calendar.MAY, 28)
            ),
            29 to listOf(
                Triple(2024, Calendar.FEBRUARY, 29),
                Triple(2024, Calendar.MARCH, 29),
                Triple(2024, Calendar.APRIL, 29),
                Triple(2024, Calendar.MAY, 29)
            ),
            30 to listOf(
                Triple(2024, Calendar.FEBRUARY, 29),
                Triple(2024, Calendar.MARCH, 30),
                Triple(2024, Calendar.APRIL, 30),
                Triple(2024, Calendar.MAY, 30)
            ),
            31 to listOf(
                Triple(2024, Calendar.FEBRUARY, 29),
                Triple(2024, Calendar.MARCH, 31),
                Triple(2024, Calendar.APRIL, 30),
                Triple(2024, Calendar.MAY, 31)
            )
        )
        leapCases.forEach { (day, expectedDates) ->
            val result = useCase(
                LoanRequest(
                    amount = 10_000.0,
                    annualRate = 12.0,
                    termValue = 4,
                    termUnit = LoanTermUnit.PERIODS,
                    paymentFrequency = PaymentFrequency.MONTHLY,
                    issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2024, Calendar.JANUARY, day)
                )
            )
            assertEquals(
                "Leap Jan $day monthly schedule mismatch",
                expectedDates,
                result.schedule.map { utcYmd(it.dueDateMillis!!) }
            )
        }
    }

    @Test
    fun dueDatesForFebruaryLeapNonLeapAndQuarterlySemiannualAnnualPeriodsMatchCalendarAnchor() {
        fun utcYmd(millis: Long): Triple<Int, Int, Int> =
            GregorianCalendar(CalculateLoanUseCase.UTC_TIME_ZONE).apply { timeInMillis = millis }.let {
                Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
            }

        // Leap day (2024-02-29) annual: 2025-02-28, 2026-02-28, 2027-02-28, 2028-02-29 (restores leap day)
        val leapAnnual = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 10.0,
                termValue = 4,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.ANNUAL,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2024, Calendar.FEBRUARY, 29)
            )
        )
        assertEquals(
            listOf(
                Triple(2025, Calendar.FEBRUARY, 28),
                Triple(2026, Calendar.FEBRUARY, 28),
                Triple(2027, Calendar.FEBRUARY, 28),
                Triple(2028, Calendar.FEBRUARY, 29)
            ),
            leapAnnual.schedule.map { utcYmd(it.dueDateMillis!!) }
        )

        // Non-leap Feb 28 (2025-02-28) annual stays on 28th even in leap year 2028
        val nonLeapAnnual = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 10.0,
                termValue = 4,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.ANNUAL,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.FEBRUARY, 28)
            )
        )
        assertEquals(
            listOf(
                Triple(2026, Calendar.FEBRUARY, 28),
                Triple(2027, Calendar.FEBRUARY, 28),
                Triple(2028, Calendar.FEBRUARY, 28),
                Triple(2029, Calendar.FEBRUARY, 28)
            ),
            nonLeapAnnual.schedule.map { utcYmd(it.dueDateMillis!!) }
        )

        // Jan 31 quarterly (4 periods): Apr 30, Jul 31, Oct 31, Jan 31 (no drift to 30th)
        val jan31Quarterly = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 4,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.QUARTERLY,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.JANUARY, 31)
            )
        )
        assertEquals(
            listOf(
                Triple(2025, Calendar.APRIL, 30),
                Triple(2025, Calendar.JULY, 31),
                Triple(2025, Calendar.OCTOBER, 31),
                Triple(2026, Calendar.JANUARY, 31)
            ),
            jan31Quarterly.schedule.map { utcYmd(it.dueDateMillis!!) }
        )

        // Jan 31 semiannual (2 periods): Jul 31, Jan 31
        val jan31Semiannual = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 2,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.SEMIANNUAL,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.JANUARY, 31)
            )
        )
        assertEquals(
            listOf(
                Triple(2025, Calendar.JULY, 31),
                Triple(2026, Calendar.JANUARY, 31)
            ),
            jan31Semiannual.schedule.map { utcYmd(it.dueDateMillis!!) }
        )

        // Leap day (2024-02-29) quarterly & semiannual
        val leapQuarterly = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 4,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.QUARTERLY,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2024, Calendar.FEBRUARY, 29)
            )
        )
        assertEquals(
            listOf(
                Triple(2024, Calendar.MAY, 29),
                Triple(2024, Calendar.AUGUST, 29),
                Triple(2024, Calendar.NOVEMBER, 29),
                Triple(2025, Calendar.FEBRUARY, 28)
            ),
            leapQuarterly.schedule.map { utcYmd(it.dueDateMillis!!) }
        )

        val leapSemiannual = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 2,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.SEMIANNUAL,
                issuedAtMillis = CalculateLoanUseCase.civilDateUtcMillis(2024, Calendar.FEBRUARY, 29)
            )
        )
        assertEquals(
            listOf(
                Triple(2024, Calendar.AUGUST, 29),
                Triple(2025, Calendar.FEBRUARY, 28)
            ),
            leapSemiannual.schedule.map { utcYmd(it.dueDateMillis!!) }
        )
    }

    @Test
    fun dueDateCalculationAndShortDateFormattingAreCompletelyInvariantAcrossTimeZones() {
        val originalTz = TimeZone.getDefault()
        val timeZones = listOf("UTC", "America/Managua", "America/New_York", "Asia/Tokyo")
        val issueDates = listOf(
            CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.JANUARY, 31),
            CalculateLoanUseCase.civilDateUtcMillis(2024, Calendar.FEBRUARY, 29),
            CalculateLoanUseCase.civilDateUtcMillis(2025, Calendar.NOVEMBER, 1) // DST transition boundary in US
        )

        try {
            for (issuedAt in issueDates) {
                for (frequency in PaymentFrequency.entries) {
                    val baselineResult = useCase(
                        LoanRequest(
                            amount = 25_000.0,
                            annualRate = 12.0,
                            termValue = 6,
                            termUnit = LoanTermUnit.PERIODS,
                            paymentFrequency = frequency,
                            issuedAtMillis = issuedAt
                        )
                    )
                    val baselineDueMillis = baselineResult.schedule.map { it.dueDateMillis!! }
                    val baselineFormattedUs = baselineDueMillis.map { formatShortDate(it, Locale.US) }
                    val baselineFormattedEs = baselineDueMillis.map { formatShortDate(it, Locale.forLanguageTag("es")) }

                    for (tzId in timeZones) {
                        TimeZone.setDefault(TimeZone.getTimeZone(tzId))
                        val tzResult = useCase(
                            LoanRequest(
                                amount = 25_000.0,
                                annualRate = 12.0,
                                termValue = 6,
                                termUnit = LoanTermUnit.PERIODS,
                                paymentFrequency = frequency,
                                issuedAtMillis = issuedAt
                            )
                        )
                        assertEquals(
                            "Due dates shifted under timezone $tzId for $frequency",
                            baselineDueMillis,
                            tzResult.schedule.map { it.dueDateMillis!! }
                        )
                        // Formatting existing timestamps after a timezone change must remain identical
                        assertEquals(
                            "US formatted dates shifted under timezone $tzId",
                            baselineFormattedUs,
                            baselineDueMillis.map { formatShortDate(it, Locale.US) }
                        )
                        assertEquals(
                            "ES formatted dates shifted under timezone $tzId",
                            baselineFormattedEs,
                            baselineDueMillis.map { formatShortDate(it, Locale.forLanguageTag("es")) }
                        )
                    }
                }
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun compareSystemsEdgeCasesZeroRateSinglePeriodAndDownPaymentExclusionAreConsistent() {
        // Zero rate: both systems identical -> all differences 0.0
        val zeroRateCmp = useCase.compare(
            LoanRequest(
                amount = 50_000.0,
                downPayment = 10_000.0,
                annualRate = 0.0,
                termValue = 10,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY
            )
        )
        assertEquals(50_000.0, zeroRateCmp.french.requestedAmount, 1e-6)
        assertEquals(10_000.0, zeroRateCmp.french.downPayment, 1e-6)
        assertEquals(40_000.0, zeroRateCmp.french.principalAmount, 1e-6)
        assertEquals(40_000.0, zeroRateCmp.french.totalPayment, 1e-6)
        assertEquals(40_000.0, zeroRateCmp.french.totalDisbursement, 1e-6)
        assertEquals(0.0, zeroRateCmp.interestDifference, 1e-9)
        assertEquals(0.0, zeroRateCmp.totalPaymentDifference, 1e-9)
        assertEquals(0.0, zeroRateCmp.firstPaymentDifference, 1e-9)
        assertEquals(0.0, zeroRateCmp.lastPaymentDifference, 1e-9)

        // Single installment: French and German produce identical single payment
        val singlePeriodCmp = useCase.compare(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 1,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY
            )
        )
        assertEquals(10_100.0, singlePeriodCmp.french.firstPayment, 1e-6)
        assertEquals(10_100.0, singlePeriodCmp.german.firstPayment, 1e-6)
        assertEquals(0.0, singlePeriodCmp.interestDifference, 1e-9)
        assertEquals(0.0, singlePeriodCmp.totalPaymentDifference, 1e-9)
        assertEquals(0.0, singlePeriodCmp.firstPaymentDifference, 1e-9)
        assertEquals(0.0, singlePeriodCmp.lastPaymentDifference, 1e-9)
    }

    @Test
    fun monetaryRoundingAdjustsFinalInstallmentForNonDivisibleAndFractionalCentPrincipals() {
        // 1000 / 3 = 333.33, 333.33, 333.34
        val zeroRateThreePeriods = useCase(
            LoanRequest(
                amount = 1_000.0,
                annualRate = 0.0,
                termValue = 3,
                termUnit = LoanTermUnit.PERIODS,
                system = AmortizationSystem.GERMAN
            )
        )
        assertEquals(333.33, zeroRateThreePeriods.schedule[0].principal, 1e-9)
        assertEquals(333.33, zeroRateThreePeriods.schedule[1].principal, 1e-9)
        assertEquals(333.34, zeroRateThreePeriods.schedule[2].principal, 1e-9)
        assertEquals(0.0, zeroRateThreePeriods.finalBalance, 1e-9)
        assertLedgerInvariants(zeroRateThreePeriods, expectedPrincipal = 1_000.0, scale = 2)

        // Odd amount with fractional cents and non-terminating rate (7.77% over 7 quarterly periods)
        for (system in AmortizationSystem.simulatorSystems) {
            val oddLoan = useCase(
                LoanRequest(
                    amount = 1_234.57,
                    annualRate = 7.77,
                    termValue = 7,
                    termUnit = LoanTermUnit.PERIODS,
                    paymentFrequency = PaymentFrequency.QUARTERLY,
                    system = system
                )
            )
            assertLedgerInvariants(oddLoan, expectedPrincipal = 1_234.57, scale = 2)
        }
    }

    @Test
    fun nominalAndEffectiveAnnualRateConventionsAreDistinctAndMathematicallyConsistent() {
        val nominal = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH,
                rateConvention = RateConvention.NOMINAL_PER_PAYMENT_PERIOD
            )
        )
        val effective = useCase(
            LoanRequest(
                amount = 10_000.0,
                annualRate = 12.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH,
                rateConvention = RateConvention.EFFECTIVE_ANNUAL
            )
        )

        assertEquals(0.01, nominal.periodicRate, 1e-9)
        assertEquals(1.01.pow(12) - 1.0, nominal.effectiveAnnualRate, 1e-9)

        val expectedPeriodicEar = 1.12.pow(1.0 / 12.0) - 1.0
        assertEquals(expectedPeriodicEar, effective.periodicRate, 1e-9)
        assertEquals(0.12, effective.effectiveAnnualRate, 1e-9)
        assertEquals(0.12, (1.0 + effective.periodicRate).pow(12) - 1.0, 1e-9)
        assertTrue(effective.totalInterest < nominal.totalInterest)
    }

    @Test
    fun supportsMultipleCurrenciesIndependentlyOfLocaleIncludingZeroDecimalCurrency() {
        val customZeroDecimal = FinanceCurrency(code = "JPY", symbol = "¥", fractionDigits = 0)
        val currencies = listOf(FinanceCurrencies.NIO, FinanceCurrencies.USD, FinanceCurrencies.EUR, customZeroDecimal)

        currencies.forEach { currency ->
            for (system in AmortizationSystem.simulatorSystems) {
                val result = useCase(
                    LoanRequest(
                        amount = 10_000.0,
                        annualRate = 10.0,
                        termValue = 12,
                        termUnit = LoanTermUnit.PERIODS,
                        paymentFrequency = PaymentFrequency.MONTHLY,
                        system = system,
                        currency = currency
                    )
                )
                assertEquals(currency, result.currency)
                assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = currency.fractionDigits)
            }
        }

        assertEquals("C$ 10,000.00", formatMoney(10_000.0, FinanceCurrencies.NIO, Locale.US))
        assertEquals("€ 10,000.00", formatMoney(10_000.0, FinanceCurrencies.EUR, Locale.US))
        assertTrue(formatMoney(10_000.0, FinanceCurrencies.USD, Locale.GERMANY).startsWith("$ "))
        assertTrue(formatMoney(10_000.0, FinanceCurrencies.NIO, Locale.UK).startsWith("C$ "))
        assertEquals("¥ 10,000", formatMoney(10_000.0, customZeroDecimal, Locale.US))
    }

    @Test
    fun invalidParametersAndInvalidDownPaymentThrowIllegalArgumentException() {
        assertIllegalArgument { useCase(amount = 0.0, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = -5_000.0, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = -1.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = 12.0, years = 0) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = 12.0, years = -2) }
        assertIllegalArgument { useCase(amount = Double.NaN, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = Double.NaN, years = 3) }
        // Invalid down payments
        assertIllegalArgument {
            useCase(LoanRequest(amount = 10_000.0, downPayment = -100.0, annualRate = 12.0, termValue = 12, termUnit = LoanTermUnit.PERIODS))
        }
        assertIllegalArgument {
            useCase(LoanRequest(amount = 10_000.0, downPayment = 10_000.0, annualRate = 12.0, termValue = 12, termUnit = LoanTermUnit.PERIODS))
        }
        assertIllegalArgument {
            useCase(LoanRequest(amount = 10_000.0, downPayment = 12_000.0, annualRate = 12.0, termValue = 12, termUnit = LoanTermUnit.PERIODS))
        }
        assertIllegalArgument {
            useCase(
                LoanRequest(
                    amount = 10_000.0,
                    annualRate = 12.0,
                    termValue = CalculateLoanUseCase.MAX_PERIODS + 1,
                    termUnit = LoanTermUnit.PERIODS
                )
            )
        }
    }

    @Test
    fun effectiveAnnualPeriodicRateConversionMaintainsBigDecimalPrecisionAcrossAllPeriodicities() {
        val rates = listOf(0.5, 12.0, 24.0, 36.0, 100.0, 300.0)
        val mc = MathContext.DECIMAL128
        val tolerance = BigDecimal("1E-28")

        for (ratePercent in rates) {
            val expectedTea = BigDecimal.valueOf(ratePercent).divide(BigDecimal(100), mc)
            for (frequency in PaymentFrequency.entries) {
                val periodicBd = useCase.computeEffectivePeriodicRate(ratePercent, frequency.periodsPerYear)
                val reconstructedTea = BigDecimal.ONE.add(periodicBd, mc)
                    .pow(frequency.periodsPerYear, mc)
                    .subtract(BigDecimal.ONE, mc)
                val error = reconstructedTea.subtract(expectedTea, mc).abs()
                assertTrue(
                    "TEA BigDecimal conversion error $error exceeded $tolerance for rate=$ratePercent%, freq=$frequency",
                    error <= tolerance
                )

                val result = useCase(
                    LoanRequest(
                        amount = 100_000.0,
                        annualRate = ratePercent,
                        termValue = 12,
                        termUnit = LoanTermUnit.PERIODS,
                        paymentFrequency = frequency,
                        rateConvention = RateConvention.EFFECTIVE_ANNUAL,
                        system = AmortizationSystem.FRENCH
                    )
                )
                assertEquals(expectedTea.toDouble(), result.effectiveAnnualRate, 1e-12)
                assertLedgerInvariants(result, expectedPrincipal = 100_000.0, scale = 2)
            }
        }
    }

    @Test
    fun downPaymentTotalOutflowHelperSeparatesInstallmentSumFromUpfrontDownPayment() {
        val result = useCase(
            LoanRequest(
                amount = 100_000.0,
                downPayment = 20_000.0,
                annualRate = 12.0,
                termValue = 2,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.GERMAN,
                currency = FinanceCurrencies.USD
            )
        )

        assertEquals(100_000.0, result.requestedAmount, 1e-9)
        assertEquals(20_000.0, result.downPayment, 1e-9)
        assertEquals(80_000.0, result.principalAmount, 1e-9)
        assertEquals(result.requestedAmount, result.downPayment + result.principalAmount, 1e-9)
        // totalPayment strictly excludes upfront down payment
        assertEquals(result.principalAmount + result.totalInterest, result.totalPayment, 1e-6)
        // totalOutflowWithDownPayment includes down payment + installments + additional charges
        val expectedOutflow = BigDecimal.valueOf(result.downPayment)
            .add(BigDecimal.valueOf(result.totalPayment))
            .add(BigDecimal.valueOf(result.totalAdditionalCharges))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
        assertEquals(expectedOutflow, result.totalOutflowWithDownPayment(), 1e-9)
        assertLedgerInvariants(result, expectedPrincipal = 80_000.0, scale = 2)
    }

    @Test
    fun compactMoneyAxisFormatterProducesReadableNonTruncatedLabelsAcrossMagnitudes() {
        val nio = FinanceCurrencies.NIO
        val jpy = FinanceCurrency(code = "JPY", symbol = "¥", fractionDigits = 0)
        assertEquals("0", formatCompactMoneyAxis(0.0, nio, Locale.US))
        assertEquals("332", formatCompactMoneyAxis(332.14, nio, Locale.US))
        assertEquals("75k", formatCompactMoneyAxis(75_000.0, nio, Locale.US))
        assertEquals("600k", formatCompactMoneyAxis(600_000.0, nio, Locale.US))
        assertEquals("1.3M", formatCompactMoneyAxis(1_250_000.0, nio, Locale.US))
        assertEquals("15M", formatCompactMoneyAxis(15_000_000.0, nio, Locale.US))
        assertEquals("1.5B", formatCompactMoneyAxis(1_500_000_000.0, nio, Locale.US))
        assertEquals("50", formatCompactMoneyAxis(50.0, jpy, Locale.US))
    }

    @Test
    fun effectiveAnnualRateMatrixCoversAllFivePeriodicitiesAndRequiredRatesToDecimal128Precision() {
        val requiredRates = listOf(0.0, 0.5, 1.0, 5.0, 12.0, 36.0, 100.0, 300.0)
        val mc = MathContext.DECIMAL128
        val tolerance = BigDecimal("1E-29")

        for (ratePercent in requiredRates) {
            val expectedTea = BigDecimal.valueOf(ratePercent).divide(BigDecimal(100), mc)
            val periodicByM = mutableMapOf<Int, BigDecimal>()

            for (frequency in PaymentFrequency.entries) {
                val m = frequency.periodsPerYear
                val periodic = useCase.computeEffectivePeriodicRate(ratePercent, m)
                periodicByM[m] = periodic

                if (ratePercent == 0.0) {
                    assertEquals(0, BigDecimal.ZERO.compareTo(periodic))
                } else {
                    assertTrue("Periodic rate must be > 0 for rate=$ratePercent%, m=$m", periodic > BigDecimal.ZERO)
                }

                if (m == 1) {
                    assertEquals(0, expectedTea.compareTo(periodic))
                }

                val reconstructedTea = BigDecimal.ONE.add(periodic, mc)
                    .pow(m, mc)
                    .subtract(BigDecimal.ONE, mc)
                val residual = reconstructedTea.subtract(expectedTea, mc).abs()
                assertTrue(
                    "Residual $residual exceeded $tolerance for TEA=$ratePercent%, m=$m",
                    residual <= tolerance
                )
            }

            // Cross-periodicity algebraic consistency: (1+i_24)^2 == 1+i_12, (1+i_12)^3 == 1+i_4, (1+i_4)^2 == 1+i_2
            val onePlus24 = BigDecimal.ONE.add(periodicByM.getValue(24), mc)
            val onePlus12 = BigDecimal.ONE.add(periodicByM.getValue(12), mc)
            val onePlus4 = BigDecimal.ONE.add(periodicByM.getValue(4), mc)
            val onePlus2 = BigDecimal.ONE.add(periodicByM.getValue(2), mc)

            assertTrue(onePlus24.pow(2, mc).subtract(onePlus12, mc).abs() <= tolerance)
            assertTrue(onePlus12.pow(3, mc).subtract(onePlus4, mc).abs() <= tolerance)
            assertTrue(onePlus4.pow(2, mc).subtract(onePlus2, mc).abs() <= tolerance)

            if (ratePercent > 0.0) {
                assertTrue(periodicByM.getValue(24) < periodicByM.getValue(12))
                assertTrue(periodicByM.getValue(12) < periodicByM.getValue(4))
                assertTrue(periodicByM.getValue(4) < periodicByM.getValue(2))
                assertTrue(periodicByM.getValue(2) < periodicByM.getValue(1))
            }
        }
    }

    @Test
    fun pureBigDecimalInitialSeedIsUpperBoundAndConvergesAcrossFractionalAndBeyondDoubleMagnitudes() {
        val mc = MathContext.DECIMAL128
        val relativeTolerance = BigDecimal("1E-29")
        val testBases = listOf(
            BigDecimal("1E-24"),
            BigDecimal("0.0001"),
            BigDecimal("0.5"),
            BigDecimal("1.000001"),
            BigDecimal("1.12"),
            BigDecimal("4.0"),
            BigDecimal("11.0"),        // 1,000% TEA
            BigDecimal("1001.0"),      // 100,000% TEA
            BigDecimal("1E24"),
            BigDecimal("1E400")        // Strictly exceeds IEEE-754 Double.MAX_VALUE (~1.8E308)
        )
        val degrees = listOf(2, 4, 12, 24)

        for (base in testBases) {
            for (degree in degrees) {
                val seed = useCase.initialNthRootSeed(base, degree)
                assertTrue("Seed $seed must be strictly positive", seed > BigDecimal.ZERO)
                assertTrue(
                    "Convexity upper bound violated for base=$base, degree=$degree: seed^m < base",
                    seed.pow(degree, mc) >= base
                )

                val root = useCase.nthRootBigDecimal(base, degree)
                val relativeError = root.pow(degree, mc)
                    .subtract(base, mc)
                    .abs()
                    .divide(base, mc)
                assertTrue(
                    "Relative residual $relativeError exceeded $relativeTolerance for base=$base, degree=$degree",
                    relativeError <= relativeTolerance
                )
            }
        }

        // Tiny and extreme financial rates via computeEffectivePeriodicRate
        for (extremeRate in listOf(0.0001, 1000.0)) {
            val expectedTea = BigDecimal.valueOf(extremeRate).divide(BigDecimal(100), mc)
            val periodic = useCase.computeEffectivePeriodicRate(extremeRate, 24)
            val reconstructed = BigDecimal.ONE.add(periodic, mc).pow(24, mc).subtract(BigDecimal.ONE, mc)
            val relativeError = reconstructed.subtract(expectedTea, mc).abs()
                .divide(expectedTea.max(BigDecimal.ONE), mc)
            assertTrue("Extreme rate $extremeRate% relative error $relativeError too large", relativeError <= relativeTolerance)
        }
    }

    @Test
    fun nthRootAndEffectiveRateRejectInvalidInputsAndThrowArithmeticExceptionOnNonConvergence() {
        // Invalid computeEffectivePeriodicRate inputs
        assertIllegalArgument { useCase.computeEffectivePeriodicRate(-0.01, 12) }
        assertIllegalArgument { useCase.computeEffectivePeriodicRate(Double.NaN, 12) }
        assertIllegalArgument { useCase.computeEffectivePeriodicRate(Double.POSITIVE_INFINITY, 12) }
        assertIllegalArgument { useCase.computeEffectivePeriodicRate(12.0, 0) }
        assertIllegalArgument { useCase.computeEffectivePeriodicRate(12.0, -4) }

        // Invalid nthRootBigDecimal inputs
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal.ZERO, 12) }
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal("-1.5"), 12) }
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal("1.12"), 0) }
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal("1.12"), -2) }
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal("1.12"), 12, maxIterations = 0) }
        assertIllegalArgument { useCase.nthRootBigDecimal(BigDecimal("1.12"), 12, tolerance = BigDecimal.ZERO) }

        // Non-convergence with insufficient iterations MUST throw ArithmeticException (never return silently)
        try {
            useCase.nthRootBigDecimal(
                value = BigDecimal("4.0"),
                degree = 24,
                maxIterations = 1,
                tolerance = BigDecimal("1E-31")
            )
            fail("Expected ArithmeticException when Newton-Raphson does not converge within maxIterations")
        } catch (_: ArithmeticException) {
            // Expected controlled exception
        }

        // Non-convergence with unachievable sub-DECIMAL128 tolerance MUST also throw ArithmeticException
        try {
            useCase.nthRootBigDecimal(
                value = BigDecimal("1.12"),
                degree = 12,
                maxIterations = 16,
                tolerance = BigDecimal("1E-40")
            )
            fail("Expected ArithmeticException when requested tolerance is tighter than DECIMAL128 precision")
        } catch (_: ArithmeticException) {
            // Expected controlled exception
        }
    }

    /**
     * Verifies exact accounting invariants to the currency minor unit across every row in [result]:
     * - openingBalance matches running balance prior to period principal
     * - principal <= openingBalance
     * - payment == principal + interest
     * - closingBalance == openingBalance - principal >= 0
     * - sum(principal) == expectedPrincipal and final balance == 0
     * - non-financial charges hook stays 0.0 and totalDisbursement == payment
     */
    private fun assertLedgerInvariants(
        result: LoanResult,
        expectedPrincipal: Double,
        scale: Int
    ) {
        val expectedPrincipalBd = BigDecimal.valueOf(expectedPrincipal).setScale(scale, RoundingMode.HALF_UP)
        var runningBalance = expectedPrincipalBd
        var sumPrincipal = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        var sumInterest = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        var sumPayment = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)

        result.schedule.forEachIndexed { idx, row ->
            assertEquals(idx + 1, row.period)
            val opening = BigDecimal.valueOf(row.openingBalance).setScale(scale, RoundingMode.HALF_UP)
            val payment = BigDecimal.valueOf(row.payment).setScale(scale, RoundingMode.HALF_UP)
            val principal = BigDecimal.valueOf(row.principal).setScale(scale, RoundingMode.HALF_UP)
            val interest = BigDecimal.valueOf(row.interest).setScale(scale, RoundingMode.HALF_UP)
            val balance = BigDecimal.valueOf(row.balance).setScale(scale, RoundingMode.HALF_UP)

            assertEquals("Row ${row.period}: opening balance mismatch", runningBalance, opening)
            assertTrue("Row ${row.period}: principal must not exceed opening balance", principal <= opening)
            assertEquals("Row ${row.period}: payment must equal principal + interest", principal.add(interest), payment)
            runningBalance = runningBalance.subtract(principal)
            assertEquals("Row ${row.period}: closing balance mismatch", runningBalance, balance)
            assertTrue("Row ${row.period}: balance must not be negative", balance >= BigDecimal.ZERO)
            assertEquals("Row ${row.period}: no fictitious charges allowed", 0.0, row.additionalCharges, 1e-9)
            assertEquals("Row ${row.period}: disbursement must match payment when charges = 0", row.payment, row.totalDisbursement, 1e-9)

            sumPrincipal = sumPrincipal.add(principal)
            sumInterest = sumInterest.add(interest)
            sumPayment = sumPayment.add(payment)
        }

        assertEquals(expectedPrincipalBd, sumPrincipal)
        assertEquals(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), runningBalance)
        assertEquals(
            BigDecimal.valueOf(result.totalInterest).setScale(scale, RoundingMode.HALF_UP),
            sumInterest
        )
        assertEquals(
            BigDecimal.valueOf(result.totalPayment).setScale(scale, RoundingMode.HALF_UP),
            sumPayment
        )
        assertEquals(sumPrincipal.add(sumInterest), sumPayment)
        assertEquals(0.0, result.totalAdditionalCharges, 1e-9)
        assertEquals(result.totalPayment, result.totalDisbursement, 1e-9)
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}
