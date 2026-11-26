package com.calcsuits.features.finance.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatRatePercent
import java.util.Locale

@Composable
internal fun LoanSection(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    var showSchedule by remember { mutableStateOf(false) }
    var showAllPeriods by remember { mutableStateOf(false) }
    val result = state.loanResult
    val visibleRows = remember(result, showSchedule, showAllPeriods) {
        if (!showSchedule || result == null) {
            emptyList()
        } else if (showAllPeriods) {
            result.schedule
        } else {
            result.schedule.take(12)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxHeight < 200.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LoanParametersCard(
                    state = state,
                    locale = locale,
                    onCurrencySelected = onCurrencySelected,
                    onAmortizationSystemSelected = onAmortizationSystemSelected,
                    onPaymentFrequencySelected = onPaymentFrequencySelected,
                    onLoanTermUnitSelected = onLoanTermUnitSelected,
                    onRateConventionSelected = onRateConventionSelected,
                    onAmountChange = onAmountChange,
                    onInterestRateChange = onInterestRateChange,
                    onYearsChange = onYearsChange
                )
                if (result != null) {
                    LoanSummaryCard(result = result, locale = locale)
                    ScheduleToggleButton(
                        scheduleSize = result.schedule.size,
                        showSchedule = showSchedule,
                        onToggle = { showSchedule = !showSchedule }
                    )
                    if (showSchedule) {
                        ScheduleHeaderCard(result = result)
                        visibleRows.forEach { row ->
                            ScheduleRowItem(row = row, currency = result.currency, locale = locale)
                        }
                        ScheduleFooterCard(
                            result = result,
                            locale = locale,
                            showAllPeriods = showAllPeriods,
                            onToggleShowAll = { showAllPeriods = !showAllPeriods }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "loan_params") {
                    LoanParametersCard(
                        state = state,
                        locale = locale,
                        onCurrencySelected = onCurrencySelected,
                        onAmortizationSystemSelected = onAmortizationSystemSelected,
                        onPaymentFrequencySelected = onPaymentFrequencySelected,
                        onLoanTermUnitSelected = onLoanTermUnitSelected,
                        onRateConventionSelected = onRateConventionSelected,
                        onAmountChange = onAmountChange,
                        onInterestRateChange = onInterestRateChange,
                        onYearsChange = onYearsChange
                    )
                }
                if (result != null) {
                    item(key = "loan_summary") {
                        LoanSummaryCard(result = result, locale = locale)
                    }
                    item(key = "loan_schedule_toggle") {
                        ScheduleToggleButton(
                            scheduleSize = result.schedule.size,
                            showSchedule = showSchedule,
                            onToggle = { showSchedule = !showSchedule }
                        )
                    }
                    if (showSchedule) {
                        item(key = "loan_schedule_header") {
                            ScheduleHeaderCard(result = result)
                        }
                        items(
                            items = visibleRows,
                            key = { row -> "loan_row_${row.period}" }
                        ) { row ->
                            ScheduleRowItem(row = row, currency = result.currency, locale = locale)
                        }
                        item(key = "loan_schedule_footer") {
                            ScheduleFooterCard(
                                result = result,
                                locale = locale,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanParametersCard(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
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
                    text = stringResource(R.string.finance_loan_params),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CurrencySelectorPill(
                    selected = state.selectedCurrency,
                    onSelected = onCurrencySelected
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.finance_system_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AmortizationSystem.entries.forEachIndexed { index, system ->
                        SegmentedButton(
                            selected = state.amortizationSystem == system,
                            onClick = { onAmortizationSystemSelected(system) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AmortizationSystem.entries.size
                            ),
                            colors = calcSegmentedButtonColors(),
                            modifier = Modifier.testTag("loan_system_${system.name.lowercase(Locale.ROOT)}")
                        ) {
                            Text(
                                text = stringResource(system.labelRes()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(state.amortizationSystem.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun AmortizationSystem.labelRes(): Int = when (this) {
    AmortizationSystem.FRENCH -> R.string.finance_system_french
    AmortizationSystem.GERMAN -> R.string.finance_system_german
    AmortizationSystem.AMERICAN -> R.string.finance_system_american
}

@StringRes
private fun AmortizationSystem.descriptionRes(): Int = when (this) {
    AmortizationSystem.FRENCH -> R.string.finance_system_french_desc
    AmortizationSystem.GERMAN -> R.string.finance_system_german_desc
    AmortizationSystem.AMERICAN -> R.string.finance_system_american_desc
}

@StringRes
private fun PaymentFrequency.labelRes(): Int = when (this) {
    PaymentFrequency.MONTHLY -> R.string.finance_pay_freq_monthly
    PaymentFrequency.SEMIMONTHLY -> R.string.finance_pay_freq_semimonthly
    PaymentFrequency.QUARTERLY -> R.string.finance_pay_freq_quarterly
    PaymentFrequency.SEMIANNUAL -> R.string.finance_pay_freq_semiannual
    PaymentFrequency.ANNUAL -> R.string.finance_pay_freq_annual
}
