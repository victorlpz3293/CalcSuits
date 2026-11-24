package com.calcsuits.domain.usecase

import com.calcsuits.domain.model.converter.ConversionUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.abs

class ConvertUnitsUseCase @Inject constructor() {

    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    operator fun invoke(
        value: Double,
        from: ConversionUnit,
        to: ConversionUnit,
        fixedDecimals: Int? = null
    ): String {
        if (from.symbol == to.symbol) return formatDouble(value, fixedDecimals)

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
            return formatDouble(converted, fixedDecimals)
        }

        if (value == 0.0) {
            return if (fixedDecimals != null) {
                BigDecimal.ZERO.setScale(fixedDecimals, RoundingMode.HALF_UP).toPlainString()
            } else {
                "0"
            }
        }

        val baseValue = BigDecimal.valueOf(value)
            .multiply(BigDecimal.valueOf(from.factorToBase), mathContext)
        val result = baseValue.divide(BigDecimal.valueOf(to.factorToBase), mathContext)
        return formatBigDecimal(result, fixedDecimals)
    }

    private fun formatDouble(value: Double, fixedDecimals: Int?): String {
        if (!value.isFinite()) return "Error"
        val cleaned = if (abs(value) < 1e-12) 0.0 else value
        return formatBigDecimal(BigDecimal.valueOf(cleaned), fixedDecimals)
    }

    private fun formatBigDecimal(value: BigDecimal, fixedDecimals: Int?): String {
        if (fixedDecimals != null) {
            return value.setScale(fixedDecimals, RoundingMode.HALF_UP).toPlainString()
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        val absVal = value.abs()
        if (absVal >= BigDecimal("1E11") || (absVal > BigDecimal.ZERO && absVal < BigDecimal("1E-6"))) {
            return "%.6E".format(java.util.Locale.US, value.toDouble())
                .replace(Regex("0+E"), "E")
                .replace(".E", "E")
        }
        return value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
