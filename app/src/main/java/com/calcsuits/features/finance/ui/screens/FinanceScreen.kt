package com.calcsuits.features.finance.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrencies
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanSimulationMode
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateCompoundingFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.features.finance.ui.viewmodel.CompoundingFrequency
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.features.finance.ui.viewmodel.FinanceTab
import com.calcsuits.features.finance.ui.viewmodel.TaxType
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.rememberCurrentLocale
import java.util.Locale

@Composable
fun FinanceScreen(
    state: FinanceState,
    onMenuClick: () -> Unit,
    onTabSelected: (FinanceTab) -> Unit,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onLoanSimulationModeSelected: (LoanSimulationMode) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onCompoundingFrequencySelected: (RateCompoundingFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit,
    onTaxTypeSelected: (TaxType) -> Unit,
    onCustomTaxLabelChange: (String) -> Unit,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    val locale = rememberCurrentLocale()
    val scrollState = rememberScrollState()

    LaunchedEffect(state.selectedTab) {
        scrollState.scrollTo(0)
    }

    val headerSubtitleRes = if (state.selectedTab == FinanceTab.LOAN) {
        R.string.finance_loan_simulator_title
    } else {
        state.selectedTab.labelRes
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance),
                subtitle = stringResource(headerSubtitleRes).uppercase(locale),
                onMenuClick = onMenuClick
            )
        },
        pinnedPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        contentSpacing = 16.dp,
        scrollState = scrollState,
        enableContentScroll = state.selectedTab != FinanceTab.LOAN,
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
                onCurrencySelected = onCurrencySelected,
                onLoanSimulationModeSelected = onLoanSimulationModeSelected,
                onAmortizationSystemSelected = onAmortizationSystemSelected,
                onPaymentFrequencySelected = onPaymentFrequencySelected,
                onCompoundingFrequencySelected = onCompoundingFrequencySelected,
                onLoanTermUnitSelected = onLoanTermUnitSelected,
                onRateConventionSelected = onRateConventionSelected,
                onAmountChange = onAmountChange,
                onDownPaymentChange = onDownPaymentChange,
                onInterestRateChange = onInterestRateChange,
                onYearsChange = onYearsChange
            )
            FinanceTab.INTEREST -> {
                InterestSection(
                    state = state,
                    locale = locale,
                    onCurrencySelected = onCurrencySelected,
                    onPrincipalChange = onPrincipalChange,
                    onAnnualRateChange = onAnnualRateChange,
                    onTermYearsChange = onTermYearsChange,
                    onFrequencyChange = onFrequencyChange
                )
                Spacer(Modifier.height(12.dp))
            }
            FinanceTab.TAX -> {
                TaxSection(
                    state = state,
                    locale = locale,
                    onCurrencySelected = onCurrencySelected,
                    onTaxTypeSelected = onTaxTypeSelected,
                    onCustomTaxLabelChange = onCustomTaxLabelChange,
                    onTaxBaseChange = onTaxBaseChange,
                    onTaxRateChange = onTaxRateChange,
                    onTaxIncludedChange = onTaxIncludedChange
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun CurrencySelectorPill(
    selected: FinanceCurrency,
    onSelected: (FinanceCurrency) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("finance_currency_selector")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selected.code} (${selected.symbol})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.cd_finance_select_currency),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FinanceCurrencies.supported.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currency.code,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (currency == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = "${stringResource(currency.displayNameRes())} (${currency.symbol})",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(currency)
                    },
                    modifier = Modifier.testTag("finance_currency_opt_${currency.code.lowercase(Locale.ROOT)}")
                )
            }
        }
    }
}

@StringRes
internal fun FinanceCurrency.displayNameRes(): Int = when (code.uppercase(Locale.ROOT)) {
    "USD" -> R.string.unit_usd
    "EUR" -> R.string.unit_eur
    "NIO" -> R.string.unit_nio
    "MXN" -> R.string.unit_mxn
    "CRC" -> R.string.unit_crc
    "GBP" -> R.string.unit_gbp
    else -> R.string.finance_currency_label
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun FinanceMetricRow(
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
