package com.calcsuits.features.finance.ui.viewmodel

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.LoanSimulationMode
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.domain.usecase.CalculateLoanUseCase
import com.calcsuits.features.finance.ui.screens.ScheduleLayoutMode
import com.calcsuits.features.finance.ui.screens.resolveScheduleLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun initialLoanStateCalculatesDefault10000At12PercentFor3YearsWithComparisonReady() {
        val state = viewModel.state.value
        val loan = state.loanResult
        assertNotNull(loan)
        assertNotNull(state.loanComparison)
        assertEquals(LoanSimulationMode.INDIVIDUAL, state.loanSimulationMode)
        assertEquals(AmortizationSystem.FRENCH, loan!!.system)
        assertEquals(332.14, loan.monthlyPayment, 0.01)
        assertEquals(1_957.15, loan.totalInterest, 0.05)
        assertEquals(36, loan.schedule.size)
        assertEquals(0.0, loan.finalBalance, 1e-9)
        assertNotNull(loan.issuedAtMillis)
        assertFalse(state.downPaymentError)
    }

    @Test
    fun simulatorOnlyExposesFrenchAndGermanAndIgnoresLegacyAmericanSelection() {
        assertEquals(
            listOf(AmortizationSystem.FRENCH, AmortizationSystem.GERMAN),
            AmortizationSystem.simulatorSystems
        )

        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        assertEquals(AmortizationSystem.GERMAN, viewModel.state.value.amortizationSystem)
        assertEquals(AmortizationSystem.GERMAN, viewModel.state.value.loanResult!!.system)

        // Attempt to select retired AMERICAN system must be ignored by simulator ViewModel
        viewModel.onAmortizationSystemSelected(AmortizationSystem.AMERICAN)
        assertEquals(AmortizationSystem.GERMAN, viewModel.state.value.amortizationSystem)
        assertEquals(AmortizationSystem.GERMAN, viewModel.state.value.loanResult!!.system)
    }

    @Test
    fun switchingLoanSystemFrequencyTermUnitRateConventionCurrencyAndComparisonModeUpdatesState() {
        viewModel.onCurrencySelected(FinanceCurrencies.NIO)
        viewModel.onAmountChange("600000")
        viewModel.onInterestRateChange("36")
        viewModel.onPaymentFrequencySelected(PaymentFrequency.QUARTERLY)
        viewModel.onLoanTermUnitSelected(LoanTermUnit.YEARS)
        viewModel.onYearsChange("2")

        // Verify French in Individual mode (INATEC case)
        viewModel.onAmortizationSystemSelected(AmortizationSystem.FRENCH)
        val french = viewModel.state.value.loanResult!!
        assertEquals(FinanceCurrencies.NIO, french.currency)
        assertEquals(AmortizationSystem.FRENCH, french.system)
        assertEquals(108_404.63, french.monthlyPayment, 1e-6)
        assertEquals(8, french.schedule.size)

        // Switch to German (INATEC case)
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        val german = viewModel.state.value.loanResult!!
        assertEquals(AmortizationSystem.GERMAN, german.system)
        assertEquals(75_000.0, german.constantPrincipalPerPeriod!!, 1e-6)
        assertEquals(243_000.0, german.totalInterest, 1e-6)
        assertEquals(843_000.0, german.totalPayment, 1e-6)

        // Switch to Comparison mode
        viewModel.onLoanSimulationModeSelected(LoanSimulationMode.COMPARISON)
        val comparison = viewModel.state.value.loanComparison!!
        assertEquals(LoanSimulationMode.COMPARISON, viewModel.state.value.loanSimulationMode)
        assertEquals(french.totalInterest, comparison.french.totalInterest, 1e-6)
        assertEquals(german.totalInterest, comparison.german.totalInterest, 1e-6)
        assertTrue(comparison.interestDifference > 0.0)
        assertEquals(comparison.interestDifference, comparison.totalPaymentDifference, 1e-6)

        // Switching to Effective Annual Rate lowers periodic rate vs Nominal TNA/4
        viewModel.onRateConventionSelected(RateConvention.EFFECTIVE_ANNUAL)
        val earGerman = viewModel.state.value.loanResult!!
        assertTrue(earGerman.totalInterest < german.totalInterest)

        // Switching currency to EUR updates result currency without altering system or term
        viewModel.onCurrencySelected(FinanceCurrencies.EUR)
        assertEquals(FinanceCurrencies.EUR, viewModel.state.value.loanResult!!.currency)
        assertEquals(FinanceCurrencies.EUR, viewModel.state.value.loanComparison!!.french.currency)
    }

    @Test
    fun downPaymentReducesFinancedPrincipalAndFlagsInvalidValuesEqualOrGreaterThanAmount() {
        viewModel.onAmountChange("100000")
        viewModel.onDownPaymentChange("25000")

        val validState = viewModel.state.value
        assertFalse(validState.downPaymentError)
        assertNotNull(validState.loanResult)
        assertEquals(100_000.0, validState.loanResult!!.requestedAmount, 1e-6)
        assertEquals(25_000.0, validState.loanResult!!.downPayment, 1e-6)
        assertEquals(75_000.0, validState.loanResult!!.principalAmount, 1e-6)
        assertEquals(75_000.0, validState.loanComparison!!.german.principalAmount, 1e-6)

        // Down payment equal to loan amount -> error and null results
        viewModel.onDownPaymentChange("100000")
        assertTrue(viewModel.state.value.downPaymentError)
        assertNull(viewModel.state.value.loanResult)
        assertNull(viewModel.state.value.loanComparison)

        // Down payment greater than loan amount -> error and null results
        viewModel.onDownPaymentChange("120000")
        assertTrue(viewModel.state.value.downPaymentError)
        assertNull(viewModel.state.value.loanResult)

        // Clearing down payment restores full principal
        viewModel.onDownPaymentChange("")
        assertFalse(viewModel.state.value.downPaymentError)
        assertEquals(100_000.0, viewModel.state.value.loanResult!!.principalAmount, 1e-6)
        assertEquals(0.0, viewModel.state.value.loanResult!!.downPayment, 1e-6)
    }

    @Test
    fun responsiveScheduleBreakpointResolverMatchesCompactMediumAndExpandedThresholds() {
        assertEquals(ScheduleLayoutMode.COMPACT_CARDS, resolveScheduleLayoutMode(360f))
        assertEquals(ScheduleLayoutMode.COMPACT_CARDS, resolveScheduleLayoutMode(412f))
        assertEquals(ScheduleLayoutMode.COMPACT_CARDS, resolveScheduleLayoutMode(599.9f))
        assertEquals(ScheduleLayoutMode.MEDIUM_COMPACT, resolveScheduleLayoutMode(600f))
        assertEquals(ScheduleLayoutMode.MEDIUM_COMPACT, resolveScheduleLayoutMode(720f))
        assertEquals(ScheduleLayoutMode.MEDIUM_COMPACT, resolveScheduleLayoutMode(839.9f))
        assertEquals(ScheduleLayoutMode.EXPANDED_TABLE, resolveScheduleLayoutMode(840f))
        assertEquals(ScheduleLayoutMode.EXPANDED_TABLE, resolveScheduleLayoutMode(1024f))
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
