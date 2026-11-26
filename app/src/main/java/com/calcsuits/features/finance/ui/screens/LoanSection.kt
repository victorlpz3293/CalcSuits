package com.calcsuits.features.finance.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.AmortizationRow
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanSimulationMode
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatRatePercent
import java.util.Locale

/**
 * Responsive presentation mode for the amortization schedule based on available width.
 */
enum class ScheduleLayoutMode {
    /** Mobile phones (`< 600dp`): single-column expandable cards with zero horizontal scrolling. */
    COMPACT_CARDS,

    /** Foldables / medium widths (`600dp..839dp`): two-column expandable card layout. */
    MEDIUM_COMPACT,

    /** Tablets & wide screens (`>= 840dp`): full 6-column financial table with sticky header. */
    EXPANDED_TABLE
}

internal fun resolveScheduleLayoutMode(availableWidthDp: Float): ScheduleLayoutMode = when {
    availableWidthDp < 600f -> ScheduleLayoutMode.COMPACT_CARDS
    availableWidthDp < 840f -> ScheduleLayoutMode.MEDIUM_COMPACT
    else -> ScheduleLayoutMode.EXPANDED_TABLE
}

/**
 * Maximum number of installment rows composed eagerly when [LoanSection] runs inside an
 * unbounded outer `verticalScroll` container (e.g., compact-height IME/landscape fallback).
 * Prevents composing 360–1,200 rows synchronously in a single frame while preserving full
 * domain totals in the footer and full lazy virtualization whenever height is bounded.
 */
internal const val MAX_NON_LAZY_SCHEDULE_ROWS = 24

/**
 * Selects non-lazy [Column] rendering strictly when parent height constraints are unbounded
 * (e.g., when [CalcPinnedScaffold] switches to outer `verticalScroll` under compact vertical space)
 * or non-positive. When parent height is bounded and positive (including 100–200dp viewports under
 * soft keyboard IME), [LazyColumn] remains active so scrolling, scroll position, and 1,200-row
 * virtualization stay intact without creating an unscrollable dead-zone.
 */
internal fun shouldUseNonLazyLoanLayout(
    hasBoundedHeight: Boolean,
    maxHeightDp: Float
): Boolean = !hasBoundedHeight || maxHeightDp <= 0f

