package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.converter.ConversionUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class ConvertUnitsUseCase @Inject constructor() {

    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    operator fun invoke(
        value: Double,
        from: ConversionUnit,
        to: ConversionUnit,
        fixedDecimals: Int? = null,
        locale: Locale = Locale.getDefault()
    ): String {
        if (from.symbol == to.symbol) return formatDouble(value, fixedDecimals, locale)

        if (from.isTemperature && to.isTemperature) {
            val celsius = when (from.symbol) {
                "°C" -> value
                "°F" -> (value - 32.0) * 5.0 / 9.0
                "K" -> value - 273.15
                else -> value
            }
            val converted = when (to.symbol) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> celsius
            }
            return formatDouble(converted, fixedDecimals, locale)
        }

        if (value == 0.0) {
            return formatBigDecimal(BigDecimal.ZERO, fixedDecimals, locale)
        }

        val baseValue = BigDecimal.valueOf(value)
            .multiply(BigDecimal.valueOf(from.factorToBase), mathContext)
        val result = baseValue.divide(BigDecimal.valueOf(to.factorToBase), mathContext)
        return formatBigDecimal(result, fixedDecimals, locale)
    }

    private fun formatDouble(value: Double, fixedDecimals: Int?, locale: Locale): String {
        if (!value.isFinite()) return ""
        val cleaned = if (abs(value) < 1e-12) 0.0 else value
        return formatBigDecimal(BigDecimal.valueOf(cleaned), fixedDecimals, locale)
    }

    private fun formatBigDecimal(value: BigDecimal, fixedDecimals: Int?, locale: Locale): String {
        val numberFormat = NumberFormat.getNumberInstance(locale).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = fixedDecimals != null
        }
        if (fixedDecimals != null) {
            numberFormat.minimumFractionDigits = fixedDecimals
            numberFormat.maximumFractionDigits = fixedDecimals
            return numberFormat.format(value.setScale(fixedDecimals, RoundingMode.HALF_UP))
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            numberFormat.minimumFractionDigits = 0
            numberFormat.maximumFractionDigits = 0
            return numberFormat.format(0)
        }
        val absVal = value.abs()
        if (absVal >= BigDecimal("1E11") || (absVal > BigDecimal.ZERO && absVal < BigDecimal("1E-6"))) {
            return "%.6E".format(locale, value.toDouble())
                .replace(Regex("0+E"), "E")
                .replace(Regex("[.,]E"), "E")
        }
        val normalized = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        numberFormat.minimumFractionDigits = 0
        numberFormat.maximumFractionDigits = 6
        return numberFormat.format(normalized)
    }
}
