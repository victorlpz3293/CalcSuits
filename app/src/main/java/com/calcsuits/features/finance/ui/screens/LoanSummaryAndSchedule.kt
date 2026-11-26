package com.calcsuits.features.finance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.calcsuits.domain.model.finance.LoanTermUnit
import com.calcsuits.domain.model.finance.RateConvention
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.formatMoney
import com.calcsuits.ui.util.formatMoneyPlain
import com.calcsuits.ui.util.formatRatePercent
import com.calcsuits.ui.util.formatShortDate
import java.util.Locale

@Composable
internal fun LoanSummaryCard(
    result: LoanResult,
    locale: Locale
) {
    val currency = result.currency
    val installmentNoun = stringResource(result.paymentFrequency.installmentNounRes())
    val installmentContextualNoun = stringResource(result.paymentFrequency.installmentContextualNounRes())
    val heroCaption = when (result.system) {
        AmortizationSystem.GERMAN -> stringResource(
            R.string.finance_loan_hero_german_fmt,
            installmentContextualNoun
        )
        else -> stringResource(
            R.string.finance_loan_hero_french_fmt,
            installmentNoun
        )
    }

    val periodsSummary = pluralStringResource(
        R.plurals.finance_term_summary_periods,
        result.schedule.size,
        result.schedule.size
    )
    val termSummary = if (result.termUnit == LoanTermUnit.YEARS) {
        val yearsSummary = pluralStringResource(
            R.plurals.finance_term_years_count,
            result.termValue,
            result.termValue
        )
        stringResource(
            R.string.finance_term_summary_years_fmt,
            yearsSummary,
            periodsSummary
        )
    } else {
        periodsSummary
    }

    val rateConventionShort = stringResource(
        if (result.rateConvention == RateConvention.NOMINAL_PER_PAYMENT_PERIOD) {
            R.string.finance_rate_convention_short_tna
        } else {
            R.string.finance_rate_convention_short_tea
        }
    )

    CalcResultCard {
        Column(
            modifier = Modifier.testTag("loan_summary_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = heroCaption.uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
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
                if (result.system == AmortizationSystem.GERMAN && result.schedule.size > 1) {
                    Text(
                        text = stringResource(
                            R.string.finance_loan_german_range,
                            formatMoney(result.lastPayment, currency, locale)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Payment Plan Context Metadata
            Text(
                text = stringResource(R.string.finance_plan_summary_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            result.issuedAtMillis?.let { issuedAt ->
                SummaryMetadataRow(
                    label = stringResource(R.string.finance_plan_issue_date),
                    value = formatShortDate(issuedAt, locale)
                )
            }
            SummaryMetadataRow(
                label = stringResource(R.string.finance_system_label),
                value = "${stringResource(result.system.descriptiveTitleRes())} · ${stringResource(result.system.academicSubtitleRes())}"
            )
            if (result.downPayment > 0.0) {
                SummaryMetadataRow(
                    label = stringResource(R.string.finance_loan_requested_amount),
                    value = formatMoney(result.requestedAmount, currency, locale),
                    monospace = true
                )
                SummaryMetadataRow(
                    label = stringResource(R.string.finance_loan_down_payment_short),
                    value = formatMoney(result.downPayment, currency, locale),
                    monospace = true
                )
            }
            SummaryMetadataRow(
                label = stringResource(R.string.finance_loan_financed_principal),
                value = formatMoney(result.principalAmount, currency, locale),
                monospace = true
            )
            SummaryMetadataRow(
                label = stringResource(R.string.finance_annual_rate),
                value = "${formatRatePercent(result.annualRatePercent, locale, maxFraction = 4)} ($rateConventionShort)",
                monospace = true
            )
            SummaryMetadataRow(
                label = stringResource(R.string.finance_payment_frequency),
                value = stringResource(result.paymentFrequency.labelRes())
            )
            SummaryMetadataRow(
                label = stringResource(R.string.finance_plan_term_label),
                value = termSummary
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Key Financial Metrics
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_initial_installment_fmt, installmentContextualNoun),
                value = formatMoney(result.firstPayment, currency, locale)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_final_installment_fmt, installmentContextualNoun),
                value = formatMoney(result.lastPayment, currency, locale)
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
                label = stringResource(R.string.finance_loan_periodic_rate),
                value = formatRatePercent(result.periodicRate * 100.0, locale, maxFraction = 4)
            )
            FinanceMetricRow(
                label = stringResource(R.string.finance_loan_effective_rate),
                value = formatRatePercent(result.effectiveAnnualRate * 100.0, locale, maxFraction = 4)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (result.downPayment > 0.0) {
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_installments_paid),
                    value = formatMoney(result.totalPayment, currency, locale),
                    modifier = Modifier.testTag("loan_total_installments_paid")
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_outflow_with_down_payment),
                    value = formatMoney(result.totalOutflowWithDownPayment(), currency, locale),
                    emphasize = true,
                    modifier = Modifier.testTag("loan_total_outflow_with_down_payment")
                )
            } else {
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_payment),
                    value = formatMoney(result.totalPayment, currency, locale),
                    emphasize = true
                )
            }
        }
    }
}