internal fun resolveVisibleScheduleRows(
    schedule: List<AmortizationRow>,
    showSchedule: Boolean,
    showAllPeriods: Boolean,
    useNonLazyLayout: Boolean
): List<AmortizationRow> {
    if (!showSchedule || schedule.isEmpty()) return emptyList()
    val requested = if (showAllPeriods) schedule else schedule.take(12)
    return if (useNonLazyLayout && requested.size > MAX_NON_LAZY_SCHEDULE_ROWS) {
        requested.take(MAX_NON_LAZY_SCHEDULE_ROWS)
    } else {
        requested
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LoanSection(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onLoanSimulationModeSelected: (LoanSimulationMode) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var showAllPeriods by rememberSaveable { mutableStateOf(false) }
    var expandedPeriods by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet<Int>()) }

    val activeResult = state.loanResult
    val comparison = state.loanComparison
    val isComparisonMode = state.loanSimulationMode == LoanSimulationMode.COMPARISON

    val toggleExpandedPeriod: (Int) -> Unit = { period ->
        expandedPeriods = if (period in expandedPeriods) {
            expandedPeriods - period
        } else {
            expandedPeriods + period
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = resolveScheduleLayoutMode(maxWidth.value)
        val useNonLazyLayout = shouldUseNonLazyLoanLayout(
            hasBoundedHeight = constraints.hasBoundedHeight,
            maxHeightDp = maxHeight.value
        )
        val visibleRows = remember(activeResult, showSchedule, showAllPeriods, useNonLazyLayout) {
            resolveVisibleScheduleRows(
                schedule = activeResult?.schedule.orEmpty(),
                showSchedule = showSchedule,
                showAllPeriods = showAllPeriods,
                useNonLazyLayout = useNonLazyLayout
            )
        }
        val pairedRows = remember(visibleRows) { visibleRows.chunked(2) }
        val nonLazyCappedRowCount = if (
            useNonLazyLayout &&
            showAllPeriods &&
            activeResult != null &&
            activeResult.schedule.size > MAX_NON_LAZY_SCHEDULE_ROWS
        ) {
            MAX_NON_LAZY_SCHEDULE_ROWS
        } else {
            null
        }

        if (useNonLazyLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LoanParametersCard(
                    state = state,
                    locale = locale,
                    onCurrencySelected = onCurrencySelected,
                    onLoanSimulationModeSelected = onLoanSimulationModeSelected,
                    onAmortizationSystemSelected = onAmortizationSystemSelected,
                    onPaymentFrequencySelected = onPaymentFrequencySelected,
                    onLoanTermUnitSelected = onLoanTermUnitSelected,
                    onRateConventionSelected = onRateConventionSelected,
                    onAmountChange = onAmountChange,
                    onDownPaymentChange = onDownPaymentChange,
                    onInterestRateChange = onInterestRateChange,
                    onYearsChange = onYearsChange
                )
                if (activeResult != null) {
                    if (isComparisonMode && comparison != null) {
                        LoanComparisonCard(comparison = comparison, locale = locale)
                    } else {
                        LoanSummaryCard(result = activeResult, locale = locale)
                    }
                    LoanChartsCard(
                        result = activeResult,
                        comparison = comparison,
                        locale = locale,
                        preferComparisonTab = isComparisonMode
                    )
                    if (isComparisonMode) {
                        ComparisonScheduleSystemSelector(
                            selectedSystem = state.amortizationSystem,
                            onSelected = onAmortizationSystemSelected
                        )
                    }
                    ScheduleToggleButton(
                        scheduleSize = activeResult.schedule.size,
                        showSchedule = showSchedule,
                        onToggle = { showSchedule = !showSchedule }
                    )
                    if (showSchedule) {
                        when (layoutMode) {
                            ScheduleLayoutMode.EXPANDED_TABLE -> {
                                // Contiguous table unit: zero vertical gaps between header, rows, and footer
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ExpandedScheduleTableHeader(result = activeResult)
                                    visibleRows.forEachIndexed { idx, row ->
                                        ExpandedScheduleTableRow(
                                            row = row,
                                            currency = activeResult.currency,
                                            locale = locale,
                                            isAlternate = idx % 2 == 1
                                        )
                                    }
                                    ScheduleTotalsFooterCard(
                                        result = activeResult,
                                        locale = locale,
                                        layoutMode = layoutMode,
                                        showAllPeriods = showAllPeriods,
                                        onToggleShowAll = { showAllPeriods = !showAllPeriods },
                                        nonLazyCappedRowCount = nonLazyCappedRowCount
                                    )
                                }
                            }
                            ScheduleLayoutMode.MEDIUM_COMPACT -> {
                                ScheduleCaptionBanner(result = activeResult, isCardLayout = true)
                                pairedRows.forEach { pair ->
                                    InstallmentCardPairRow(
                                        left = pair[0],
                                        right = pair.getOrNull(1),
                                        currency = activeResult.currency,
                                        locale = locale,
                                        expandedPeriods = expandedPeriods,
                                        onTogglePeriod = toggleExpandedPeriod
                                    )
                                }
                                ScheduleTotalsFooterCard(
                                    result = activeResult,
                                    locale = locale,
                                    layoutMode = layoutMode,
                                    showAllPeriods = showAllPeriods,
                                    onToggleShowAll = { showAllPeriods = !showAllPeriods },
                                    nonLazyCappedRowCount = nonLazyCappedRowCount
                                )
                            }
                            ScheduleLayoutMode.COMPACT_CARDS -> {
                                ScheduleCaptionBanner(result = activeResult, isCardLayout = true)
                                visibleRows.forEach { row ->
                                    InstallmentExpandableCard(
                                        row = row,
                                        currency = activeResult.currency,
                                        locale = locale,
                                        expanded = row.period in expandedPeriods,
                                        onToggleExpand = { toggleExpandedPeriod(row.period) }
                                    )
                                }
                                ScheduleTotalsFooterCard(
                                    result = activeResult,
                                    locale = locale,
                                    layoutMode = layoutMode,
                                    showAllPeriods = showAllPeriods,
                                    onToggleShowAll = { showAllPeriods = !showAllPeriods },
                                    nonLazyCappedRowCount = nonLazyCappedRowCount
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else {
            // Spacing is applied per-item so EXPANDED_TABLE header, rows, and totals footer render contiguously (0dp gap).
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "loan_params") {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        LoanParametersCard(
                            state = state,
                            locale = locale,
                            onCurrencySelected = onCurrencySelected,
                            onLoanSimulationModeSelected = onLoanSimulationModeSelected,
                            onAmortizationSystemSelected = onAmortizationSystemSelected,
                            onPaymentFrequencySelected = onPaymentFrequencySelected,
                            onLoanTermUnitSelected = onLoanTermUnitSelected,
                            onRateConventionSelected = onRateConventionSelected,
                            onAmountChange = onAmountChange,
                            onDownPaymentChange = onDownPaymentChange,
                            onInterestRateChange = onInterestRateChange,
                            onYearsChange = onYearsChange
                        )
                    }
                }
                if (activeResult != null) {
                    if (isComparisonMode && comparison != null) {
                        item(key = "loan_comparison") {
                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                LoanComparisonCard(comparison = comparison, locale = locale)
                            }
                        }
                    } else {
                        item(key = "loan_summary") {
                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                LoanSummaryCard(result = activeResult, locale = locale)
                            }
                        }
                    }

                    item(key = "loan_charts") {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            LoanChartsCard(
                                result = activeResult,
                                comparison = comparison,
                                locale = locale,
                                preferComparisonTab = isComparisonMode
                            )
                        }
                    }

                    if (isComparisonMode) {
                        item(key = "loan_comparison_schedule_selector") {
                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                ComparisonScheduleSystemSelector(
                                    selectedSystem = state.amortizationSystem,
                                    onSelected = onAmortizationSystemSelected
                                )
                            }
                        }
                    }

                    item(key = "loan_schedule_toggle") {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            ScheduleToggleButton(
                                scheduleSize = activeResult.schedule.size,
                                showSchedule = showSchedule,
                                onToggle = { showSchedule = !showSchedule }
                            )
                        }
                    }

                    if (showSchedule) {
                        when (layoutMode) {
                            ScheduleLayoutMode.EXPANDED_TABLE -> {
                                stickyHeader(key = "loan_schedule_table_header") {
                                    ExpandedScheduleTableHeader(result = activeResult)
                                }
                                itemsIndexed(
                                    items = visibleRows,
                                    key = { _, row -> "loan_table_${activeResult.system.name}_${row.period}" }
                                ) { idx, row ->
                                    ExpandedScheduleTableRow(
                                        row = row,
                                        currency = activeResult.currency,
                                        locale = locale,
                                        isAlternate = idx % 2 == 1
                                    )
                                }
                                item(key = "loan_schedule_footer") {
                                    ScheduleTotalsFooterCard(
                                        result = activeResult,
                                        locale = locale,
                                        layoutMode = layoutMode,
                                        showAllPeriods = showAllPeriods,
                                        onToggleShowAll = { showAllPeriods = !showAllPeriods }
                                    )
                                }
                            }
                            ScheduleLayoutMode.MEDIUM_COMPACT -> {
                                item(key = "loan_schedule_caption") {
                                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                        ScheduleCaptionBanner(result = activeResult, isCardLayout = true)
                                    }
                                }
                                items(
                                    items = pairedRows,
                                    key = { pair -> "loan_pair_${activeResult.system.name}_${pair.first().period}" }
                                ) { pair ->
                                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                        InstallmentCardPairRow(
                                            left = pair[0],
                                            right = pair.getOrNull(1),
                                            currency = activeResult.currency,
                                            locale = locale,
                                            expandedPeriods = expandedPeriods,
                                            onTogglePeriod = toggleExpandedPeriod
                                        )
                                    }
                                }
                                item(key = "loan_schedule_footer") {
                                    ScheduleTotalsFooterCard(
                                        result = activeResult,
                                        locale = locale,
                                        layoutMode = layoutMode,
                                        showAllPeriods = showAllPeriods,
                                        onToggleShowAll = { showAllPeriods = !showAllPeriods }
                                    )
                                }
                            }
                            ScheduleLayoutMode.COMPACT_CARDS -> {
                                item(key = "loan_schedule_caption") {
                                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                        ScheduleCaptionBanner(result = activeResult, isCardLayout = true)
                                    }
                                }
                                items(
                                    items = visibleRows,
                                    key = { row -> "loan_card_${activeResult.system.name}_${row.period}" }
                                ) { row ->
                                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                        InstallmentExpandableCard(
                                            row = row,
                                            currency = activeResult.currency,
                                            locale = locale,
                                            expanded = row.period in expandedPeriods,
                                            onToggleExpand = { toggleExpandedPeriod(row.period) }
                                        )
                                    }
                                }
                                item(key = "loan_schedule_footer") {
                                    ScheduleTotalsFooterCard(
                                        result = activeResult,
                                        locale = locale,
                                        layoutMode = layoutMode,
                                        showAllPeriods = showAllPeriods,
                                        onToggleShowAll = { showAllPeriods = !showAllPeriods }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonScheduleSystemSelector(
    selectedSystem: AmortizationSystem,
    onSelected: (AmortizationSystem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.finance_comparison_schedule_selector),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AmortizationSystem.simulatorSystems.forEachIndexed { index, system ->
                SegmentedButton(
                    selected = selectedSystem == system,
                    onClick = { onSelected(system) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AmortizationSystem.simulatorSystems.size
                    ),
                    colors = calcSegmentedButtonColors(),
                    modifier = Modifier.testTag("loan_comparison_schedule_${system.name.lowercase(Locale.ROOT)}")
                ) {
                    Text(
                        text = stringResource(system.academicSubtitleRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanParametersCard(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onLoanSimulationModeSelected: (LoanSimulationMode) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    val currencyPrefix = "${state.selectedCurrency.symbol} "

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.finance_loan_simulator_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CurrencySelectorPill(
                    selected = state.selectedCurrency,
                    onSelected = onCurrencySelected
                )
            }

            // Simulation mode: Individual vs Comparison
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.finance_simulation_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LoanSimulationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.loanSimulationMode == mode,
                            onClick = { onLoanSimulationModeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LoanSimulationMode.entries.size
                            ),
                            colors = calcSegmentedButtonColors(),
                            modifier = Modifier.testTag("loan_mode_${mode.name.lowercase(Locale.ROOT)}")
                        ) {
                            Text(
                                text = stringResource(
                                    if (mode == LoanSimulationMode.INDIVIDUAL) {
                                        R.string.finance_mode_individual
                                    } else {
                                        R.string.finance_mode_comparison
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Amortization system selector (only French & German; visible in Individual mode)
            if (state.loanSimulationMode == LoanSimulationMode.INDIVIDUAL) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.finance_system_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AmortizationSystem.simulatorSystems.forEach { system ->
                            val selected = state.amortizationSystem == system
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("loan_system_${system.name.lowercase(Locale.ROOT)}")
                                    .clickable { onAmortizationSystemSelected(system) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(system.descriptiveTitleRes()),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(system.academicSubtitleRes()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(state.amortizationSystem.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.finance_loan_principal)) },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loan_amount_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            OutlinedTextField(
                value = state.downPayment,
                onValueChange = onDownPaymentChange,
                label = { Text(stringResource(R.string.finance_loan_down_payment_optional)) },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                isError = state.downPaymentError,
                supportingText = {
                    if (state.downPaymentError) {
                        Text(
                            text = stringResource(R.string.finance_loan_down_payment_error),
                            modifier = Modifier.testTag("loan_down_payment_error")
                        )
                    } else if (state.loanResult != null && state.loanResult.downPayment > 0.0) {
                        Text(
                            text = stringResource(
                                R.string.finance_loan_financed_principal_inline,
                                formatMoney(state.loanResult.principalAmount, state.selectedCurrency, locale)
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("loan_financed_principal_hint")
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loan_down_payment_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.interestRate,
                    onValueChange = onInterestRateChange,
                    label = { Text(stringResource(R.string.finance_annual_rate)) },
                    suffix = { Text("%") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("loan_rate_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.years,
                    onValueChange = onYearsChange,
                    label = {
                        Text(
                            stringResource(
                                if (state.loanTermUnit == LoanTermUnit.YEARS) {
                                    R.string.finance_term_years
                                } else {
                                    R.string.finance_term_periods
                                }
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("loan_years_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LoanTermUnit.entries.forEach { unit ->
                    CalcFilterChip(
                        selected = state.loanTermUnit == unit,
                        onClick = { onLoanTermUnitSelected(unit) },
                        label = {
                            Text(
                                text = stringResource(
                                    if (unit == LoanTermUnit.YEARS) {
                                        R.string.finance_term_unit_years
                                    } else {
                                        R.string.finance_term_unit_periods
                                    }
                                ),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("loan_term_unit_${unit.name.lowercase(Locale.ROOT)}")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.finance_payment_frequency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentFrequency.entries.forEach { freq ->
                        CalcFilterChip(
                            selected = state.paymentFrequency == freq,
                            onClick = { onPaymentFrequencySelected(freq) },
                            label = {
                                Text(
                                    text = stringResource(freq.labelRes()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("loan_pay_freq_${freq.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.finance_rate_convention_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RateConvention.entries.forEach { convention ->
                        CalcFilterChip(
                            selected = state.rateConvention == convention,
                            onClick = { onRateConventionSelected(convention) },
                            label = {
                                Text(
                                    text = stringResource(
                                        if (convention == RateConvention.NOMINAL_PER_PAYMENT_PERIOD) {
                                            R.string.finance_rate_nominal
                                        } else {
                                            R.string.finance_rate_effective
                                        }
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("loan_rate_conv_${convention.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }

                state.loanResult?.let { res ->
                    val periodicPct = formatRatePercent(res.periodicRate * 100.0, locale, maxFraction = 4)
                    val earPct = formatRatePercent(res.effectiveAnnualRate * 100.0, locale, maxFraction = 4)
                    val noteText = if (state.rateConvention == RateConvention.NOMINAL_PER_PAYMENT_PERIOD) {
                        stringResource(
                            R.string.finance_rate_note_nominal,
                            res.paymentFrequency.periodsPerYear,
                            periodicPct,
                            earPct
                        )
                    } else {
                        stringResource(
                            R.string.finance_rate_note_effective,
                            res.paymentFrequency.periodsPerYear,
                            periodicPct
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = noteText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("loan_rate_assumption_note")
                            )
                        }
                    }
                }
            }
        }
    }
}

@StringRes
internal fun AmortizationSystem.descriptiveTitleRes(): Int = when (this) {
    AmortizationSystem.GERMAN -> R.string.finance_system_german_title
    else -> R.string.finance_system_french_title
}

@StringRes
internal fun AmortizationSystem.academicSubtitleRes(): Int = when (this) {
    AmortizationSystem.GERMAN -> R.string.finance_system_german_academic
    else -> R.string.finance_system_french_academic
}

@StringRes
internal fun AmortizationSystem.descriptionRes(): Int = when (this) {
    AmortizationSystem.GERMAN -> R.string.finance_system_german_desc
    else -> R.string.finance_system_french_desc
}

@StringRes
internal fun PaymentFrequency.labelRes(): Int = when (this) {
    PaymentFrequency.MONTHLY -> R.string.finance_pay_freq_monthly
    PaymentFrequency.SEMIMONTHLY -> R.string.finance_pay_freq_semimonthly
    PaymentFrequency.QUARTERLY -> R.string.finance_pay_freq_quarterly
    PaymentFrequency.SEMIANNUAL -> R.string.finance_pay_freq_semiannual
    PaymentFrequency.ANNUAL -> R.string.finance_pay_freq_annual
}

@StringRes
internal fun PaymentFrequency.shortLabelRes(): Int = when (this) {
    PaymentFrequency.MONTHLY -> R.string.finance_pay_freq_short_monthly
    PaymentFrequency.SEMIMONTHLY -> R.string.finance_pay_freq_short_semimonthly
    PaymentFrequency.QUARTERLY -> R.string.finance_pay_freq_short_quarterly
    PaymentFrequency.SEMIANNUAL -> R.string.finance_pay_freq_short_semiannual
    PaymentFrequency.ANNUAL -> R.string.finance_pay_freq_short_annual
}

@StringRes
internal fun PaymentFrequency.installmentNounRes(): Int = when (this) {
    PaymentFrequency.MONTHLY -> R.string.finance_installment_monthly
    PaymentFrequency.SEMIMONTHLY -> R.string.finance_installment_semimonthly
    PaymentFrequency.QUARTERLY -> R.string.finance_installment_quarterly
    PaymentFrequency.SEMIANNUAL -> R.string.finance_installment_semiannual
    PaymentFrequency.ANNUAL -> R.string.finance_installment_annual
}

@StringRes
internal fun PaymentFrequency.installmentContextualNounRes(): Int = when (this) {
    PaymentFrequency.MONTHLY -> R.string.finance_installment_contextual_monthly
    PaymentFrequency.SEMIMONTHLY -> R.string.finance_installment_contextual_semimonthly
    PaymentFrequency.QUARTERLY -> R.string.finance_installment_contextual_quarterly
    PaymentFrequency.SEMIANNUAL -> R.string.finance_installment_contextual_semiannual
    PaymentFrequency.ANNUAL -> R.string.finance_installment_contextual_annual
}
