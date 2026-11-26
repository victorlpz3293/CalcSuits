package com.calcsuits.features.finance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.features.finance.ui.viewmodel.CompoundingFrequency
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.features.finance.ui.viewmodel.TaxType
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatRatePercent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterestSection(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    val currency = state.selectedCurrency
    val currencyPrefix = "${currency.symbol} "

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.finance_interest_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CurrencySelectorPill(
                    selected = currency,
                    onSelected = onCurrencySelected
                )
            }
            OutlinedTextField(
                value = state.principal,
                onValueChange = onPrincipalChange,
                label = { Text(stringResource(R.string.finance_initial_capital)) },
                prefix = { Text(currencyPrefix, fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interest_principal_input"),
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
                    modifier = Modifier
                        .weight(1f)
                        .testTag("interest_rate_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.termYears,
                    onValueChange = onTermYearsChange,
                    label = { Text(stringResource(R.string.finance_years)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("interest_years_input"),
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
                        },
                        modifier = Modifier.testTag("interest_freq_${freq.name.lowercase(Locale.ROOT)}")
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
                        text = formatMoney(res.simpleTotal, currency, locale),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "+${formatMoney(res.simpleInterest, currency, locale)}",
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
                        text = formatMoney(res.compoundTotal, currency, locale),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "+${formatMoney(res.compoundInterest, currency, locale)}",
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
                    value = "+${formatMoney(diff, currency, locale)}",
                    emphasize = true,
                    valueColor = extColors.onPositiveContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaxSection(
    state: FinanceState,
    locale: Locale,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onTaxTypeSelected: (TaxType) -> Unit,
    onCustomTaxLabelChange: (String) -> Unit,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    val currency = state.selectedCurrency
    val currencyPrefix = "${currency.symbol} "
    val resolvedTaxLabel = if (state.taxType == TaxType.CUSTOM && state.customTaxLabel.isNotBlank()) {
        state.customTaxLabel.trim()
    } else {
        stringResource(state.taxType.labelRes)
    }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.finance_tax_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CurrencySelectorPill(
                    selected = currency,
                    onSelected = onCurrencySelected
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.finance_tax_type_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaxType.entries.forEach { type ->
                        CalcFilterChip(
                            selected = state.taxType == type,
                            onClick = { onTaxTypeSelected(type) },
                            label = {
                                Text(
                                    text = stringResource(type.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("tax_type_${type.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
                if (state.taxType == TaxType.CUSTOM) {
                    OutlinedTextField(
                        value = state.customTaxLabel,
                        onValueChange = onCustomTaxLabelChange,
                        label = { Text(stringResource(R.string.finance_tax_custom_name_label)) },
                        placeholder = { Text(stringResource(R.string.finance_tax_custom_name_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tax_custom_name_input"),
                        singleLine = true
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalcFilterChip(
                    selected = !state.taxIncluded,
                    onClick = { onTaxIncludedChange(false) },
                    label = { Text(stringResource(R.string.finance_tax_add), maxLines = 1) },
                    modifier = Modifier.testTag("tax_mode_add")
                )
                CalcFilterChip(
                    selected = state.taxIncluded,
                    onClick = { onTaxIncludedChange(true) },
                    label = { Text(stringResource(R.string.finance_tax_extract), maxLines = 1) },
                    modifier = Modifier.testTag("tax_mode_extract")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_amount_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            OutlinedTextField(
                value = state.taxRatePercent,
                onValueChange = onTaxRateChange,
                label = { Text(stringResource(R.string.finance_tax_rate_label)) },
                suffix = { Text("%") },
                supportingText = {
                    Text(
                        text = stringResource(R.string.finance_tax_rate_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_rate_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )
        }
    }

    state.taxResult?.let { res ->
        CalcResultCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.finance_tax_selected_type).uppercase(locale)} · $resolvedTaxLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("tax_result_type")
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_subtotal),
                    value = formatMoney(res.baseAmount, currency, locale)
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_applied_rate),
                    value = formatRatePercent(res.appliedRatePercent, locale, maxFraction = 4)
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_amount_plain),
                    value = formatMoney(res.taxAmount, currency, locale)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow(
                    label = stringResource(R.string.finance_tax_total),
                    value = formatMoney(res.totalAmount, currency, locale),
                    emphasize = true
                )
            }
        }
    }
}
