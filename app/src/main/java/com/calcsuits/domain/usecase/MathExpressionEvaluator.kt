package com.calcsuits.domain.usecase

import java.math.BigDecimal
import java.math.MathContext
import java.util.ArrayDeque
import kotlin.math.E
import kotlin.math.PI
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

/**
 * Safe deterministic evaluator for arithmetic and scientific expressions.
 * Supports operator precedence, right-associative exponentiation, implicit multiplication,
 * constants (pi/π, e), factorial, modulo, and configurable DEG/RAD trigonometry.
 */
class MathExpressionEvaluator(
    private val mathContext: MathContext,
    private val useRadians: Boolean = false
) {
    private val functions = setOf(
        "sin", "cos", "tan",
        "asin", "acos", "atan",
        "sinh", "cosh", "tanh",
        "log", "ln", "sqrt", "abs", "exp", "fact"
    )
    private val precedence = mapOf(
        "+" to 1,
        "-" to 1,
        "*" to 2,
        "/" to 2,
        "%" to 2,
        "^" to 3,
        "u-" to 4
    )
    private val rightAssociative = setOf("^", "u-")

    fun evaluate(expression: String): BigDecimal = evaluateInternal(expression, variableX = null)

    fun evaluate(expression: String, x: Double): BigDecimal = evaluateInternal(expression, variableX = x)

    private fun evaluateInternal(expression: String, variableX: Double?): BigDecimal {
        if (expression.isBlank()) {
            throw MathEvaluationException(MathError.EmptyExpression, "Expression is empty")
        }
        val normalized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")
        val rawTokens = tokenize(normalized, variableX)
        if (rawTokens.isEmpty()) {
            throw MathEvaluationException(MathError.NoValidTokens, "Expression contains no valid tokens")
        }
        val tokens = insertImplicitMultiplication(rawTokens)
        return evaluateRpn(shuntingYard(tokens))
    }

    private fun tokenize(expression: String, variableX: Double?): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dots = 0
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                        if (expression[i] == '.') dots++
                        i++
                    }
                    if (i < expression.length && (expression[i] == 'e' || expression[i] == 'E')) {
                        val nextIdx = i + 1
                        val hasExpDigits = if (nextIdx < expression.length && (expression[nextIdx] == '+' || expression[nextIdx] == '-')) {
                            nextIdx + 1 < expression.length && expression[nextIdx + 1].isDigit()
                        } else {
                            nextIdx < expression.length && expression[nextIdx].isDigit()
                        }
                        if (hasExpDigits) {
                            i++
                            if (i < expression.length && (expression[i] == '+' || expression[i] == '-')) i++
                            while (i < expression.length && expression[i].isDigit()) i++
                        }
                    }
                    if (dots > 1) {
                        throw MathEvaluationException(MathError.InvalidDecimal, "Invalid decimal number")
                    }
                    val number = expression.substring(start, i)
                    if (number == "." || number.toBigDecimalOrNull() == null) {
                        throw MathEvaluationException(MathError.InvalidNumber, "Invalid number")
                    }
                    tokens.add(number)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < expression.length && expression[i].isLetter()) i++
                    val word = expression.substring(start, i).lowercase()
                    when {
                        word == "x" && variableX != null -> tokens.add(variableX.toString())
                        word == "pi" -> tokens.add(PI.toString())
                        word == "e" -> tokens.add(E.toString())
                        word == "mod" -> tokens.add("%")
                        word in functions -> tokens.add(word)
                        else -> throw MathEvaluationException(
                            MathError.UnknownIdentifier(word),
                            "Unknown function or identifier: $word"
                        )
                    }
                }
                c == '!' -> {
                    tokens.add("!")
                    i++
                }
                c in "+-*/%^()" -> {
                    if (c == '-' && (tokens.isEmpty() || tokens.last() == "(" || tokens.last() in precedence)) {
                        tokens.add("u-")
                    } else if (c == '+' && (tokens.isEmpty() || tokens.last() == "(" || tokens.last() in precedence)) {
                        // Unary plus is a no-op
                    } else {
                        tokens.add(c.toString())
                    }
                    i++
                }
                else -> throw MathEvaluationException(
                    MathError.InvalidCharacter(c.toString()),
                    "Invalid character: $c"
                )
            }
        }
        return tokens
    }

    private fun insertImplicitMultiplication(tokens: List<String>): List<String> {
        if (tokens.size < 2) return tokens
        val result = mutableListOf<String>()
        for (idx in tokens.indices) {
            val current = tokens[idx]
            if (idx > 0) {
                val prev = tokens[idx - 1]
                val prevIsValue = prev.isNumber() || prev == ")" || prev == "!"
                val currentStartsValue = current.isNumber() || current == "(" || current in functions
                if (prevIsValue && currentStartsValue) {
                    result.add("*")
                }
            }
            result.add(current)
        }
        return result
    }

    private fun shuntingYard(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val operators = ArrayDeque<String>()
        var openParentheses = 0
        for (token in tokens) {
            when {
                token.isNumber() -> output.add(token)
                token == "!" -> output.add("fact")
                token in functions -> operators.addLast(token)
                token == "(" -> {
                    operators.addLast(token)
                    openParentheses++
                }
                token == ")" -> {
                    if (openParentheses <= 0) {
                        throw MathEvaluationException(MathError.UnbalancedParentheses, "Unbalanced parentheses")
                    }
                    while (operators.isNotEmpty() && operators.peekLast() != "(") {
                        output.add(operators.removeLast())
                    }
                    if (operators.isEmpty()) {
                        throw MathEvaluationException(MathError.UnbalancedParentheses, "Unbalanced parentheses")
                    }
                    operators.removeLast()
                    openParentheses--
                    if (operators.isNotEmpty() && operators.peekLast() in functions) {
                        output.add(operators.removeLast())
                    }
                }
                token in precedence -> {
                    while (operators.isNotEmpty() && operators.peekLast() != "(") {
                        val top = operators.peekLast()
                        if (top in functions) {
                            output.add(operators.removeLast())
                        } else if (top in precedence) {
                            val topPrec = precedence.getValue(top)
                            val curPrec = precedence.getValue(token)
                            val shouldPop = if (token in rightAssociative) topPrec > curPrec else topPrec >= curPrec
                            if (shouldPop) output.add(operators.removeLast()) else break
                        } else break
                    }
                    operators.addLast(token)
                }
                else -> throw MathEvaluationException(
                    MathError.UnknownOperator(token),
                    "Unknown function or operator: $token"
                )
            }
        }
        if (openParentheses != 0) {
            throw MathEvaluationException(MathError.UnbalancedParentheses, "Unbalanced parentheses")
        }
        while (operators.isNotEmpty()) {
            val op = operators.removeLast()
            if (op == "(") {
                throw MathEvaluationException(MathError.UnbalancedParentheses, "Unbalanced parentheses")
            }
            output.add(op)
        }
        return output
    }

    private fun evaluateRpn(rpn: List<String>): BigDecimal {
        val stack = ArrayDeque<BigDecimal>()
        for (token in rpn) {
            when {
                token.isNumber() -> stack.addLast(token.toBigDecimal())
                token == "u-" -> {
                    if (stack.isEmpty()) {
                        throw MathEvaluationException(MathError.MissingNegationOperand, "Missing operand for negation")
                    }
                    stack.addLast(stack.removeLast().negate(mathContext))
                }
                token in functions -> {
                    if (stack.isEmpty()) {
                        throw MathEvaluationException(MathError.MissingOperand(token), "Missing operand for $token")
                    }
                    val x = stack.removeLast().toDouble()
                    val result = evaluateFunction(token, x)
                    if (!result.isFinite()) {
                        throw MathArithmeticException(MathError.UndefinedResult, "Undefined result")
                    }
                    val cleaned = if (abs(result) < 1e-13) 0.0 else result
                    stack.addLast(cleaned.toBigDecimal(mathContext))
                }
                token in precedence -> {
                    if (stack.size < 2) {
                        throw MathEvaluationException(MathError.MissingOperands(token), "Missing operands for $token")
                    }
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    val result = when (token) {
                        "+" -> a.add(b, mathContext)
                        "-" -> a.subtract(b, mathContext)
                        "*" -> a.multiply(b, mathContext)
                        "/" -> {
                            if (b.compareTo(BigDecimal.ZERO) == 0) {
                                throw MathArithmeticException(MathError.DivisionByZero, "Division by zero")
                            }
                            a.divide(b, mathContext)
                        }
                        "%" -> {
                            if (b.compareTo(BigDecimal.ZERO) == 0) {
                                throw MathArithmeticException(MathError.ModuloByZero, "Modulo by zero")
                            }
                            a.remainder(b, mathContext)
                        }
                        "^" -> {
                            val value = a.toDouble().pow(b.toDouble())
                            if (!value.isFinite()) {
                                throw MathArithmeticException(MathError.UndefinedResult, "Undefined result")
                            }
                            value.toBigDecimal(mathContext)
                        }
                        else -> throw MathEvaluationException(MathError.UnsupportedOperator, "Unsupported operator")
                    }
                    stack.addLast(result)
                }
                else -> throw MathEvaluationException(MathError.UnknownToken(token), "Unknown token: $token")
            }
        }
        if (stack.size != 1) {
            throw MathEvaluationException(MathError.IncompleteExpression, "Incomplete expression")
        }
        return stack.removeLast()
    }

    private fun evaluateFunction(fn: String, x: Double): Double {
        val angle = if (useRadians) x else Math.toRadians(x)
        return when (fn) {
            "sin" -> sin(angle)
            "cos" -> cos(angle)
            "tan" -> {
                if (!useRadians) {
                    val mod = ((x % 180.0) + 180.0) % 180.0
                    if (abs(mod - 90.0) <= 1e-9) {
                        throw MathArithmeticException(
                            MathError.UndefinedTangentAtDegrees(x.toString()),
                            "Undefined tangent at $x°"
                        )
                    }
                } else if (abs(cos(angle)) <= 1e-12) {
                    throw MathArithmeticException(MathError.UndefinedTangent, "Undefined tangent")
                }
                tan(angle)
            }
            "asin" -> {
                if (x !in -1.0..1.0) {
                    throw MathEvaluationException(MathError.DomainFnMinusOneOne("asin"), "asin domain: [-1, 1]")
                }
                val r = asin(x)
                if (useRadians) r else Math.toDegrees(r)
            }
            "acos" -> {
                if (x !in -1.0..1.0) {
                    throw MathEvaluationException(MathError.DomainFnMinusOneOne("acos"), "acos domain: [-1, 1]")
                }
                val r = acos(x)
                if (useRadians) r else Math.toDegrees(r)
            }
            "atan" -> {
                val r = atan(x)
                if (useRadians) r else Math.toDegrees(r)
            }
            "sinh" -> sinh(x)
            "cosh" -> cosh(x)
            "tanh" -> tanh(x)
            "log" -> {
                if (x <= 0.0) {
                    throw MathEvaluationException(MathError.LogPositive, "Logarithm requires a positive value")
                }
                log10(x)
            }
            "ln" -> {
                if (x <= 0.0) {
                    throw MathEvaluationException(MathError.LnPositive, "Natural logarithm requires a positive value")
                }
                ln(x)
            }
            "sqrt" -> {
                if (x < 0.0) {
                    throw MathEvaluationException(MathError.NegativeRoot, "Negative square root")
                }
                sqrt(x)
            }
            "abs" -> abs(x)
            "exp" -> exp(x)
            "fact" -> {
                val n = x.roundToLong()
                if (abs(x - n.toDouble()) >= 1e-9 || n !in 0L..20L) {
                    throw MathEvaluationException(MathError.FactorialRange, "Factorial requires an integer between 0 and 20")
                }
                var acc = 1L
                for (k in 2L..n) acc *= k
                acc.toDouble()
            }
            else -> throw MathEvaluationException(MathError.UnsupportedFunction(fn), "Unsupported function: $fn")
        }
    }

    private fun String.isNumber(): Boolean = toBigDecimalOrNull() != null
}
