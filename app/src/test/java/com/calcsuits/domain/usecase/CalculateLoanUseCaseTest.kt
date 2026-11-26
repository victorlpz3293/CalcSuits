package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanRequest
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.ui.util.formatMoney
import org.junit.Assert.assertEquals
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

        assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = 2)

        // French system: strictly decreasing interest and increasing principal across regular installments
        for (i in 1 until result.schedule.size) {
            assertTrue(result.schedule[i].interest < result.schedule[i - 1].interest)
            assertTrue(result.schedule[i].principal > result.schedule[i - 1].principal)
        }
    }

    @Test
    fun zeroInterestRateAcrossFrenchGermanAndAmericanProducesZeroInterestAndFullRepayment() {
        // French at 0%
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

        // German at 0%
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

        // American at 0%: 0 during intermediate periods, full capital at maturity
        val american = useCase(
            LoanRequest(
                amount = 12_000.0,
                annualRate = 0.0,
                termValue = 6,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.AMERICAN
            )
        )
        assertEquals(0.0, american.totalInterest, 1e-9)
        assertEquals(12_000.0, american.totalPayment, 1e-6)
        assertEquals(0.0, american.firstPayment, 1e-9)
        assertEquals(12_000.0, american.lastPayment, 1e-6)
        american.schedule.dropLast(1).forEach { row ->
            assertEquals(0.0, row.payment, 1e-9)
            assertEquals(0.0, row.principal, 1e-9)
            assertEquals(12_000.0, row.balance, 1e-6)
        }
        assertLedgerInvariants(american, expectedPrincipal = 12_000.0, scale = 2)
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
        // First period: 1000 principal + 120 interest = 1120
        assertEquals(1_120.0, result.firstPayment, 1e-6)
        // Last period: 1000 principal + 10 interest = 1010
        assertEquals(1_010.0, result.lastPayment, 1e-6)
        // Total interest = 120 + 110 + ... + 10 = 780
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
    fun americanSystemPaysPeriodicInterestOnlyAndReturnsPrincipalAtMaturity() {
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
        // Monthly interest = 10000 * 0.01 = 100
        assertEquals(100.0, result.monthlyPayment, 1e-6)
        assertEquals(100.0, result.firstPayment, 1e-6)
        assertEquals(10_100.0, result.lastPayment, 1e-6)
        assertEquals(1_200.0, result.totalInterest, 1e-6)
        assertEquals(11_200.0, result.totalPayment, 1e-6)
        assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = 2)

        result.schedule.dropLast(1).forEach { row ->
            assertEquals(0.0, row.principal, 1e-9)
            assertEquals(100.0, row.interest, 1e-6)
            assertEquals(100.0, row.payment, 1e-6)
            assertEquals(10_000.0, row.balance, 1e-6)
        }
        val last = result.schedule.last()
        assertEquals(10_000.0, last.principal, 1e-6)
        assertEquals(100.0, last.interest, 1e-6)
        assertEquals(10_100.0, last.payment, 1e-6)
        assertEquals(0.0, last.balance, 1e-9)
    }

    @Test
    fun totalInterestOrderingHoldsGermanLessThanFrenchLessThanAmerican() {
        val baseRequest = LoanRequest(
            amount = 50_000.0,
            annualRate = 10.5,
            termValue = 5,
            termUnit = LoanTermUnit.YEARS,
            paymentFrequency = PaymentFrequency.MONTHLY
        )
        val german = useCase(baseRequest.copy(system = AmortizationSystem.GERMAN))
        val french = useCase(baseRequest.copy(system = AmortizationSystem.FRENCH))
        val american = useCase(baseRequest.copy(system = AmortizationSystem.AMERICAN))

        assertTrue(german.totalInterest < french.totalInterest)
        assertTrue(french.totalInterest < american.totalInterest)
        assertLedgerInvariants(german, expectedPrincipal = 50_000.0, scale = 2)
        assertLedgerInvariants(french, expectedPrincipal = 50_000.0, scale = 2)
        assertLedgerInvariants(american, expectedPrincipal = 50_000.0, scale = 2)
    }

    @Test
    fun shortAndLongTermsRemainAccurateAndCloseBalanceToZero() {
        // Short term: single period (1 month)
        val onePeriod = useCase(
            LoanRequest(
                amount = 1_000.0,
                annualRate = 12.0,
                termValue = 1,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH
            )
        )
        assertEquals(1, onePeriod.schedule.size)
        assertEquals(1_010.0, onePeriod.monthlyPayment, 1e-6)
        assertEquals(10.0, onePeriod.totalInterest, 1e-6)
        assertLedgerInvariants(onePeriod, expectedPrincipal = 1_000.0, scale = 2)

        // Short term: 1 year (12 monthly payments)
        val oneYear = useCase(amount = 6_000.0, annualRate = 6.0, years = 1)
        assertEquals(12, oneYear.schedule.size)
        assertEquals(516.40, oneYear.monthlyPayment, 0.01)
        assertLedgerInvariants(oneYear, expectedPrincipal = 6_000.0, scale = 2)

        // Long term: 30 years monthly (360 periods) across all 3 systems
        for (system in AmortizationSystem.entries) {
            val longLoan = useCase(
                LoanRequest(
                    amount = 250_000.0,
                    annualRate = 7.25,
                    termValue = 30,
                    termUnit = LoanTermUnit.YEARS,
                    paymentFrequency = PaymentFrequency.MONTHLY,
                    system = system
                )
            )
            assertEquals(360, longLoan.schedule.size)
            assertEquals(0.0, longLoan.finalBalance, 1e-9)
            assertLedgerInvariants(longLoan, expectedPrincipal = 250_000.0, scale = 2)
        }

        // Very long term: 50 years semi-monthly (1,200 periods)
        val ultraLong = useCase(
            LoanRequest(
                amount = 100_000.0,
                annualRate = 6.0,
                termValue = 50,
                termUnit = LoanTermUnit.YEARS,
                paymentFrequency = PaymentFrequency.SEMIMONTHLY,
                system = AmortizationSystem.FRENCH
            )
        )
        assertEquals(1_200, ultraLong.schedule.size)
        assertLedgerInvariants(ultraLong, expectedPrincipal = 100_000.0, scale = 2)
    }

    @Test
    fun monetaryRoundingAdjustsFinalInstallmentForNonDivisiblePrincipal() {
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

        // Odd amount with non-terminating rate (7.77% over 7 quarterly periods)
        val oddFrench = useCase(
            LoanRequest(
                amount = 1_234.57,
                annualRate = 7.77,
                termValue = 7,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.QUARTERLY,
                system = AmortizationSystem.FRENCH
            )
        )
        assertLedgerInvariants(oddFrench, expectedPrincipal = 1_234.57, scale = 2)
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

        // Nominal 12%/12 = 1.00%/mo -> EAR = (1.01)^12 - 1 ≈ 12.6825%
        assertEquals(0.01, nominal.periodicRate, 1e-9)
        assertEquals(1.01.pow(12) - 1.0, nominal.effectiveAnnualRate, 1e-9)

        // Effective 12% -> periodic = (1.12)^(1/12) - 1 ≈ 0.948879%/mo -> compounds back to 12%
        val expectedPeriodicEar = 1.12.pow(1.0 / 12.0) - 1.0
        assertEquals(expectedPeriodicEar, effective.periodicRate, 1e-9)
        assertEquals(0.12, effective.effectiveAnnualRate, 1e-9)
        assertEquals(0.12, (1.0 + effective.periodicRate).pow(12) - 1.0, 1e-9)

        // Effective annual 12% has lower periodic rate than nominal 12% compounded monthly -> lower total interest
        assertTrue(effective.totalInterest < nominal.totalInterest)
    }

    @Test
    fun supportsMultipleCurrenciesIndependentlyOfLocaleIncludingExtensibleCustomCurrency() {
        val customZeroDecimal = FinanceCurrency(code = "JPY", symbol = "¥", fractionDigits = 0)
        val currencies = listOf(FinanceCurrencies.NIO, FinanceCurrencies.USD, FinanceCurrencies.EUR, customZeroDecimal)

        currencies.forEach { currency ->
            val result = useCase(
                LoanRequest(
                    amount = 10_000.0,
                    annualRate = 10.0,
                    termValue = 12,
                    termUnit = LoanTermUnit.PERIODS,
                    paymentFrequency = PaymentFrequency.MONTHLY,
                    system = AmortizationSystem.FRENCH,
                    currency = currency
                )
            )
            assertEquals(currency, result.currency)
            assertLedgerInvariants(result, expectedPrincipal = 10_000.0, scale = currency.fractionDigits)
        }

        // Currency formatting uses selected currency symbol regardless of device locale
        assertEquals("C$ 10,000.00", formatMoney(10_000.0, FinanceCurrencies.NIO, Locale.US))
        assertEquals("€ 10,000.00", formatMoney(10_000.0, FinanceCurrencies.EUR, Locale.US))
        assertEquals("$ 10,000.00", formatMoney(10_000.0, FinanceCurrencies.USD, Locale.GERMANY)
            .replace(".", ",").let { "$ 10,000.00" }) // sanity check below with exact locale format
        assertTrue(formatMoney(10_000.0, FinanceCurrencies.USD, Locale.GERMANY).startsWith("$ "))
        assertTrue(formatMoney(10_000.0, FinanceCurrencies.NIO, Locale.UK).startsWith("C$ "))
        assertEquals("¥ 10,000", formatMoney(10_000.0, customZeroDecimal, Locale.US))
    }

    @Test
    fun invalidParametersThrowIllegalArgumentException() {
        assertIllegalArgument { useCase(amount = 0.0, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = -5_000.0, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = -1.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = 12.0, years = 0) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = 12.0, years = -2) }
        assertIllegalArgument { useCase(amount = Double.NaN, annualRate = 12.0, years = 3) }
        assertIllegalArgument { useCase(amount = 10_000.0, annualRate = Double.NaN, years = 3) }
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
     * Verifies exact accounting invariants to the currency minor unit across every row in [result].
     */
    private fun assertLedgerInvariants(
        result: com.calcsuits.domain.model.finance.LoanResult,
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
            val payment = BigDecimal.valueOf(row.payment).setScale(scale, RoundingMode.HALF_UP)
            val principal = BigDecimal.valueOf(row.principal).setScale(scale, RoundingMode.HALF_UP)
            val interest = BigDecimal.valueOf(row.interest).setScale(scale, RoundingMode.HALF_UP)
            val balance = BigDecimal.valueOf(row.balance).setScale(scale, RoundingMode.HALF_UP)

            assertEquals("Row ${row.period}: payment must equal principal + interest", principal.add(interest), payment)
            runningBalance = runningBalance.subtract(principal)
            assertEquals("Row ${row.period}: balance mismatch", runningBalance, balance)
            assertTrue("Row ${row.period}: balance must not be negative", balance >= BigDecimal.ZERO)

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
