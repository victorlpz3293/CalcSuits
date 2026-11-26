package com.calcsuits.features.finance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.LoanComparisonResult
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatCompactMoneyAxis
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatRatePercent
import com.calcsuits.ui.util.formatShortDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private enum class LoanChartTab {
    BALANCE,
    INTEREST_VS_PRINCIPAL,
    SYSTEM_COMPARISON
}

/**
 * Uses a stacked two-column metric layout inside [LoanComparisonCard] on narrow screens
 * (`< 400dp` inner card width, covering 360dp–412dp phones) so 7–9 digit formatted monetary
 * amounts remain fully legible without ellipsis truncation.
 */
internal fun shouldUseStackedComparisonLayout(availableWidthDp: Float): Boolean =
    availableWidthDp < 400f

private data class ComparisonMetricItem(
    val label: String,
    val frenchValue: String,
    val germanValue: String,
    val emphasize: Boolean = false,
    val dividerBefore: Boolean = false
)

@Composable
internal fun LoanComparisonCard(
    comparison: LoanComparisonResult,
    locale: Locale
) {
    val french = comparison.french
    val german = comparison.german
    val currency = french.currency
    val installmentContextualNoun = stringResource(french.paymentFrequency.installmentContextualNounRes())
    val periodsLabel = pluralStringResource(
        R.plurals.finance_term_summary_periods,
        french.schedule.size,
        french.schedule.size
    )
    val hasDownPayment = french.downPayment > 0.0

    val metricItems = buildList {
        add(
            ComparisonMetricItem(
                label = stringResource(R.string.finance_loan_initial_installment_fmt, installmentContextualNoun),
                frenchValue = formatMoney(french.firstPayment, currency, locale),
                germanValue = formatMoney(german.firstPayment, currency, locale)
            )
        )
        add(
            ComparisonMetricItem(
                label = stringResource(R.string.finance_loan_final_installment_fmt, installmentContextualNoun),
                frenchValue = formatMoney(french.lastPayment, currency, locale),
                germanValue = formatMoney(german.lastPayment, currency, locale)
            )
        )
        add(
            ComparisonMetricItem(
                label = stringResource(R.string.finance_total_interest),
                frenchValue = formatMoney(french.totalInterest, currency, locale),
                germanValue = formatMoney(german.totalInterest, currency, locale)
            )
        )
        add(
            ComparisonMetricItem(
                label = stringResource(R.string.finance_loan_principal_amortized),
                frenchValue = formatMoney(french.principalAmount, currency, locale),
                germanValue = formatMoney(german.principalAmount, currency, locale)
            )
        )
        if (hasDownPayment) {
            add(
                ComparisonMetricItem(
                    label = stringResource(R.string.finance_total_installments_paid),
                    frenchValue = formatMoney(french.totalPayment, currency, locale),
                    germanValue = formatMoney(german.totalPayment, currency, locale),
                    dividerBefore = true
                )
            )
            add(
                ComparisonMetricItem(
                    label = stringResource(R.string.finance_total_outflow_with_down_payment),
                    frenchValue = formatMoney(french.totalOutflowWithDownPayment(), currency, locale),
                    germanValue = formatMoney(german.totalOutflowWithDownPayment(), currency, locale),
                    emphasize = true
                )
            )
        } else {
            add(
                ComparisonMetricItem(
                    label = stringResource(R.string.finance_total_payment),
                    frenchValue = formatMoney(french.totalPayment, currency, locale),
                    germanValue = formatMoney(german.totalPayment, currency, locale),
                    emphasize = true,
                    dividerBefore = true
                )
            )
        }
    }

    CalcResultCard {
        Column(
            modifier = Modifier.testTag("loan_comparison_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Balance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.finance_comparison_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Shared parameters context strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.finance_comparison_context,
                            formatMoney(french.principalAmount, currency, locale),
                            formatRatePercent(french.annualRatePercent, locale, maxFraction = 2),
                            stringResource(french.paymentFrequency.shortLabelRes()),
                            periodsLabel
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasDownPayment) {
                        Text(
                            text = stringResource(
                                R.string.finance_comparison_down_payment_note,
                                formatMoney(french.requestedAmount, currency, locale),
                                formatMoney(french.downPayment, currency, locale)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    french.issuedAtMillis?.let { issuedAt ->
                        Text(
                            text = stringResource(
                                R.string.finance_issue_date_inline,
                                formatShortDate(issuedAt, locale)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useStacked = shouldUseStackedComparisonLayout(maxWidth.value)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (useStacked) 10.dp else 12.dp)
                ) {
                    if (useStacked) {
                        // Two equal-width columns (50% / 50%) so full monetary amounts never clip on 360dp–412dp phones
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ComparisonSystemHeader(
                                title = stringResource(R.string.finance_system_french_short),
                                subtitle = stringResource(R.string.finance_system_french_academic),
                                color = MaterialTheme.colorScheme.primary,
                                alignEnd = false,
                                modifier = Modifier.weight(1f)
                            )
                            ComparisonSystemHeader(
                                title = stringResource(R.string.finance_system_german_short),
                                subtitle = stringResource(R.string.finance_system_german_academic),
                                color = CalcSuitsTheme.colors.technicalAccent,
                                alignEnd = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        metricItems.forEach { item ->
                            if (item.dividerBefore) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            ComparisonStackedMetricItem(
                                label = item.label,
                                frenchValue = item.frenchValue,
                                germanValue = item.germanValue,
                                emphasize = item.emphasize
                            )
                        }
                    } else {
                        // Wide 3-column horizontal layout for tablets / expanded screens
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Spacer(Modifier.weight(1.1f))
                            ComparisonSystemHeader(
                                title = stringResource(R.string.finance_system_french_short),
                                subtitle = stringResource(R.string.finance_system_french_academic),
                                color = MaterialTheme.colorScheme.primary,
                                alignEnd = true,
                                modifier = Modifier.weight(1f)
                            )
                            ComparisonSystemHeader(
                                title = stringResource(R.string.finance_system_german_short),
                                subtitle = stringResource(R.string.finance_system_german_academic),
                                color = CalcSuitsTheme.colors.technicalAccent,
                                alignEnd = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        metricItems.forEach { item ->
                            if (item.dividerBefore) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            ComparisonMetricRow(
                                label = item.label,
                                frenchValue = item.frenchValue,
                                germanValue = item.germanValue,
                                emphasize = item.emphasize
                            )
                        }
                    }
                }
            }

            // Explicit differences block (French - German)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.finance_comparison_differences_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    FinanceMetricRow(
                        label = stringResource(R.string.finance_comparison_interest_diff),
                        value = formatMoney(comparison.interestDifference, currency, locale),
                        modifier = Modifier.testTag("loan_comparison_interest_diff")
                    )
                    FinanceMetricRow(
                        label = stringResource(R.string.finance_comparison_total_diff),
                        value = formatMoney(comparison.totalPaymentDifference, currency, locale),
                        modifier = Modifier.testTag("loan_comparison_total_diff")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.finance_comparison_neutral_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonSystemHeader(
    title: String,
    subtitle: String,
    color: Color,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (alignEnd) Alignment.End else Alignment.Start
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComparisonStackedMetricItem(
    label: String,
    frenchValue: String,
    germanValue: String,
    emphasize: Boolean = false
) {
    val valueStyle = (if (emphasize) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.bodySmall
    }).copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = frenchValue,
                style = valueStyle,
                color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = germanValue,
                style = valueStyle,
                color = if (emphasize) CalcSuitsTheme.colors.technicalAccent else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ComparisonMetricRow(
    label: String,
    frenchValue: String,
    germanValue: String,
    emphasize: Boolean = false
) {
    val valueStyle = (if (emphasize) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.bodySmall
    }).copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = frenchValue,
            style = valueStyle,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = germanValue,
            style = valueStyle,
            color = if (emphasize) CalcSuitsTheme.colors.technicalAccent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanChartsCard(
    result: LoanResult,
    comparison: LoanComparisonResult?,
    locale: Locale,
    preferComparisonTab: Boolean = false
) {
    var selectedTab by remember {
        mutableStateOf(
            if (preferComparisonTab && comparison != null) {
                LoanChartTab.SYSTEM_COMPARISON
            } else {
                LoanChartTab.BALANCE
            }
        )
    }

    LaunchedEffect(preferComparisonTab) {
        if (preferComparisonTab && comparison != null) {
            selectedTab = LoanChartTab.SYSTEM_COMPARISON
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = CalcSuitsTheme.colors.technicalAccent
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val currency = result.currency

    val chartModel = remember(result, comparison, selectedTab) {
        buildChartSeries(
            result = result,
            comparison = comparison,
            tab = selectedTab
        )
    }

    CalcCard {
        Column(
            modifier = Modifier.testTag("loan_charts_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.finance_charts_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalcFilterChip(
                    selected = selectedTab == LoanChartTab.BALANCE,
                    onClick = { selectedTab = LoanChartTab.BALANCE },
                    label = { Text(stringResource(R.string.finance_chart_tab_balance), maxLines = 1) },
                    modifier = Modifier.testTag("loan_chart_tab_balance")
                )
                CalcFilterChip(
                    selected = selectedTab == LoanChartTab.INTEREST_VS_PRINCIPAL,
                    onClick = { selectedTab = LoanChartTab.INTEREST_VS_PRINCIPAL },
                    label = { Text(stringResource(R.string.finance_chart_tab_components), maxLines = 1) },
                    modifier = Modifier.testTag("loan_chart_tab_components")
                )
                if (comparison != null) {
                    CalcFilterChip(
                        selected = selectedTab == LoanChartTab.SYSTEM_COMPARISON,
                        onClick = { selectedTab = LoanChartTab.SYSTEM_COMPARISON },
                        label = { Text(stringResource(R.string.finance_chart_tab_comparison), maxLines = 1) },
                        modifier = Modifier.testTag("loan_chart_tab_comparison")
                    )
                }
            }

            // Legend + exact peak scale reference
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (selectedTab) {
                        LoanChartTab.BALANCE -> {
                            LegendDot(
                                color = primaryColor,
                                label = stringResource(R.string.finance_chart_legend_balance)
                            )
                        }
                        LoanChartTab.INTEREST_VS_PRINCIPAL -> {
                            LegendDot(
                                color = primaryColor,
                                label = stringResource(R.string.finance_col_principal)
                            )
                            LegendDot(
                                color = accentColor,
                                label = stringResource(R.string.finance_col_interest)
                            )
                        }
                        LoanChartTab.SYSTEM_COMPARISON -> {
                            LegendDot(
                                color = primaryColor,
                                label = stringResource(R.string.finance_system_french_academic)
                            )
                            LegendDot(
                                color = accentColor,
                                label = stringResource(R.string.finance_system_german_academic)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.finance_chart_scale_peak,
                        formatMoney(chartModel.maxY, currency, locale)
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact Y-axis ticks (zero truncation on 6-9 digit amounts) + Canvas
            val yAxisWidth = 56.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .height(176.dp)
                        .width(yAxisWidth),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatCompactMoneyAxis(chartModel.maxY, currency, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = formatCompactMoneyAxis(chartModel.maxY / 2.0, currency, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = formatCompactMoneyAxis(0.0, currency, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(176.dp)
                        .testTag("loan_evolution_canvas")
                ) {
                    val padTop = 8.dp.toPx()
                    val padBottom = 8.dp.toPx()
                    val plotHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                    val plotWidth = size.width.coerceAtLeast(1f)
                    val maxY = chartModel.maxY.coerceAtLeast(1.0)

                    // Horizontal reference lines (100%, 50%, 0%)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = padTop + plotHeight * fraction
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (fraction < 1f) dashEffect else null
                        )
                    }

                    fun drawSeries(values: List<Double>, color: Color, fillArea: Boolean) {
                        if (values.isEmpty()) return
                        val strokeWidth = 2.5.dp.toPx()
                        if (values.size == 1) {
                            val y = padTop + plotHeight * (1f - (values[0] / maxY).toFloat().coerceIn(0f, 1f))
                            drawLine(
                                color = color,
                                start = Offset(0f, y),
                                end = Offset(plotWidth, y),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = color,
                                radius = 4.dp.toPx(),
                                center = Offset(plotWidth / 2f, y)
                            )
                            return
                        }

                        val linePath = Path()
                        val fillPath = Path()
                        val stepX = plotWidth / (values.size - 1).toFloat()

                        values.forEachIndexed { index, raw ->
                            val x = index * stepX
                            val normalized = (raw / maxY).toFloat().coerceIn(0f, 1f)
                            val y = padTop + plotHeight * (1f - normalized)
                            if (index == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, padTop + plotHeight)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        if (fillArea) {
                            fillPath.lineTo(plotWidth, padTop + plotHeight)
                            fillPath.close()
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.24f),
                                        color.copy(alpha = 0.02f)
                                    ),
                                    startY = padTop,
                                    endY = padTop + plotHeight
                                )
                            )
                        }

                        drawPath(
                            path = linePath,
                            color = color,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawSeries(
                        values = chartModel.primarySeries,
                        color = primaryColor,
                        fillArea = selectedTab == LoanChartTab.BALANCE
                    )
                    if (chartModel.secondarySeries.isNotEmpty()) {
                        drawSeries(
                            values = chartModel.secondarySeries,
                            color = accentColor,
                            fillArea = false
                        )
                    }
                }
            }

            // X-axis period markers aligned with chart area (yAxisWidth + 8dp spacing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = yAxisWidth + 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = chartModel.startLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (chartModel.midLabel.isNotEmpty()) {
                    Text(
                        text = chartModel.midLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = chartModel.endLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class LoanChartSeries(
    val primarySeries: List<Double>,
    val secondarySeries: List<Double>,
    val maxY: Double,
    val startLabel: String,
    val midLabel: String,
    val endLabel: String
)

private fun buildChartSeries(
    result: LoanResult,
    comparison: LoanComparisonResult?,
    tab: LoanChartTab
): LoanChartSeries {
    val rows = result.schedule
    val n = rows.size
    val midPeriod = if (n >= 3) (n + 1) / 2 else 0

    return when (tab) {
        LoanChartTab.BALANCE -> {
            // Include initial balance at t=0 followed by closing balance of every period
            val rawPoints = buildList(n + 1) {
                add(result.principalAmount)
                rows.forEach { add(it.balance) }
            }
            val sampled = downsample(rawPoints)
            val maxY = max(result.principalAmount, sampled.maxOrNull() ?: 1.0)
            LoanChartSeries(
                primarySeries = sampled,
                secondarySeries = emptyList(),
                maxY = maxY,
                startLabel = "#0",
                midLabel = if (midPeriod > 0) "#$midPeriod" else "",
                endLabel = "#$n"
            )
        }
        LoanChartTab.INTEREST_VS_PRINCIPAL -> {
            val principalPoints = downsample(rows.map { it.principal })
            val interestPoints = downsample(rows.map { it.interest })
            val maxY = max(
                principalPoints.maxOrNull() ?: 1.0,
                interestPoints.maxOrNull() ?: 1.0
            )
            LoanChartSeries(
                primarySeries = principalPoints,
                secondarySeries = interestPoints,
                maxY = maxY,
                startLabel = "#1",
                midLabel = if (midPeriod > 0) "#$midPeriod" else "",
                endLabel = "#$n"
            )
        }
        LoanChartTab.SYSTEM_COMPARISON -> {
            val frenchRows = comparison?.french?.schedule ?: rows
            val germanRows = comparison?.german?.schedule ?: emptyList()
            val frenchPayments = downsample(frenchRows.map { it.payment })
            val germanPayments = downsample(germanRows.map { it.payment })
            val maxY = max(
                frenchPayments.maxOrNull() ?: 1.0,
                germanPayments.maxOrNull() ?: 1.0
            )
            val count = frenchRows.size
            val mid = if (count >= 3) (count + 1) / 2 else 0
            LoanChartSeries(
                primarySeries = frenchPayments,
                secondarySeries = germanPayments,
                maxY = maxY,
                startLabel = "#1",
                midLabel = if (mid > 0) "#$mid" else "",
                endLabel = "#$count"
            )
        }
    }
}

/**
 * Downsamples long schedules to at most [maxPoints] samples while always preserving
 * exact first and last points for smooth Canvas rendering on 30-50 year loans.
 */
private fun downsample(values: List<Double>, maxPoints: Int = 120): List<Double> {
    if (values.size <= maxPoints) return values
    val lastIndex = values.lastIndex
    return List(maxPoints) { i ->
        val mapped = ((i.toDouble() / (maxPoints - 1).toDouble()) * lastIndex).roundToInt()
            .coerceIn(0, lastIndex)
        values[mapped]
    }
}
