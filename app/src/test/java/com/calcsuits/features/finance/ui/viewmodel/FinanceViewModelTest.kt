package com.calcsuits.features.finance.ui.viewmodel

import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateCompoundingFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.domain.usecase.CalculateLoanUseCase
import com.calcsuits.features.finance.ui.screens.MAX_NON_LAZY_SCHEDULE_ROWS
import com.calcsuits.features.finance.ui.screens.ScheduleLayoutMode
import com.calcsuits.features.finance.ui.screens.resolveScheduleLayoutMode
import com.calcsuits.features.finance.ui.screens.resolveVisibleScheduleRows
import com.calcsuits.features.finance.ui.screens.shouldUseNonLazyLoanLayout
import com.calcsuits.ui.components.shouldUseCompactPinnedLayout
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
    fun financeStartsOnThreeCardHubAndNavigatesToEachToolAndBackPreservingInputs() {
        assertEquals(
            listOf(FinanceTab.LOAN, FinanceTab.INTEREST, FinanceTab.TAX),
            FinanceTab.entries
        )
        assertNull(viewModel.state.value.selectedTab)

        viewModel.onTabSelected(FinanceTab.LOAN)
        assertEquals(FinanceTab.LOAN, viewModel.state.value.selectedTab)
        viewModel.onAmountChange("45000")
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        viewModel.onBackToHub()
        assertNull(viewModel.state.value.selectedTab)

        viewModel.onTabSelected(FinanceTab.INTEREST)
        assertEquals(FinanceTab.INTEREST, viewModel.state.value.selectedTab)
        viewModel.onPrincipalChange("12000")
        viewModel.onBackToHub()
        assertNull(viewModel.state.value.selectedTab)

        viewModel.onTabSelected(FinanceTab.TAX)
        assertEquals(FinanceTab.TAX, viewModel.state.value.selectedTab)
        viewModel.onTaxBaseChange("850")
        viewModel.onTaxRateChange("15")
        viewModel.onBackToHub()
        assertNull(viewModel.state.value.selectedTab)

        // Returning preserves all entered values across all 3 tools
        val state = viewModel.state.value
        assertEquals("45000", state.amount)
        assertEquals(AmortizationSystem.GERMAN, state.amortizationSystem)
        assertEquals("12000", state.principal)
        assertEquals("850", state.taxBaseInput)
        assertEquals("15", state.taxRatePercent)
    }

    @Test
    fun initialLoanStateCalculatesDefault10000At12PercentFor3YearsInFrenchSystem() {
        val state = viewModel.state.value
        val loan = state.loanResult
        assertNotNull(loan)
        assertEquals(AmortizationSystem.FRENCH, state.amortizationSystem)
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
    fun switchingFrenchAndGermanTabsRecalculatesIndependentlyWithoutMixingResults() {
        viewModel.onTabSelected(FinanceTab.LOAN)
        viewModel.onCurrencySelected(FinanceCurrencies.NIO)
        viewModel.onAmountChange("600000")
        viewModel.onInterestRateChange("36")
        viewModel.onPaymentFrequencySelected(PaymentFrequency.QUARTERLY)
        viewModel.onLoanTermUnitSelected(LoanTermUnit.YEARS)
        viewModel.onYearsChange("2")

        // Verify French tab (INATEC case)
        viewModel.onAmortizationSystemSelected(AmortizationSystem.FRENCH)
        val french = viewModel.state.value.loanResult!!
        assertEquals(FinanceCurrencies.NIO, french.currency)
        assertEquals(AmortizationSystem.FRENCH, french.system)
        assertEquals(108_404.63, french.monthlyPayment, 1e-6)
        assertEquals(8, french.schedule.size)
        assertNull(french.constantPrincipalPerPeriod)

        // Switch to German tab (INATEC case) — inputs preserved, independent German result computed
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        val german = viewModel.state.value.loanResult!!
        assertEquals(AmortizationSystem.GERMAN, german.system)
        assertEquals(75_000.0, german.constantPrincipalPerPeriod!!, 1e-6)
        assertEquals(243_000.0, german.totalInterest, 1e-6)
        assertEquals(843_000.0, german.totalPayment, 1e-6)
        assertTrue(german.totalInterest < french.totalInterest)

        // Switch back to French tab — exact French figures restored without leaking German fields
        viewModel.onAmortizationSystemSelected(AmortizationSystem.FRENCH)
        val frenchAgain = viewModel.state.value.loanResult!!
        assertEquals(AmortizationSystem.FRENCH, frenchAgain.system)
        assertEquals(french.monthlyPayment, frenchAgain.monthlyPayment, 1e-6)
        assertEquals(french.totalInterest, frenchAgain.totalInterest, 1e-6)
        assertNull(frenchAgain.constantPrincipalPerPeriod)

        // Switching to Effective Annual Rate lowers periodic rate vs Nominal TNA/4
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        viewModel.onRateConventionSelected(RateConvention.EFFECTIVE_ANNUAL)
        val earGerman = viewModel.state.value.loanResult!!
        assertTrue(earGerman.totalInterest < german.totalInterest)

        // Switching currency to EUR updates result currency without altering system or term
        viewModel.onCurrencySelected(FinanceCurrencies.EUR)
        assertEquals(FinanceCurrencies.EUR, viewModel.state.value.loanResult!!.currency)
        assertEquals(AmortizationSystem.GERMAN, viewModel.state.value.amortizationSystem)
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

        // Switching to German keeps identical financed principal
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        assertEquals(75_000.0, viewModel.state.value.loanResult!!.principalAmount, 1e-6)

        // Down payment equal to loan amount -> error and null result
        viewModel.onDownPaymentChange("100000")
        assertTrue(viewModel.state.value.downPaymentError)
        assertNull(viewModel.state.value.loanResult)

        // Down payment greater than loan amount -> error and null result
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
    fun loanLayoutResolverUsesNonLazyOnlyWhenHeightIsUnboundedOrNonPositive() {
        assertTrue(shouldUseNonLazyLoanLayout(hasBoundedHeight = false, maxHeightDp = Float.POSITIVE_INFINITY))
        assertTrue(shouldUseNonLazyLoanLayout(hasBoundedHeight = false, maxHeightDp = 800f))
        assertTrue(shouldUseNonLazyLoanLayout(hasBoundedHeight = true, maxHeightDp = 0f))
        assertFalse(shouldUseNonLazyLoanLayout(hasBoundedHeight = true, maxHeightDp = 148f))
        assertFalse(shouldUseNonLazyLoanLayout(hasBoundedHeight = true, maxHeightDp = 199.9f))
        assertFalse(shouldUseNonLazyLoanLayout(hasBoundedHeight = true, maxHeightDp = 200f))
        assertFalse(shouldUseNonLazyLoanLayout(hasBoundedHeight = true, maxHeightDp = 640f))
    }

    @Test
    fun nonLazyScheduleRowResolverCapsEagerCompositionOnLongSchedulesWhileLazyKeepsAllRows() {
        val longLoan = CalculateLoanUseCase()(
            com.calcsuits.domain.model.finance.LoanRequest(
                amount = 500_000.0,
                annualRate = 9.5,
                termValue = 1_200,
                termUnit = LoanTermUnit.PERIODS,
                paymentFrequency = PaymentFrequency.MONTHLY,
                system = AmortizationSystem.FRENCH
            )
        )
        assertEquals(1_200, longLoan.schedule.size)

        assertEquals(
            1_200,
            resolveVisibleScheduleRows(
                schedule = longLoan.schedule,
                showSchedule = true,
                showAllPeriods = true,
                useNonLazyLayout = false
            ).size
        )
        val capped = resolveVisibleScheduleRows(
            schedule = longLoan.schedule,
            showSchedule = true,
            showAllPeriods = true,
            useNonLazyLayout = true
        )
        assertEquals(MAX_NON_LAZY_SCHEDULE_ROWS, capped.size)
        assertEquals(1, capped.first().period)
        assertEquals(MAX_NON_LAZY_SCHEDULE_ROWS, capped.last().period)

        assertEquals(
            12,
            resolveVisibleScheduleRows(
                schedule = longLoan.schedule,
                showSchedule = true,
                showAllPeriods = false,
                useNonLazyLayout = true
            ).size
        )
        assertTrue(
            resolveVisibleScheduleRows(
                schedule = longLoan.schedule,
                showSchedule = false,
                showAllPeriods = true,
                useNonLazyLayout = false
            ).isEmpty()
        )
    }

    @Test
    fun viewModelEmitsCanonicalUtcMidnightCivilDateForIssuedAtMillis() {
        val issuedAt = viewModel.state.value.loanResult!!.issuedAtMillis!!
        assertEquals(CalculateLoanUseCase.normalizeCivilDateUtcMillis(issuedAt), issuedAt)
        assertEquals(0L, issuedAt % 86_400_000L)
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
    fun taxCalculationAddsTaxDirectlyWithoutImposedDefaultRate() {
        // Default rate is 0% (no jurisdiction-specific legal default imposed)
        assertEquals("0", viewModel.state.value.taxRatePercent)

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

    @Test
    fun compoundingFrequencyCanBeConfiguredIndependentlyFromPaymentPeriodicityAndPreservesUserChoice() {
        assertEquals(PaymentFrequency.MONTHLY, viewModel.state.value.paymentFrequency)
        assertEquals(RateCompoundingFrequency.MONTHLY, viewModel.state.value.compoundingFrequency)
        assertTrue(viewModel.state.value.compoundingFollowsPayment)

        viewModel.onInterestRateChange("24")
        viewModel.onPaymentFrequencySelected(PaymentFrequency.QUARTERLY)
        assertEquals(RateCompoundingFrequency.QUARTERLY, viewModel.state.value.compoundingFrequency)
        assertEquals(0.06, viewModel.state.value.loanResult!!.periodicRate, 1e-12)

        viewModel.onCompoundingFrequencySelected(RateCompoundingFrequency.MONTHLY)
        val splitState = viewModel.state.value
        assertFalse(splitState.compoundingFollowsPayment)
        assertEquals(PaymentFrequency.QUARTERLY, splitState.paymentFrequency)
        assertEquals(RateCompoundingFrequency.MONTHLY, splitState.compoundingFrequency)
        assertEquals(0.061208, splitState.loanResult!!.periodicRate, 1e-12)
        assertEquals(0.268241794562545, splitState.loanResult!!.effectiveAnnualRate, 1e-12)

        viewModel.onPaymentFrequencySelected(PaymentFrequency.SEMIANNUAL)
        val updatedState = viewModel.state.value
        assertEquals(PaymentFrequency.SEMIANNUAL, updatedState.paymentFrequency)
        assertEquals(RateCompoundingFrequency.MONTHLY, updatedState.compoundingFrequency)
        assertEquals(0.126162419264, updatedState.loanResult!!.periodicRate, 1e-12)
    }

    @Test
    fun pinnedScaffoldCompactLayoutDecisionDependsOnlyOnViewportHeightNotImeInsets() {
        assertFalse(shouldUseCompactPinnedLayout(viewportHeightDp = 760f, compactHeightThresholdDp = 200f))
        assertFalse(shouldUseCompactPinnedLayout(viewportHeightDp = 300f, compactHeightThresholdDp = 200f))
        assertFalse(shouldUseCompactPinnedLayout(viewportHeightDp = 200f, compactHeightThresholdDp = 200f))
        assertTrue(shouldUseCompactPinnedLayout(viewportHeightDp = 199.9f, compactHeightThresholdDp = 200f))
        assertTrue(shouldUseCompactPinnedLayout(viewportHeightDp = 360f, compactHeightThresholdDp = 420f))
        assertFalse(shouldUseCompactPinnedLayout(viewportHeightDp = 760f, compactHeightThresholdDp = 420f))
    }

    @Test
    fun loanFieldStatePersistsAcrossHubNavigationSystemAndCurrencyChanges() {
        viewModel.onTabSelected(FinanceTab.LOAN)
        viewModel.onAmountChange("250000")
        viewModel.onDownPaymentChange("50000")
        viewModel.onInterestRateChange("18.5")
        viewModel.onYearsChange("5")
        viewModel.onPaymentFrequencySelected(PaymentFrequency.QUARTERLY)
        viewModel.onCompoundingFrequencySelected(RateCompoundingFrequency.MONTHLY)
        viewModel.onRateConventionSelected(RateConvention.NOMINAL_PER_PAYMENT_PERIOD)

        viewModel.onBackToHub()
        viewModel.onTabSelected(FinanceTab.INTEREST)
        viewModel.onBackToHub()
        viewModel.onTabSelected(FinanceTab.TAX)
        viewModel.onBackToHub()
        viewModel.onTabSelected(FinanceTab.LOAN)
        viewModel.onAmortizationSystemSelected(AmortizationSystem.GERMAN)
        viewModel.onCurrencySelected(FinanceCurrencies.NIO)

        val restored = viewModel.state.value
        assertEquals(FinanceTab.LOAN, restored.selectedTab)
        assertEquals("250000", restored.amount)
        assertEquals("50000", restored.downPayment)
        assertEquals("18.5", restored.interestRate)
        assertEquals("5", restored.years)
        assertEquals(PaymentFrequency.QUARTERLY, restored.paymentFrequency)
        assertEquals(RateCompoundingFrequency.MONTHLY, restored.compoundingFrequency)
        assertEquals(RateConvention.NOMINAL_PER_PAYMENT_PERIOD, restored.rateConvention)
        assertEquals(AmortizationSystem.GERMAN, restored.amortizationSystem)
        assertEquals(FinanceCurrencies.NIO, restored.selectedCurrency)
        assertNotNull(restored.loanResult)
        assertEquals(200_000.0, restored.loanResult!!.principalAmount, 1e-6)
    }
}
