package com.calcsuits.domain.usecase

import java.math.MathContext
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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
    fun degVsRadTrigonometryEvaluatesCorrectly() {
        assertEquals(0.5, evaluator.evaluate("sin(30)").toDouble(), 1e-9)
        assertEquals(0.5, evaluator.evaluate("cos(60)").toDouble(), 1e-9)
        assertEquals(1.0, evaluator.evaluate("tan(45)").toDouble(), 1e-9)
        assertEquals(30.0, evaluator.evaluate("asin(0.5)").toDouble(), 1e-9)
        assertEquals(60.0, evaluator.evaluate("acos(0.5)").toDouble(), 1e-9)
        assertEquals(45.0, evaluator.evaluate("atan(1)").toDouble(), 1e-9)

        assertEquals(0.5, radEvaluator.evaluate("sin(pi/6)").toDouble(), 1e-9)
        assertEquals(0.5, radEvaluator.evaluate("cos(pi/3)").toDouble(), 1e-9)
        assertEquals(1.0, radEvaluator.evaluate("tan(pi/4)").toDouble(), 1e-9)
        assertEquals(Math.PI / 6.0, radEvaluator.evaluate("asin(0.5)").toDouble(), 1e-9)
        assertEquals(Math.PI / 3.0, radEvaluator.evaluate("acos(0.5)").toDouble(), 1e-9)
        assertEquals(Math.PI / 4.0, radEvaluator.evaluate("atan(1)").toDouble(), 1e-9)

        // Same numeric input differs between DEG and RAD
        assertEquals(1.0, evaluator.evaluate("sin(90)").toDouble(), 1e-9)
        assertEquals(kotlin.math.sin(90.0), radEvaluator.evaluate("sin(90)").toDouble(), 1e-9)
    }

    @Test
    fun percentSymbolActsAsBinaryModuloAndRejectsUnaryUsage() {
        assertEquals("1", evaluator.evaluate("10 % 3").stripTrailingZeros().toPlainString())
        assertEquals("2.5", evaluator.evaluate("7.5 % 5").stripTrailingZeros().toPlainString())

        assertMathError(MathError.ModuloByZero) { evaluator.evaluate("10 % 0") }
        assertMathError(MathError.ModuloByZero) { evaluator.evaluate("10 mod 0") }
        assertMathError(MathError.MissingOperands("%")) { evaluator.evaluate("50%") }
    }

    @Test
    fun factorialBoundaryAndInvalidValuesAreHandled() {
        assertEquals("1", evaluator.evaluate("0!").stripTrailingZeros().toPlainString())
        assertEquals("1", evaluator.evaluate("1!").stripTrailingZeros().toPlainString())
        assertEquals("2432902008176640000", evaluator.evaluate("20!").stripTrailingZeros().toPlainString())

        assertMathError(MathError.FactorialRange) { evaluator.evaluate("21!") }
        assertMathError(MathError.FactorialRange) { evaluator.evaluate("100!") }
        assertMathError(MathError.FactorialRange) { evaluator.evaluate("(-1)!") }
        assertMathError(MathError.FactorialRange) { evaluator.evaluate("3.5!") }
    }

    @Test
    fun scientificNotationIsEvaluatedAccurately() {
        assertEquals("1000", evaluator.evaluate("1e3").stripTrailingZeros().toPlainString())
        assertEquals("0.025", evaluator.evaluate("2.5E-2").stripTrailingZeros().toPlainString())
        assertEquals("15500", evaluator.evaluate("1.5e+4 + 5e2").stripTrailingZeros().toPlainString())
        assertEquals("30", evaluator.evaluate("1e2 * 3e-1").stripTrailingZeros().toPlainString())
        assertEquals(2.0 * Math.E, evaluator.evaluate("2e").toDouble(), 1e-9)
    }

    @Test
    fun divisionByZeroReturnsTypedArithmeticError() {
        assertMathError(MathError.DivisionByZero) { evaluator.evaluate("5/0") }
        assertMathError(MathError.DivisionByZero) { evaluator.evaluate("10/(3-3)") }
        assertMathError(MathError.DivisionByZero) { radEvaluator.evaluate("1/x", 0.0) }
    }

    @Test
    fun unbalancedParenthesesReturnTypedEvaluationError() {
        assertMathError(MathError.UnbalancedParentheses) { evaluator.evaluate("5+(2") }
        assertMathError(MathError.UnbalancedParentheses) { evaluator.evaluate("5+2)") }
        assertMathError(MathError.UnbalancedParentheses) { evaluator.evaluate("((1+2)") }
        assertMathError(MathError.UnbalancedParentheses) { evaluator.evaluate(")(") }
    }

    @Test
    fun invalidExpressionsAndDomainViolationsReturnTypedErrors() {
        assertMathError(MathError.EmptyExpression) { evaluator.evaluate("") }
        assertMathError(MathError.EmptyExpression) { evaluator.evaluate("   ") }
        assertMathError(MathError.MissingOperands("/")) { evaluator.evaluate("5/") }
        assertMathError(MathError.NegativeRoot) { evaluator.evaluate("sqrt(-1)") }
        assertMathError(MathError.UndefinedTangentAtDegrees("90.0")) { evaluator.evaluate("tan(90)") }
        assertMathError(MathError.UndefinedTangentAtDegrees("270.0")) { evaluator.evaluate("tan(270)") }
        assertMathError(MathError.UndefinedTangent) { radEvaluator.evaluate("tan(pi/2)") }
        assertMathError(MathError.LogPositive) { evaluator.evaluate("log(0)") }
        assertMathError(MathError.LnPositive) { evaluator.evaluate("ln(-5)") }
        assertMathError(MathError.DomainFnMinusOneOne("asin")) { evaluator.evaluate("asin(2)") }
        assertMathError(MathError.DomainFnMinusOneOne("acos")) { evaluator.evaluate("acos(-1.5)") }
        assertMathError(MathError.InvalidCharacter("$")) { evaluator.evaluate("5$2") }
        assertMathError(MathError.InvalidDecimal) { evaluator.evaluate("1.2.3") }
        assertMathError(MathError.UnknownIdentifier("foo")) { evaluator.evaluate("foo(2)") }
        assertMathError(MathError.UndefinedResult) { evaluator.evaluate("10^400") }
    }

    private fun assertMathError(expected: MathError, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: MathEvaluationException) {
            assertEquals(expected, e.error)
        } catch (e: MathArithmeticException) {
            assertEquals(expected, e.error)
        }
    }
}
