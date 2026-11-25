package com.calcsuits.features.finance.ui.viewmodel

import com.calcsuits.domain.usecase.CalculateLoanUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinanceViewModelTest {

    private lateinit var viewModel: FinanceViewModel

    @Before
    fun setUp() {
        viewModel = FinanceViewModel(CalculateLoanUseCase())
    }

    @Test
    fun initialLoanStateCalculatesDefault10000At12PercentFor3Years() {
        val loan = viewModel.state.value.loanResult
        assertNotNull(loan)
        assertEquals(332.14, loan!!.monthlyPayment, 0.01)
        assertEquals(1_957.15, loan.totalInterest, 0.01)
        assertEquals(36, loan.schedule.size)
    }

    @Test
    fun simpleAndCompoundInterestAnnualCompoundingMatchesFormula() {
        viewModel.onPrincipalChange("1000")
        viewModel.onAnnualRateChange("10")
        viewModel.onTermYearsChange("2")
        viewModel.onFrequencyChange(CompoundingFrequency.ANNUAL)

        val result = viewModel.state.value.interestResult
        assertNotNull(result)
        assertEquals(200.0, result!!.simpleInterest, 1e-6)
        assertEquals(1_200.0, result.simpleTotal, 1e-6)
        assertEquals(210.0, result.compoundInterest, 1e-6)
        assertEquals(1_210.0, result.compoundTotal, 1e-6)
    }

    @Test
    fun higherCompoundingFrequencyYieldsHigherCompoundInterest() {
        viewModel.onPrincipalChange("5000")
        viewModel.onAnnualRateChange("8")
        viewModel.onTermYearsChange("5")

        viewModel.onFrequencyChange(CompoundingFrequency.ANNUAL)
        val annual = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.QUARTERLY)
        val quarterly = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.MONTHLY)
        val monthly = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.DAILY)
        val daily = viewModel.state.value.interestResult!!.compoundTotal

        assertTrue(annual < quarterly)
        assertTrue(quarterly < monthly)
        assertTrue(monthly < daily)
        assertEquals(7_449.23, monthly, 0.01)
    }

    @Test
    fun zeroRateInterestProducesZeroGain() {
        viewModel.onPrincipalChange("2500")
        viewModel.onAnnualRateChange("0")
        viewModel.onTermYearsChange("4")
        viewModel.onFrequencyChange(CompoundingFrequency.MONTHLY)

        val result = viewModel.state.value.interestResult
        assertNotNull(result)
        assertEquals(0.0, result!!.simpleInterest, 1e-9)
        assertEquals(2_500.0, result.simpleTotal, 1e-9)
        assertEquals(0.0, result.compoundInterest, 1e-9)
        assertEquals(2_500.0, result.compoundTotal, 1e-9)
    }

    @Test
    fun invalidInterestInputsClearInterestResult() {
        viewModel.onPrincipalChange("0")
        assertNull(viewModel.state.value.interestResult)

        viewModel.onPrincipalChange("1000")
        viewModel.onTermYearsChange("0")
        assertNull(viewModel.state.value.interestResult)

        viewModel.onTermYearsChange("101")
        assertNull(viewModel.state.value.interestResult)

        viewModel.onTermYearsChange("")
        assertNull(viewModel.state.value.interestResult)
    }

    @Test
    fun taxCalculationAddsVatWhenTaxNotIncluded() {
        viewModel.onTaxIncludedChange(false)
        viewModel.onTaxBaseChange("1000")
        viewModel.onTaxRateChange("15")

        val tax = viewModel.state.value.taxResult
        assertNotNull(tax)
        assertEquals(1_000.0, tax!!.baseAmount, 1e-6)
        assertEquals(150.0, tax.taxAmount, 1e-6)
        assertEquals(1_150.0, tax.totalAmount, 1e-6)
    }

    @Test
    fun taxCalculationExtractsIncludedVatAndMatchesRoundTrip() {
        viewModel.onTaxIncludedChange(true)
        viewModel.onTaxBaseChange("1150")
        viewModel.onTaxRateChange("15")

        val tax = viewModel.state.value.taxResult
        assertNotNull(tax)
        assertEquals(1_000.0, tax!!.baseAmount, 1e-6)
        assertEquals(150.0, tax.taxAmount, 1e-6)
        assertEquals(1_150.0, tax.totalAmount, 1e-6)
    }

    @Test
    fun zeroPercentVatLeavesAmountUnchangedInBothModes() {
        viewModel.onTaxBaseChange("500")
        viewModel.onTaxRateChange("0")

        viewModel.onTaxIncludedChange(false)
        val added = viewModel.state.value.taxResult!!
        assertEquals(500.0, added.baseAmount, 1e-9)
        assertEquals(0.0, added.taxAmount, 1e-9)
        assertEquals(500.0, added.totalAmount, 1e-9)

        viewModel.onTaxIncludedChange(true)
        val extracted = viewModel.state.value.taxResult!!
        assertEquals(500.0, extracted.baseAmount, 1e-9)
        assertEquals(0.0, extracted.taxAmount, 1e-9)
        assertEquals(500.0, extracted.totalAmount, 1e-9)
    }

    @Test
    fun blankTaxInputsClearTaxResultAndSanitizeNonNumericCharacters() {
        viewModel.onTaxBaseChange("abc12.34.56")
        assertEquals("12.3456", viewModel.state.value.taxBaseInput)

        viewModel.onTaxBaseChange("")
        assertNull(viewModel.state.value.taxResult)
    }
}
