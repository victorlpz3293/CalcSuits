package com.calcsuits.features.finance.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatCompactMoneyAxis
import com.calcsuits.ui.util.formatMoney
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private enum class LoanChartTab {
    BALANCE,
    INTEREST_VS_PRINCIPAL
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanChartsCard(
    result: LoanResult,
    locale: Locale
) {
    var selectedTab by rememberSaveable { mutableStateOf(LoanChartTab.BALANCE) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = CalcSuitsTheme.colors.technicalAccent
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val currency = result.currency

    val chartModel = remember(result, selectedTab) {
        buildChartSeries(
            result = result,
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
    tab: LoanChartTab
): LoanChartSeries {
    val rows = result.schedule
    val n = rows.size
    val midPeriod = if (n >= 3) (n + 1) / 2 else 0

    return when (tab) {
        LoanChartTab.BALANCE -> {
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
