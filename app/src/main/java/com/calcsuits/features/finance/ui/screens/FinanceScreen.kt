package com.calcsuits.features.finance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.features.finance.ui.viewmodel.CompoundingFrequency
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.features.finance.ui.viewmodel.FinanceTab
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.currencySymbolFor
import com.calcsuits.ui.util.formatCurrency
import com.calcsuits.ui.util.formatPercentInt
import com.calcsuits.ui.util.rememberCurrentLocale
import java.util.Locale

@Composable
fun FinanceScreen(
    state: FinanceState,
    onMenuClick: () -> Unit,
    onTabSelected: (FinanceTab) -> Unit,
    onAmountChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    val locale = rememberCurrentLocale()
    val scrollState = rememberScrollState()

    LaunchedEffect(state.selectedTab) {
        scrollState.scrollTo(0)
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance),
                subtitle = stringResource(state.selectedTab.labelRes).uppercase(locale),
                onMenuClick = onMenuClick
            )
        },
        pinnedPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        contentSpacing = 16.dp,
        scrollState = scrollState,
        compactHeightThreshold = 200.dp,
        pinnedContent = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FinanceTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FinanceTab.entries.size
                        ),
                        colors = calcSegmentedButtonColors(),
                        modifier = Modifier.testTag("finance_tab_${tab.name.lowercase(Locale.ROOT)}")
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) {
        when (state.selectedTab) {
            FinanceTab.LOAN -> LoanSection(
                state = state,
                locale = locale,
                onAmountChange = onAmountChange,
                onInterestRateChange = onInterestRateChange,
                onYearsChange = onYearsChange
            )
            FinanceTab.INTEREST -> InterestSection(
                state = state,
                locale = locale,
                onPrincipalChange = onPrincipalChange,
                onAnnualRateChange = onAnnualRateChange,
                onTermYearsChange = onTermYearsChange,
                onFrequencyChange = onFrequencyChange
            )
            FinanceTab.TAX -> TaxSection(
                state = state,
                locale = locale,
                onTaxBaseChange = onTaxBaseChange,
                onTaxRateChange = onTaxRateChange,
                onTaxIncludedChange = onTaxIncludedChange
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LoanSection(
    state: FinanceState,
    locale: Locale,
    onAmountChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    val currencyPrefix = remember(locale) { "${currencySymbolFor(locale)} " }
    var showSchedule by remember { mutableStateOf(false) }
    var showAllPeriods by remember { mutableStateOf(false) }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.finance_loan_params),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.finance_loan_principal)) },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth().testTag("loan_amount_input"),
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
                    modifier = Modifier.weight(1f).testTag("loan_rate_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.years,
                    onValueChange = onYearsChange,
                    label = { Text(stringResource(R.string.finance_term_years)) },
                    modifier = Modifier.weight(1f).testTag("loan_years_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
        }
    }

    state.loanResult?.let { result ->
        val capital = state.amount.toDoubleOrNull() ?: 1.0
        val principalRatio = (capital / result.totalPayment).toFloat().coerceIn(0.05f, 1f)

        CalcResultCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = stringResource(R.string.finance_monthly_payment_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatCurrency(result.monthlyPayment, locale),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(principalRatio)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        if (principalRatio < 0.999f) {
                            Box(
                                modifier = Modifier
                                    .weight((1f - principalRatio).coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(extColors.technicalAccent)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(
                            color = MaterialTheme.colorScheme.primary,
                            label = stringResource(
                                R.string.finance_legend_principal,
                                formatPercentInt(principalRatio.toDouble(), locale)
                            )
                        )
                        LegendDot(
                            color = extColors.technicalAccent,
                            label = stringResource(
                                R.string.finance_legend_interest,
                                formatPercentInt((1f - principalRatio).toDouble(), locale)
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_interest),
                    value = formatCurrency(result.totalInterest, locale)
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_payment),
                    value = formatCurrency(result.totalPayment, locale),
                    emphasize = true
                )
            }
        }

        OutlinedButton(
            onClick = { showSchedule = !showSchedule },
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().testTag("loan_toggle_schedule")
        ) {
            Icon(
                imageVector = Icons.Rounded.TableRows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (showSchedule) {
                    stringResource(R.string.finance_hide_schedule)
                } else {
                    pluralStringResource(
                        R.plurals.finance_show_schedule,
                        result.schedule.size,
                        result.schedule.size
                    )
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showSchedule) {
            val rows = if (showAllPeriods) result.schedule else result.schedule.take(12)
            CalcCard(contentPadding = PaddingValues(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f))
                    Text(
                        text = stringResource(R.string.finance_col_principal),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1.2f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.finance_col_interest),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1.2f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.finance_col_balance),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1.4f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${row.period}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.6f)
                        )
                        Text(
                            formatCurrency(row.principal, locale),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatCurrency(row.interest, locale),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = extColors.technicalAccent,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatCurrency(row.balance, locale),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.4f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                if (result.schedule.size > 12) {
                    OutlinedButton(
                        onClick = { showAllPeriods = !showAllPeriods },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = if (showAllPeriods) {
                                stringResource(R.string.finance_show_first_12)
                            } else {
                                pluralStringResource(
                                    R.plurals.finance_show_all_periods,
                                    result.schedule.size,
                                    result.schedule.size
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestSection(
    state: FinanceState,
    locale: Locale,
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    val currencyPrefix = remember(locale) { "${currencySymbolFor(locale)} " }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.finance_interest_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.principal,
                onValueChange = onPrincipalChange,
                label = { Text(stringResource(R.string.finance_initial_capital)) },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.annualRate,
                    onValueChange = onAnnualRateChange,
                    label = { Text(stringResource(R.string.finance_annual_rate)) },
                    suffix = { Text("%") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.termYears,
                    onValueChange = onTermYearsChange,
                    label = { Text(stringResource(R.string.finance_years)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
            Text(
                text = stringResource(R.string.finance_compounding),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompoundingFrequency.entries.forEach { freq ->
                    CalcFilterChip(
                        selected = state.frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        label = {
                            Text(
                                text = stringResource(freq.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }

    state.interestResult?.let { res ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalcCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.finance_simple),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatCurrency(res.simpleTotal, locale),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "+${formatCurrency(res.simpleInterest, locale)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = extColors.positive
                    )
                }
            }
            CalcResultCard(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.finance_compound),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        formatCurrency(res.compoundTotal, locale),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "+${formatCurrency(res.compoundInterest, locale)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = extColors.positive
                    )
                }
            }
        }
        val diff = res.compoundTotal - res.simpleTotal
        if (diff > 0.005) {
            CalcCard(
                shape = MaterialTheme.shapes.medium,
                containerColor = extColors.positiveContainer,
                borderColor = extColors.positive.copy(alpha = 0.55f),
                contentPadding = PaddingValues(14.dp)
            ) {
                FinanceMetricRow(
                    label = stringResource(R.string.finance_compound_advantage),
                    value = "+${formatCurrency(diff, locale)}",
                    emphasize = true,
                    valueColor = extColors.onPositiveContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaxSection(
    state: FinanceState,
    locale: Locale,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    val currencyPrefix = remember(locale) { "${currencySymbolFor(locale)} " }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.finance_tax_title),
                style = MaterialTheme.typography.titleMedium
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalcFilterChip(
                    selected = !state.taxIncluded,
                    onClick = { onTaxIncludedChange(false) },
                    label = { Text(stringResource(R.string.finance_tax_add), maxLines = 1) }
                )
                CalcFilterChip(
                    selected = state.taxIncluded,
                    onClick = { onTaxIncludedChange(true) },
                    label = { Text(stringResource(R.string.finance_tax_extract), maxLines = 1) }
                )
            }

            OutlinedTextField(
                value = state.taxBaseInput,
                onValueChange = onTaxBaseChange,
                label = {
                    Text(
                        stringResource(
                            if (state.taxIncluded) {
                                R.string.finance_tax_total_input
                            } else {
                                R.string.finance_tax_net_input
                            }
                        )
                    )
                },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            OutlinedTextField(
                value = state.taxRatePercent,
                onValueChange = onTaxRateChange,
                label = { Text(stringResource(R.string.finance_tax_rate_label)) },
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("12", "15", "16", "18", "21")) { preset ->
                    CalcFilterChip(
                        selected = state.taxRatePercent == preset,
                        onClick = { onTaxRateChange(preset) },
                        label = { Text("$preset%") }
                    )
                }
            }
        }
    }

    state.taxResult?.let { res ->
        CalcResultCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_subtotal),
                    value = formatCurrency(res.baseAmount, locale)
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_amount, state.taxRatePercent),
                    value = formatCurrency(res.taxAmount, locale)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_total),
                    value = formatCurrency(res.totalAmount, locale),
                    emphasize = true
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinanceMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    valueColor: Color? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = (if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = valueColor ?: if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
