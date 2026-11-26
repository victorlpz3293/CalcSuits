package com.calcsuits.features.finance.ui.viewmodel

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
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
        assertEquals(AmortizationSystem.FRENCH, loan!!.system)
        assertEquals(332.14, loan.monthlyPayment, 0.01)
        assertEquals(1_957.15, loan.totalInterest, 0.05)
        assertEquals(36, loan.schedule.size)
        assertEquals(0.0, loan.finalBalance, 1e-9)
    }

    @Test
    fun switchingLoanSystemFrequencyTermUnitRateConventionAndCurrencyUpdatesResult() {
        viewModel.onCurrencySelected(FinanceCurrencies.NIO)
        viewModel.onAmountChange("24000")
        viewModel.onInterestRateChange("12")
        viewModel.onLoanTermUnitSelected(LoanTermUnit.PERIODS)
        viewModel.onYearsChange("12")
        viewModel.onPaymentFrequencySelected(PaymentFrequency.MONTHLY)

        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        val german = viewModel.state.value.loanResult!!
        assertEquals(FinanceCurrencies.NIO, german.currency)
        assertEquals(AmortizationSystem.GERMAN, german.system)
        assertEquals(2_000.0, german.constantPrincipalPerPeriod!!, 1e-6)
        assertEquals(0.0, german.finalBalance, 1e-9)

        viewModel.onAmortizationSystemSelected(AmortizationSystem.AMERICAN)
        val american = viewModel.state.value.loanResult!!
        assertEquals(AmortizationSystem.AMERICAN, american.system)
        assertEquals(240.0, american.firstPayment, 1e-6)
        assertEquals(24_240.0, american.lastPayment, 1e-6)
        assertTrue(american.totalInterest > german.totalInterest)

        // Switching to effective annual rate reduces periodic rate vs nominal 12%/12
        viewModel.onRateConventionSelected(RateConvention.EFFECTIVE_ANNUAL)
        val americanEar = viewModel.state.value.loanResult!!
        assertTrue(americanEar.totalInterest < american.totalInterest)

        // Switching currency to EUR updates result currency without altering term or system
        viewModel.onCurrencySelected(FinanceCurrencies.EUR)
        assertEquals(FinanceCurrencies.EUR, viewModel.state.value.loanResult!!.currency)
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
    fun higherCompoundingFrequencyYieldsHigherCompoundInterestAcrossAllFrequencies() {
        viewModel.onPrincipalChange("5000")
        viewModel.onAnnualRateChange("8")
        viewModel.onTermYearsChange("5")

        viewModel.onFrequencyChange(CompoundingFrequency.ANNUAL)
        val annual = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.SEMIANNUAL)
        val semiannual = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.QUARTERLY)
        val quarterly = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.MONTHLY)
        val monthly = viewModel.state.value.interestResult!!.compoundTotal

        viewModel.onFrequencyChange(CompoundingFrequency.DAILY)
        val daily = viewModel.state.value.interestResult!!.compoundTotal

        assertTrue(annual < semiannual)
        assertTrue(semiannual < quarterly)
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
    fun taxCalculationAddsTaxWhenTaxNotIncluded() {
        viewModel.onTaxIncludedChange(false)
        viewModel.onTaxBaseChange("1000")
        viewModel.onTaxRateChange("15")

        val tax = viewModel.state.value.taxResult
        assertNotNull(tax)
        assertEquals(1_000.0, tax!!.baseAmount, 1e-6)
        assertEquals(15.0, tax.appliedRatePercent, 1e-9)
        assertEquals(150.0, tax.taxAmount, 1e-6)
        assertEquals(1_150.0, tax.totalAmount, 1e-6)
    }

    @Test
    fun taxCalculationExtractsIncludedTaxAndMatchesRoundTrip() {
        viewModel.onTaxIncludedChange(true)
        viewModel.onTaxBaseChange("1150")
        viewModel.onTaxRateChange("15")

        val tax = viewModel.state.value.taxResult
        assertNotNull(tax)
        assertEquals(1_000.0, tax!!.baseAmount, 1e-6)
        assertEquals(15.0, tax.appliedRatePercent, 1e-9)
        assertEquals(150.0, tax.taxAmount, 1e-6)
        assertEquals(1_150.0, tax.totalAmount, 1e-6)
    }

    @Test
    fun configurableFractionalTaxRatesPreserveExactBasePlusTaxEqualsTotal() {
        viewModel.onTaxIncludedChange(false)
        viewModel.onTaxBaseChange("250.50")
        viewModel.onTaxRateChange("7.5")

        val added = viewModel.state.value.taxResult!!
        assertEquals(250.50, added.baseAmount, 1e-6)
        assertEquals(7.5, added.appliedRatePercent, 1e-9)
        assertEquals(18.79, added.taxAmount, 1e-6)
        assertEquals(269.29, added.totalAmount, 1e-6)
        assertEquals(added.totalAmount, added.baseAmount + added.taxAmount, 1e-6)

        // Extract with configurable 13.25% rate
        viewModel.onTaxIncludedChange(true)
        viewModel.onTaxBaseChange("1000")
        viewModel.onTaxRateChange("13.25")
        val extracted = viewModel.state.value.taxResult!!
        assertEquals(13.25, extracted.appliedRatePercent, 1e-9)
        assertEquals(883.00, extracted.baseAmount, 1e-6)
        assertEquals(117.00, extracted.taxAmount, 1e-6)
        assertEquals(1_000.00, extracted.totalAmount, 1e-6)
    }

    @Test
    fun selectingTaxTypeDoesNotImposeOrOverwriteUserConfiguredRate() {
        viewModel.onTaxRateChange("8.25")
        viewModel.onTaxTypeSelected(TaxType.SALES)
        assertEquals("8.25", viewModel.state.value.taxRatePercent)
        assertEquals(TaxType.SALES, viewModel.state.value.taxType)

        viewModel.onTaxTypeSelected(TaxType.CUSTOM)
        viewModel.onCustomTaxLabelChange("Impuesto\nMunicipal")
        assertEquals("Impuesto Municipal", viewModel.state.value.customTaxLabel)
        assertEquals("8.25", viewModel.state.value.taxRatePercent)
    }

    @Test
    fun zeroPercentTaxLeavesAmountUnchangedInBothModes() {
        viewModel.onTaxBaseChange("500")
        viewModel.onTaxRateChange("0")

        viewModel.onTaxIncludedChange(false)
        val added = viewModel.state.value.taxResult!!
        assertEquals(500.0, added.baseAmount, 1e-9)
        assertEquals(0.0, added.appliedRatePercent, 1e-9)
        assertEquals(0.0, added.taxAmount, 1e-9)
        assertEquals(500.0, added.totalAmount, 1e-9)

        viewModel.onTaxIncludedChange(true)
        val extracted = viewModel.state.value.taxResult!!
        assertEquals(500.0, extracted.baseAmount, 1e-9)
        assertEquals(0.0, extracted.appliedRatePercent, 1e-9)
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
