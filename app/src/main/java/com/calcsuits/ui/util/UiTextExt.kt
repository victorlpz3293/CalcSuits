package com.calcsuits.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.model.finance.FinanceCurrency
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> {
        if (args.isEmpty()) {
            stringResource(resId)
        } else {
            stringResource(resId, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
        }
    }
    is UiText.PluralResource -> {
        if (args.isEmpty()) {
            pluralStringResource(resId, count)
        } else {
            pluralStringResource(resId, count, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
        }
    }
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> {
        if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
        }
    }
    is UiText.PluralResource -> {
        if (args.isEmpty()) {
            context.resources.getQuantityString(resId, count)
        } else {
            context.resources.getQuantityString(resId, count, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
        }
    }
}

@Composable
fun rememberCurrentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return configuration.locales[0] ?: Locale.getDefault()
}

fun formatCurrency(value: Double, locale: Locale): String {
    val currencyLocale = resolveCurrencyLocale(locale)
    val formatter = NumberFormat.getCurrencyInstance(currencyLocale)
    val symbol = formatter.currency?.getSymbol(currencyLocale)
    if (symbol.isNullOrBlank() || symbol == "¤" || formatter.currency?.currencyCode == "XXX") {
        val numberFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$ ${numberFormat.format(value)}"
    }
    return formatter.format(value)
}

fun currencySymbolFor(locale: Locale): String {
    val currencyLocale = resolveCurrencyLocale(locale)
    val symbol = NumberFormat.getCurrencyInstance(currencyLocale).currency?.getSymbol(currencyLocale)
    return if (symbol.isNullOrBlank() || symbol == "¤") "$" else symbol
}

fun formatPercentInt(ratio: Double, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).apply {
        maximumFractionDigits = 0
    }.format(ratio)

fun formatDecimal(value: Double, locale: Locale, minFraction: Int = 0, maxFraction: Int = 6): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = minFraction
        maximumFractionDigits = maxFraction
        isGroupingUsed = false
    }.format(value)

/**
 * Formats a monetary value using the user-selected [FinanceCurrency] independently of the
 * device/app regional currency. The [locale] is used solely for decimal/grouping punctuation.
 */
fun formatMoney(value: Double, currency: FinanceCurrency, locale: Locale): String =
    "${currency.symbol} ${formatMoneyPlain(value, currency, locale)}"

/**
 * Formats a monetary value to [FinanceCurrency.fractionDigits] without repeating the symbol
 * (ideal for dense tabular views like amortization schedules where currency is stated in the header).
 */
fun formatMoneyPlain(value: Double, currency: FinanceCurrency, locale: Locale): String {
    val safeValue = if (!value.isFinite() || abs(value) < 1e-9) 0.0 else value
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = true
        minimumFractionDigits = currency.fractionDigits
        maximumFractionDigits = currency.fractionDigits
    }
    return formatter.format(safeValue)
}

/**
 * Formats a monetary magnitude concisely for chart Y-axis ticks (e.g. "0", "75k", "600k", "1.2M")
 * so large loan balances never truncate with ellipsis on narrow phone screens.
 */
fun formatCompactMoneyAxis(value: Double, currency: FinanceCurrency, locale: Locale): String {
    val safeValue = if (!value.isFinite() || abs(value) < 1e-9) 0.0 else value
    val absValue = abs(safeValue)
    val sign = if (safeValue < 0.0) "-" else ""

    fun formatScaled(scaled: Double, suffix: String, maxFrac: Int): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = maxFrac
        }
        return "$sign${nf.format(scaled)}$suffix"
    }

    return when {
        absValue == 0.0 -> "0"
        absValue >= 1_000_000_000.0 -> formatScaled(absValue / 1_000_000_000.0, "B", if (absValue >= 100_000_000_000.0) 0 else 1)
        absValue >= 1_000_000.0 -> formatScaled(absValue / 1_000_000.0, "M", if (absValue >= 100_000_000.0) 0 else 1)
        absValue >= 1_000.0 -> formatScaled(absValue / 1_000.0, "k", if (absValue >= 100_000.0) 0 else 1)
        else -> {
            val nf = NumberFormat.getNumberInstance(locale).apply {
                roundingMode = RoundingMode.HALF_UP
                isGroupingUsed = false
                minimumFractionDigits = 0
                maximumFractionDigits = if (currency.fractionDigits == 0 || absValue >= 100.0) 0 else 1
            }
            "$sign${nf.format(absValue)}"
        }
    }
}

/**
 * Formats a percentage value (already expressed in percent, e.g. 15.0 -> "15%", 0.9489 -> "0.9489%").
 */
fun formatRatePercent(percentValue: Double, locale: Locale, maxFraction: Int = 4): String {
    val safeValue = if (!percentValue.isFinite() || abs(percentValue) < 1e-12) 0.0 else percentValue
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = maxFraction
    }
    return "${formatter.format(safeValue)}%"
}

/**
 * Formats a canonical UTC civil-date epoch timestamp (`00:00:00.000 UTC`) as a concise localized
 * calendar date (e.g., "Nov 26, 2026" or "26 nov 2026"), invariant to the device default timezone.
 */
fun formatShortDate(epochMillis: Long, locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMillis))

private fun resolveCurrencyLocale(locale: Locale): Locale {
    if (locale.country.isNotEmpty()) return locale
    return when (locale.language.lowercase(Locale.ROOT)) {
        "es" -> Locale.forLanguageTag("es-US")
        else -> Locale.US
    }
}