@Composable
private fun SummaryMetadataRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.25f)
        )
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
internal fun ScheduleCaptionBanner(
    result: LoanResult,
    isCardLayout: Boolean
) {
    val periodsLabel = pluralStringResource(
        R.plurals.finance_term_summary_periods,
        result.schedule.size,
        result.schedule.size
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.finance_schedule_currency_caption,
                    result.currency.code,
                    result.currency.symbol,
                    periodsLabel
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (isCardLayout) {
                Text(
                    text = stringResource(R.string.finance_schedule_tap_expand_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Mobile-first expandable card for a single installment (Compact & Medium widths).
 * Zero horizontal scrolling; Total Installment carries the highest visual hierarchy.
 */
@Composable
internal fun InstallmentExpandableCard(
    row: AmortizationRow,
    currency: FinanceCurrency,
    locale: Locale,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extColors = CalcSuitsTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("loan_row_card_${row.period}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Collapsed header: Installment # + Prominent Total Installment + Expand icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#${row.period}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.finance_col_payment),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(row.payment, currency, locale),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.cd_finance_collapse_installment
                            else R.string.cd_finance_expand_installment,
                            row.period
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Collapsed 3-column summary metrics: Interest | Principal Repayment | Closing Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactInstallmentMetric(
                    label = stringResource(R.string.finance_col_interest),
                    value = formatMoneyPlain(row.interest, currency, locale),
                    valueColor = extColors.technicalAccent,
                    modifier = Modifier.weight(1f)
                )
                CompactInstallmentMetric(
                    label = stringResource(R.string.finance_col_principal),
                    value = formatMoneyPlain(row.principal, currency, locale),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                CompactInstallmentMetric(
                    label = stringResource(R.string.finance_col_closing_balance),
                    value = formatMoneyPlain(row.balance, currency, locale),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Expanded detailed breakdown
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_period_full),
                        value = "${row.period}"
                    )
                    row.dueDateMillis?.let { dueDate ->
                        ExpandedDetailRow(
                            label = stringResource(R.string.finance_col_due_date),
                            value = formatShortDate(dueDate, locale),
                            monospace = false
                        )
                    }
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_opening_balance),
                        value = formatMoney(row.openingBalance, currency, locale)
                    )
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_interest),
                        value = formatMoney(row.interest, currency, locale),
                        valueColor = extColors.technicalAccent
                    )
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_principal),
                        value = formatMoney(row.principal, currency, locale)
                    )
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_payment),
                        value = formatMoney(row.payment, currency, locale),
                        bold = true,
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    ExpandedDetailRow(
                        label = stringResource(R.string.finance_col_closing_balance),
                        value = formatMoney(row.balance, currency, locale),
                        bold = true
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInstallmentMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpandedDetailRow(
    label: String,
    value: String,
    monospace: Boolean = true,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
            ),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Medium-width 2-column row of expandable installment cards (600dp..839dp).
 */
@Composable
internal fun InstallmentCardPairRow(
    left: AmortizationRow,
    right: AmortizationRow?,
    currency: FinanceCurrency,
    locale: Locale,
    expandedPeriods: Set<Int>,
    onTogglePeriod: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        InstallmentExpandableCard(
            row = left,
            currency = currency,
            locale = locale,
            expanded = left.period in expandedPeriods,
            onToggleExpand = { onTogglePeriod(left.period) },
            modifier = Modifier.weight(1f)
        )
        if (right != null) {
            InstallmentExpandableCard(
                row = right,
                currency = currency,
                locale = locale,
                expanded = right.period in expandedPeriods,
                onToggleExpand = { onTogglePeriod(right.period) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Traditional 6-column financial table header for Expanded widths (>= 840dp).
 * Columns: N.º de cuota | Saldo inicial | Intereses | Amortización | Cuota total | Saldo final.
 */
@Composable
internal fun ExpandedScheduleTableHeader(result: LoanResult) {
    val periodsLabel = pluralStringResource(
        R.plurals.finance_term_summary_periods,
        result.schedule.size,
        result.schedule.size
    )
    // Opaque surface prevents scrolling table rows from bleeding through behind the sticky header.
    Surface(
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_expanded_table_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.finance_schedule_currency_caption,
                    result.currency.code,
                    result.currency.symbol,
                    periodsLabel
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell(text = stringResource(R.string.finance_col_period_short), weight = 0.55f, alignEnd = false)
                TableHeaderCell(text = stringResource(R.string.finance_col_opening_balance), weight = 1.15f)
                TableHeaderCell(text = stringResource(R.string.finance_col_interest), weight = 1.0f)
                TableHeaderCell(text = stringResource(R.string.finance_col_principal), weight = 1.05f)
                TableHeaderCell(text = stringResource(R.string.finance_col_payment), weight = 1.1f)
                TableHeaderCell(text = stringResource(R.string.finance_col_closing_balance), weight = 1.15f)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    alignEnd: Boolean = true
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight)
    )
}

@Composable
internal fun ExpandedScheduleTableRow(
    row: AmortizationRow,
    currency: FinanceCurrency,
    locale: Locale,
    isAlternate: Boolean
) {
    val extColors = CalcSuitsTheme.colors
    val rowBg = if (isAlternate) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 12.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .testTag("loan_table_row_${row.period}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${row.period}",
                style = cellStyle.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.55f)
            )
            Text(
                text = formatMoneyPlain(row.openingBalance, currency, locale),
                style = cellStyle,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.15f)
            )
            Text(
                text = formatMoneyPlain(row.interest, currency, locale),
                style = cellStyle,
                color = extColors.technicalAccent,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.0f)
            )
            Text(
                text = formatMoneyPlain(row.principal, currency, locale),
                style = cellStyle,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.05f)
            )
            Text(
                text = formatMoneyPlain(row.payment, currency, locale),
                style = cellStyle.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                text = formatMoneyPlain(row.balance, currency, locale),
                style = cellStyle.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.15f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/**
 * Totals footer sourced strictly from [LoanResult] domain totals (never re-summed in UI).
 */
@Composable
internal fun ScheduleTotalsFooterCard(
    result: LoanResult,
    locale: Locale,
    layoutMode: ScheduleLayoutMode,
    showAllPeriods: Boolean,
    onToggleShowAll: () -> Unit,
    nonLazyCappedRowCount: Int? = null
) {
    val currency = result.currency
    val isExpandedTable = layoutMode == ScheduleLayoutMode.EXPANDED_TABLE
    val shape = if (isExpandedTable) {
        RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    } else {
        RoundedCornerShape(14.dp)
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("loan_schedule_totals_footer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isExpandedTable) {
                val cellStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.finance_schedule_totals),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "—",
                        style = cellStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.15f)
                    )
                    Text(
                        text = formatMoneyPlain(result.totalInterest, currency, locale),
                        style = cellStyle,
                        color = CalcSuitsTheme.colors.technicalAccent,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.0f)
                    )
                    Text(
                        text = formatMoneyPlain(result.principalAmount, currency, locale),
                        style = cellStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.05f)
                    )
                    Text(
                        text = formatMoneyPlain(result.totalPayment, currency, locale),
                        style = cellStyle,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = formatMoneyPlain(result.finalBalance, currency, locale),
                        style = cellStyle,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.15f)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.finance_schedule_totals_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_total_interest),
                    value = formatMoney(result.totalInterest, currency, locale),
                    valueColor = CalcSuitsTheme.colors.technicalAccent
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_loan_principal_amortized),
                    value = formatMoney(result.principalAmount, currency, locale)
                )
                FinanceMetricRow(
                    label = stringResource(R.string.finance_col_closing_balance),
                    value = formatMoney(result.finalBalance, currency, locale)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FinanceMetricRow(
                    label = stringResource(
                        if (result.downPayment > 0.0) {
                            R.string.finance_total_installments_paid
                        } else {
                            R.string.finance_total_payment
                        }
                    ),
                    value = formatMoney(result.totalPayment, currency, locale),
                    emphasize = true
                )
            }

            if (nonLazyCappedRowCount != null) {
                Text(
                    text = stringResource(
                        R.string.finance_schedule_compact_height_cap_note,
                        nonLazyCappedRowCount,
                        result.schedule.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("loan_schedule_compact_height_cap_note")
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
