package com.calcsuits.domain.usecase

import java.math.MathContext
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MathExpressionEvaluatorTest {
    private lateinit var evaluator: MathExpressionEvaluator
    private lateinit var radEvaluator: MathExpressionEvaluator

    @Before
    fun setUp() {
        evaluator = MathExpressionEvaluator(MathContext(16, RoundingMode.HALF_UP), useRadians = false)
        radEvaluator = MathExpressionEvaluator(MathContext(16, RoundingMode.HALF_UP), useRadians = true)
    }

    @Test
    fun basicArithmeticAndPrecedenceAreEvaluated() {
        assertEquals("25", evaluator.evaluate("5*5").stripTrailingZeros().toPlainString())
        assertEquals("14", evaluator.evaluate("2+3*4").stripTrailingZeros().toPlainString())
        assertEquals("1", evaluator.evaluate("10 mod 3").stripTrailingZeros().toPlainString())
        assertEquals("512", evaluator.evaluate("2^3^2").stripTrailingZeros().toPlainString())
    }

    @Test
    fun parenthesesNegativeNumbersAndImplicitMultiplicationAreEvaluated() {
        assertEquals("-6", evaluator.evaluate("(-2)*3").stripTrailingZeros().toPlainString())
        assertEquals("20", evaluator.evaluate("(2+3)*4").stripTrailingZeros().toPlainString())
        assertEquals("14", evaluator.evaluate("2(3+4)").stripTrailingZeros().toPlainString())
        assertEquals("120", evaluator.evaluate("5!").stripTrailingZeros().toPlainString())
    }

    @Test
    fun scientificFunctionsAndVariablesAreEvaluated() {
        assertEquals("0.5", evaluator.evaluate("sin(30)").setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
        assertEquals("0", evaluator.evaluate("sin(180)").stripTrailingZeros().toPlainString())
        assertEquals("10", evaluator.evaluate("log(10000000000)").stripTrailingZeros().toPlainString())
        assertEquals("12", evaluator.evaluate("sqrt(144)").stripTrailingZeros().toPlainString())
        assertEquals("4", radEvaluator.evaluate("x^2", -2.0).stripTrailingZeros().toPlainString())
        assertEquals("1", radEvaluator.evaluate("exp(x)", 0.0).stripTrailingZeros().toPlainString())
    }

    @Test
    fun invalidExpressionsReturnControlledFailures() {
        assertFails { evaluator.evaluate("5/") }
        assertFails { evaluator.evaluate("5/0") }
        assertFails { evaluator.evaluate("sqrt(-1)") }
        assertFails { evaluator.evaluate("tan(90)") }
        assertFails { evaluator.evaluate("5+(2") }
        assertFails { evaluator.evaluate("5$2") }
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Se esperaba una excepción controlada")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        } catch (expected: ArithmeticException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }
}
