package com.calcsuits.features.finance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.PaymentFrequency
import com.calcsuits.domain.model.finance.RateCompoundingFrequency
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.features.finance.ui.viewmodel.CompoundingFrequency
import com.calcsuits.features.finance.ui.viewmodel.FinanceState
import com.calcsuits.features.finance.ui.viewmodel.FinanceTab
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.rememberCurrentLocale
import java.util.Locale

@Composable
fun FinanceScreen(
    state: FinanceState,
    onMenuClick: () -> Unit,
    onTabSelected: (FinanceTab) -> Unit,
    onBackToHub: () -> Unit,
    onCurrencySelected: (FinanceCurrency) -> Unit,
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
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    BackHandler(enabled = state.selectedTab != null) {
        onBackToHub()
    }

    val locale = rememberCurrentLocale()

    when (state.selectedTab) {
        null -> FinanceToolsHub(
            onMenuClick = onMenuClick,
            onToolSelected = onTabSelected
        )
        FinanceTab.LOAN -> LoanToolView(
            state = state,
            locale = locale,
            onBack = onBackToHub,
            onCurrencySelected = onCurrencySelected,
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
        FinanceTab.INTEREST -> InterestToolView(
            state = state,
            locale = locale,
            onBack = onBackToHub,
            onCurrencySelected = onCurrencySelected,
            onPrincipalChange = onPrincipalChange,
            onAnnualRateChange = onAnnualRateChange,
            onTermYearsChange = onTermYearsChange,
            onFrequencyChange = onFrequencyChange
        )
        FinanceTab.TAX -> TaxToolView(
            state = state,
            locale = locale,
            onBack = onBackToHub,
            onCurrencySelected = onCurrencySelected,
            onTaxBaseChange = onTaxBaseChange,
            onTaxRateChange = onTaxRateChange,
            onTaxIncludedChange = onTaxIncludedChange
        )
    }
}

@Composable
private fun FinanceToolsHub(
    onMenuClick: () -> Unit,
    onToolSelected: (FinanceTab) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance),
                subtitle = stringResource(R.string.finance_hub_subtitle),
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.finance_hub_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            FinanceTab.entries.forEach { tool ->
                Card(
                    onClick = { onToolSelected(tool) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finance_card_${tool.key}"),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(tool.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(tool.summaryRes),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = extColors.technicalAccent
                            )
                            Text(
                                text = stringResource(tool.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanToolView(
    state: FinanceState,
    locale: Locale,
    onBack: () -> Unit,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onAmortizationSystemSelected: (AmortizationSystem) -> Unit,
    onPaymentFrequencySelected: (PaymentFrequency) -> Unit,
    onCompoundingFrequencySelected: (RateCompoundingFrequency) -> Unit,
    onLoanTermUnitSelected: (LoanTermUnit) -> Unit,
    onRateConventionSelected: (RateConvention) -> Unit,
    onAmountChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onInterestRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance_tab_loan),
                subtitle = stringResource(R.string.finance).uppercase(locale),
                onMenuClick = onBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_finance)
            )
        },
        pinnedPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        contentSpacing = 16.dp,
        scrollState = scrollState,
        enableContentScroll = false,
        compactHeightThreshold = 200.dp,
        pinnedContent = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AmortizationSystem.simulatorSystems.forEachIndexed { index, system ->
                    SegmentedButton(
                        selected = state.amortizationSystem == system,
                        onClick = { onAmortizationSystemSelected(system) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AmortizationSystem.simulatorSystems.size
                        ),
                        colors = calcSegmentedButtonColors(),
                        modifier = Modifier.testTag("loan_system_${system.name.lowercase(Locale.ROOT)}")
                    ) {
                        Text(
                            text = stringResource(system.tabLabelRes()).uppercase(locale),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) {
        LoanSection(
            state = state,
            locale = locale,
            onCurrencySelected = onCurrencySelected,
            onPaymentFrequencySelected = onPaymentFrequencySelected,
            onCompoundingFrequencySelected = onCompoundingFrequencySelected,
            onLoanTermUnitSelected = onLoanTermUnitSelected,
            onRateConventionSelected = onRateConventionSelected,
            onAmountChange = onAmountChange,
            onDownPaymentChange = onDownPaymentChange,
            onInterestRateChange = onInterestRateChange,
            onYearsChange = onYearsChange
        )
    }
}

@Composable
private fun InterestToolView(
    state: FinanceState,
    locale: Locale,
    onBack: () -> Unit,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onTermYearsChange: (String) -> Unit,
    onFrequencyChange: (CompoundingFrequency) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance_tab_interest),
                subtitle = stringResource(R.string.finance).uppercase(locale),
                onMenuClick = onBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_finance)
            )
        },
        pinnedPadding = PaddingValues(0.dp),
        pinnedSpacing = 0.dp,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
        contentSpacing = 16.dp,
        scrollState = scrollState,
        enableContentScroll = true,
        compactHeightThreshold = 200.dp,
        pinnedContent = {}
    ) {
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
}

@Composable
private fun TaxToolView(
    state: FinanceState,
    locale: Locale,
    onBack: () -> Unit,
    onCurrencySelected: (FinanceCurrency) -> Unit,
    onTaxBaseChange: (String) -> Unit,
    onTaxRateChange: (String) -> Unit,
    onTaxIncludedChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.finance_tab_tax),
                subtitle = stringResource(R.string.finance).uppercase(locale),
                onMenuClick = onBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_finance)
            )
        },
        pinnedPadding = PaddingValues(0.dp),
        pinnedSpacing = 0.dp,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
        contentSpacing = 16.dp,
        scrollState = scrollState,
        enableContentScroll = true,
        compactHeightThreshold = 200.dp,
        pinnedContent = {}
    ) {
        TaxSection(
            state = state,
            locale = locale,
            onCurrencySelected = onCurrencySelected,
            onTaxBaseChange = onTaxBaseChange,
            onTaxRateChange = onTaxRateChange,
            onTaxIncludedChange = onTaxIncludedChange
        )
        Spacer(Modifier.height(12.dp))
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
