package com.calcsuits.domain.usecase

import com.calcsuits.model.CalculatorOperation
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

class CalculateExpressionUseCase @Inject constructor() {

    private val mathContext = MathContext(16, RoundingMode.HALF_UP)

    fun evaluate(
        expression: String,
        useRadians: Boolean = false,
        maxDecimals: Int = 10
    ): Result<String> = try {
        val evaluator = MathExpressionEvaluator(mathContext, useRadians)
        val result = evaluator.evaluate(expression)
        Result.success(formatResult(result, maxDecimals))
    } catch (e: Exception) {
        Result.failure(e)
    }

    operator fun invoke(
        number1: String,
        number2: String? = null,
        operation: CalculatorOperation,
        maxDecimals: Int = 10
    ): Result<String> = try {
        val n1 = number1.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número 1 inválido")

        val result = when (operation) {
            is CalculatorOperation.Add -> {
                val n2 = number2?.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número 2 requerido")
                n1.add(n2, mathContext)
            }
            is CalculatorOperation.Subtract -> {
                val n2 = number2?.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número 2 requerido")
                n1.subtract(n2, mathContext)
            }
            is CalculatorOperation.Multiply -> {
                val n2 = number2?.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número 2 requerido")
                n1.multiply(n2, mathContext)
            }
            is CalculatorOperation.Divide -> {
                val n2 = number2?.toBigDecimalOrNull() ?: throw IllegalArgumentException("Número 2 requerido")
                if (n2.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("División por cero")
                n1.divide(n2, mathContext)
            }
            is CalculatorOperation.Power -> {
                val n2 = number2?.toDoubleOrNull() ?: throw IllegalArgumentException("Exponente requerido")
                val value = n1.toDouble().pow(n2)
                if (!value.isFinite()) throw ArithmeticException("Resultado fuera de rango")
                BigDecimal(value, mathContext)
            }
            is CalculatorOperation.Modulo -> {
                val n2 = number2?.toBigDecimalOrNull() ?: throw IllegalArgumentException("Divisor requerido")
                if (n2.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("Módulo por cero")
                n1.remainder(n2, mathContext)
            }
        }

        Result.success(formatResult(result, maxDecimals))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun calculateUnary(
        number: String,
        function: String,
        useRadians: Boolean = false,
        maxDecimals: Int = 10
    ): Result<String> = try {
        val valDouble = number.toDoubleOrNull() ?: throw IllegalArgumentException("Número inválido")
        val angle = if (useRadians) valDouble else Math.toRadians(valDouble)

        val resDouble = when (function) {
            "sin" -> sin(angle)
            "cos" -> cos(angle)
            "tan" -> {
                if (!useRadians) {
                    val mod = ((valDouble % 180.0) + 180.0) % 180.0
                    if (abs(mod - 90.0) <= 1e-9) throw ArithmeticException("Tangente indefinida")
                } else if (abs(cos(angle)) <= 1e-12) {
                    throw ArithmeticException("Tangente indefinida")
                }
                tan(angle)
            }
            "asin" -> {
                if (valDouble !in -1.0..1.0) throw IllegalArgumentException("Dominio [-1, 1]")
                val r = asin(valDouble)
                if (useRadians) r else Math.toDegrees(r)
            }
            "acos" -> {
                if (valDouble !in -1.0..1.0) throw IllegalArgumentException("Dominio [-1, 1]")
                val r = acos(valDouble)
                if (useRadians) r else Math.toDegrees(r)
            }
            "atan" -> {
                val r = atan(valDouble)
                if (useRadians) r else Math.toDegrees(r)
            }
            "sinh" -> sinh(valDouble)
            "cosh" -> cosh(valDouble)
            "tanh" -> tanh(valDouble)
            "log" -> {
                if (valDouble <= 0.0) throw IllegalArgumentException("Dominio > 0")
                log10(valDouble)
            }
            "ln" -> {
                if (valDouble <= 0.0) throw IllegalArgumentException("Dominio > 0")
                ln(valDouble)
            }
            "sqrt" -> {
                if (valDouble < 0.0) throw IllegalArgumentException("Raíz negativa")
                sqrt(valDouble)
            }
            "exp" -> exp(valDouble)
            "inv" -> {
                if (valDouble == 0.0) throw ArithmeticException("División por cero")
                1.0 / valDouble
            }
            "abs" -> abs(valDouble)
            "fact" -> factorial(valDouble).toDouble()
            else -> throw IllegalArgumentException("Función desconocida: $function")
        }

        if (!resDouble.isFinite()) throw ArithmeticException("Resultado no definido")
        val cleaned = if (abs(resDouble) < 1e-13) 0.0 else resDouble

        Result.success(formatResult(cleaned.toBigDecimal(mathContext), maxDecimals))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun constantValue(symbol: String, maxDecimals: Int = 10): String = when (symbol) {
        "π", "pi" -> formatResult(PI.toBigDecimal(mathContext), maxDecimals)
        "e" -> formatResult(E.toBigDecimal(mathContext), maxDecimals)
        else -> "0"
    }

    private fun factorial(x: Double): Long {
        val n = x.roundToLong()
        if (abs(x - n.toDouble()) > 1e-9 || n < 0 || n > 20) {
            throw IllegalArgumentException("Factorial requiere entero 0..20")
        }
        var res = 1L
        for (i in 2L..n) res *= i
        return res
    }

    fun formatResult(result: BigDecimal, maxDecimals: Int = 10): String {
        if (result.compareTo(BigDecimal.ZERO) == 0) return "0"
        val absVal = result.abs()
        val upperLimit = BigDecimal("1E12")
        val lowerLimit = BigDecimal("1E-7")
        if (absVal >= upperLimit || (absVal > BigDecimal.ZERO && absVal < lowerLimit)) {
            return "%.${maxDecimals.coerceIn(2, 8)}E".format(java.util.Locale.US, result.toDouble())
                .replace(Regex("0+E"), "E")
                .replace(".E", "E")
        }
        val rounded = result.setScale(maxDecimals.coerceIn(2, 12), RoundingMode.HALF_UP)
        val stripped = rounded.stripTrailingZeros()
        return if (stripped.compareTo(BigDecimal.ZERO) == 0) "0" else stripped.toPlainString()
    }
}
