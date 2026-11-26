package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.ui.util.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
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
