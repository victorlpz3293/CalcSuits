package com.calcsuits.features.finance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.R
import com.calcsuits.domain.model.finance.AmortizationRow
import com.calcsuits.domain.model.finance.AmortizationSystem
import com.calcsuits.domain.model.finance.FinanceCurrency
import com.calcsuits.domain.model.finance.LoanResult
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatMoneyPlain
import com.calcsuits.ui.util.formatPercentInt
import com.calcsuits.ui.util.formatRatePercent
import java.util.Locale

@Composable
internal fun LoanSummaryCard(
    result: LoanResult,
    locale: Locale
) {
    val extColors = CalcSuitsTheme.colors
    val currency = result.currency
    val principalRatio = if (result.totalPayment > 0.0) {
        (result.principalAmount / result.totalPayment).toFloat().coerceIn(0.05f, 1f)
    } else {
        1f
    }

    val primaryHeaderRes = when (result.system) {
        AmortizationSystem.FRENCH -> R.string.finance_loan_primary_french
        AmortizationSystem.GERMAN -> R.string.finance_loan_primary_german
        AmortizationSystem.AMERICAN -> if (result.schedule.size == 1) {
            R.string.finance_loan_primary_american_single
        } else {
            R.string.finance_loan_primary_american
        }
    }

    CalcResultCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(primaryHeaderRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatMoney(result.monthlyPayment, currency, locale),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("loan_primary_payment")
                )
                when (result.system) {
                    AmortizationSystem.GERMAN -> if (result.schedule.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.finance_loan_german_range,
                                formatMoney(result.lastPayment, currency, locale)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AmortizationSystem.AMERICAN -> if (result.schedule.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.finance_loan_american_balloon,
                                formatMoney(result.lastPayment, currency, locale)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AmortizationSystem.FRENCH -> Unit
                }
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
                label = stringResource(R.string.finance_loan_principal_amortized),
                value = formatMoney(result.principalAmount, currency, locale)
            )
            result.constantPrincipalPerPeriod?.let { constPrincipal ->
                FinanceMetricRow(
                    label = stringResource(R.string.finance_loan_constant_principal),
                    value = formatMoney(constPrincipal, currency, locale)
                )
            }
            FinanceMetricRow(
                label = stringResource(R.string.finance_total_interest),
                value = formatMoney(result.totalInterest, currency, locale)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_first_payment),
                value = formatMoney(result.firstPayment, currency, locale)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_last_payment),
                value = formatMoney(result.lastPayment, currency, locale)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_final_balance),
                value = formatMoney(result.finalBalance, currency, locale)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_periodic_rate),
                value = formatRatePercent(result.periodicRate * 100.0, locale, maxFraction = 4)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_effective_rate),
                value = formatRatePercent(result.effectiveAnnualRate * 100.0, locale, maxFraction = 4)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FinanceMetricRow(
                label = stringResource(R.string.finance_total_payment),
                value = formatMoney(result.totalPayment, currency, locale),
                emphasize = true
            )
        }
    }
}

@Composable
internal fun ScheduleToggleButton(
    scheduleSize: Int,
    showSchedule: Boolean,
    onToggle: () -> Unit
) {
    OutlinedButton(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_toggle_schedule")
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
                    scheduleSize,
                    scheduleSize
                )
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ScheduleHeaderCard(result: LoanResult) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.finance_schedule_currency_caption,
                    result.currency.code,
                    result.currency.symbol,
                    result.schedule.size
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(0.45f)
                )
                Text(
                    text = stringResource(R.string.finance_col_payment),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.15f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.finance_col_principal),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.15f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.finance_col_interest),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.05f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.finance_col_balance),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ScheduleRowItem(
    row: AmortizationRow,
    currency: FinanceCurrency,
    locale: Locale
) {
    val extColors = CalcSuitsTheme.colors
    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${row.period}",
                style = cellStyle.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.45f)
            )
            Text(
                text = formatMoneyPlain(row.payment, currency, locale),
                style = cellStyle.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1.15f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatMoneyPlain(row.principal, currency, locale),
                style = cellStyle,
                modifier = Modifier.weight(1.15f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatMoneyPlain(row.interest, currency, locale),
                style = cellStyle,
                color = extColors.technicalAccent,
                modifier = Modifier.weight(1.05f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatMoneyPlain(row.balance, currency, locale),
                style = cellStyle.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
internal fun ScheduleFooterCard(
    result: LoanResult,
    locale: Locale,
    showAllPeriods: Boolean,
    onToggleShowAll: () -> Unit
) {
    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )

    Surface(
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.finance_schedule_totals),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatMoneyPlain(result.totalPayment, result.currency, locale),
                    style = cellStyle,
                    modifier = Modifier.weight(1.15f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatMoneyPlain(result.principalAmount, result.currency, locale),
                    style = cellStyle,
                    modifier = Modifier.weight(1.15f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatMoneyPlain(result.totalInterest, result.currency, locale),
                    style = cellStyle,
                    modifier = Modifier.weight(1.05f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatMoneyPlain(result.finalBalance, result.currency, locale),
                    style = cellStyle,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (result.schedule.size > 12) {
                OutlinedButton(
                    onClick = onToggleShowAll,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_toggle_all_periods")
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
