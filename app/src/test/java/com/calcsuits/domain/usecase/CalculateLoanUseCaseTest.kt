package com.calcsuits.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CalculateLoanUseCaseTest {

    private lateinit var useCase: CalculateLoanUseCase

    @Before
    fun setUp() {
        useCase = CalculateLoanUseCase()
    }

    @Test
    fun loan10000At12PercentFor3YearsMatchesExpectedValuesAndAmortizationSum() {
        val result = useCase(amount = 10_000.0, annualRate = 12.0, years = 3)

        assertEquals(332.14, result.monthlyPayment, 0.01)
        assertEquals(1_957.15, result.totalInterest, 0.01)
        assertEquals(11_957.15, result.totalPayment, 0.01)
        assertEquals(36, result.schedule.size)
        assertEquals(1, result.schedule.first().period)
        assertEquals(36, result.schedule.last().period)
        assertEquals(0.0, result.schedule.last().balance, 1e-6)

        val principalSum = result.schedule.sumOf { it.principal }
        assertEquals(10_000.0, principalSum, 1e-6)

        val interestSum = result.schedule.sumOf { it.interest }
        assertEquals(result.totalInterest, interestSum, 1e-6)
    }

    @Test
    fun zeroInterestRateProducesZeroInterestAndEqualPayments() {
        val result = useCase(amount = 12_000.0, annualRate = 0.0, years = 2)

        assertEquals(500.0, result.monthlyPayment, 1e-6)
        assertEquals(0.0, result.totalInterest, 1e-6)
        assertEquals(12_000.0, result.totalPayment, 1e-6)
        assertEquals(24, result.schedule.size)
        assertEquals(12_000.0, result.schedule.sumOf { it.principal }, 1e-6)
        assertEquals(0.0, result.schedule.last().balance, 1e-6)
        result.schedule.forEach { row ->
            assertEquals(0.0, row.interest, 1e-9)
            assertEquals(500.0, row.principal, 1e-6)
        }
    }

    @Test
    fun minimumSupportedTermOneYearProduces12PaymentsAndFullPrincipalAmortization() {
        // Note: CalculateLoanUseCase takes years: Int (minimum 1 year = 12 months);
        // a 1-month term is not representable with the current Int years signature.
        val result = useCase(amount = 6_000.0, annualRate = 6.0, years = 1)

        assertEquals(12, result.schedule.size)
        assertEquals(516.40, result.monthlyPayment, 0.01)
        assertEquals(6_000.0, result.schedule.sumOf { it.principal }, 1e-6)
        assertEquals(0.0, result.schedule.last().balance, 1e-6)
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
