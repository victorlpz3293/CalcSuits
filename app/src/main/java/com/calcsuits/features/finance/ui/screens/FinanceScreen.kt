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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcsuits.features.finance.ui.viewmodel.CompoundingFrequency
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.features.finance.ui.viewmodel.FinanceTab
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
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
    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = "Finanzas",
                subtitle = state.selectedTab.label.uppercase(),
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        modifier = Modifier.testTag("finance_tab_${tab.name.lowercase()}")
                    ) {
                        Text(tab.label, maxLines = 1)
                    }
                }
            }

            when (state.selectedTab) {
                FinanceTab.LOAN -> LoanSection(
                    state = state,
                    onAmountChange = onAmountChange,
                    onInterestRateChange = onInterestRateChange,
                    onYearsChange = onYearsChange
                )
                FinanceTab.INTEREST -> InterestSection(
                    state = state,
                    onPrincipalChange = onPrincipalChange,
                    onAnnualRateChange = onAnnualRateChange,
                    onTermYearsChange = onTermYearsChange,
                    onFrequencyChange = onFrequencyChange
                )
                FinanceTab.TAX -> TaxSection(
                    state = state,
                    onTaxBaseChange = onTaxBaseChange,
                    onTaxRateChange = onTaxRateChange,
                    onTaxIncludedChange = onTaxIncludedChange
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LoanSection(
    state: FinanceState,
    onAmountChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    var showSchedule by remember { mutableStateOf(false) }
    var showAllPeriods by remember { mutableStateOf(false) }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Parámetros del préstamo", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text("Capital a financiar") },
                prefix = { Text("$ ", fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth().testTag("loan_amount_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.interestRate,
                    onValueChange = onInterestRateChange,
                    label = { Text("Tasa anual") },
                    suffix = { Text("%") },
                    modifier = Modifier.weight(1f).testTag("loan_rate_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.years,
                    onValueChange = onYearsChange,
                    label = { Text("Plazo (años)") },
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
                        text = "CUOTA MENSUAL ESTIMADA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = money(result.monthlyPayment),
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
                            label = "Capital (${String.format(Locale.US, "%.0f%%", principalRatio * 100)})"
                        )
                        LegendDot(
                            color = extColors.technicalAccent,
                            label = "Interés (${String.format(Locale.US, "%.0f%%", (1f - principalRatio) * 100)})"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow("Intereses totales", money(result.totalInterest))
                FinanceMetricRow("Monto total a pagar", money(result.totalPayment), emphasize = true)
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
                text = if (showSchedule) "Ocultar tabla de amortización" else "Ver tabla de amortización (${result.schedule.size} cuotas)",
                fontWeight = FontWeight.SemiBold
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
                    Text("Capital", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    Text("Interés", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    Text("Saldo", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End)
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
                            moneyCompact(row.principal),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            moneyCompact(row.interest),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = extColors.technicalAccent,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            moneyCompact(row.balance),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.4f),
                            textAlign = TextAlign.End
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
                        Text(if (showAllPeriods) "Mostrar primeras 12" else "Mostrar todas (${result.schedule.size})")
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
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Interés simple vs. compuesto", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.principal,
                onValueChange = onPrincipalChange,
                label = { Text("Capital inicial") },
                prefix = { Text("$ ", fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.annualRate,
                    onValueChange = onAnnualRateChange,
                    label = { Text("Tasa anual") },
                    suffix = { Text("%") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.termYears,
                    onValueChange = onTermYearsChange,
                    label = { Text("Años") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
            Text("Capitalización", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompoundingFrequency.entries.forEach { freq ->
                    CalcFilterChip(
                        selected = state.frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        label = { Text(freq.label, maxLines = 1) }
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
                    Text("SIMPLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        money(res.simpleTotal),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "+${money(res.simpleInterest)}",
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
                    Text("COMPUESTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        money(res.compoundTotal),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "+${money(res.compoundInterest)}",
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
                    label = "Ventaja del interés compuesto",
                    value = "+${money(diff)}",
                    emphasize = true,
                    valueColor = extColors.onPositiveContainer
                )
            }
        }
    }
}

@Composable
private fun TaxSection(
    state: FinanceState,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cálculo de IVA / Impuestos", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcFilterChip(
                    selected = !state.taxIncluded,
                    onClick = { onTaxIncludedChange(false) },
                    label = { Text("Sumar IVA") }
                )
                CalcFilterChip(
                    selected = state.taxIncluded,
                    onClick = { onTaxIncludedChange(true) },
                    label = { Text("Desglosar IVA incluido") }
                )
            }

            OutlinedTextField(
                value = state.taxBaseInput,
                onValueChange = onTaxBaseChange,
                label = { Text(if (state.taxIncluded) "Monto total (IVA incluido)" else "Monto neto (sin IVA)") },
                prefix = { Text("$ ", fontFamily = JetBrainsMonoFontFamily) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            OutlinedTextField(
                value = state.taxRatePercent,
                onValueChange = onTaxRateChange,
                label = { Text("Tasa de impuesto") },
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
                FinanceMetricRow("Subtotal (base)", money(res.baseAmount))
                FinanceMetricRow("Impuesto (${state.taxRatePercent}%)", money(res.taxAmount))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow("Total final", money(res.totalAmount), emphasize = true)
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
            color = MaterialTheme.colorScheme.onSurface
        )
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

private fun money(value: Double): String = String.format(Locale.US, "$ %,.2f", value)
private fun moneyCompact(value: Double): String = String.format(Locale.US, "$%.2f", value)
