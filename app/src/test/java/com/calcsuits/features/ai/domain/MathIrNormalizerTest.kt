package com.calcsuits.features.ai.domain

import com.calcsuits.domain.usecase.MathExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.MathContext
import java.math.RoundingMode

class MathIrNormalizerTest {

    private val degEvaluator = MathExpressionEvaluator(MathContext(14, RoundingMode.HALF_UP), useRadians = false)
    private val radEvaluator = MathExpressionEvaluator(MathContext(14, RoundingMode.HALF_UP), useRadians = true)

    @Test
    fun unaryMinusPowerPrecedenceMatchesMathConventionAndEvaluator() {
        val negPower = MathIrNormalizer.normalize("-2^2")
        assertTrue(negPower.ok)
        assertEquals("-(2^2)", negPower.normalized)
        assertEquals(-4.0, degEvaluator.evaluate(negPower.normalized).toDouble(), 1e-9)

        val parenNegPower = MathIrNormalizer.normalize("(-2)^2")
        assertTrue(parenNegPower.ok)
        assertEquals("(-2)^2", parenNegPower.normalized)
        assertEquals(4.0, degEvaluator.evaluate(parenNegPower.normalized).toDouble(), 1e-9)

        val rightAssocPower = MathIrNormalizer.normalize("2^{3^{2}}")
        assertTrue(rightAssocPower.ok)
        assertEquals("2^(3^2)", rightAssocPower.normalized)
        assertEquals(512.0, degEvaluator.evaluate(rightAssocPower.normalized).toDouble(), 1e-9)
    }

    @Test
    fun normalizesLatexFractionsRootsAndTrigIntoValidCalcSuitsSyntax() {
        val frac = MathIrNormalizer.normalize("""\[\frac{3x+2}{5}\]""")
        assertTrue(frac.ok)
        assertEquals(MathExpressionKind.GRAPHABLE_FUNCTION_OF_X, frac.kind)
        assertEquals("(3 * x + 2) / 5", frac.normalized)
        assertEquals(2.2, degEvaluator.evaluate(frac.normalized, 3.0).toDouble(), 1e-9)

        val root = MathIrNormalizer.normalize("""\sqrt{25+144}""")
        assertTrue(root.ok)
        assertEquals(MathExpressionKind.NUMERIC_EVALUABLE, root.kind)
        assertEquals("sqrt(25 + 144)", root.normalized)
        assertEquals(13.0, degEvaluator.evaluate(root.normalized).toDouble(), 1e-9)

        val trig = MathIrNormalizer.normalize("""\sin(30)+\cos(60)""")
        assertTrue(trig.ok)
        assertEquals("sin(30) + cos(60)", trig.normalized)
        assertEquals(1.0, degEvaluator.evaluate(trig.normalized).toDouble(), 1e-9)
        // Verify RAD differs from DEG on the exact same normalized expression
        val radVal = radEvaluator.evaluate(trig.normalized).toDouble()
        assertFalse(kotlin.math.abs(radVal - 1.0) < 1e-3)
    }

    @Test
    fun normalizesDarkModeOcrCaretLatexVariantCleanly() {
        val ocrDarkMode = MathIrNormalizer.normalize("""\[x^{ \wedge}2-5x+6=0\]""")
        assertTrue(ocrDarkMode.ok)
        assertEquals(MathExpressionKind.EQUATION_IN_X, ocrDarkMode.kind)
        assertEquals("x^2 - 5 * x + 6 = 0", ocrDarkMode.normalized)
    }

    @Test
    fun classifiesPercentagesAndMultiVariableExpressionsCorrectly() {
        val pct = MathIrNormalizer.normalize("""15\% \text{ de } 250""")
        assertTrue(pct.ok)
        assertEquals(MathExpressionKind.PERCENTAGE_OF, pct.kind)
        assertEquals("15% de 250", pct.normalized)

        val multiVarEq = MathIrNormalizer.normalize("""\[2x^{2}+3y=0\]""")
        assertTrue(multiVarEq.ok)
        assertEquals(MathExpressionKind.UNSUPPORTED_EQUATION, multiVarEq.kind)
        assertEquals(listOf("x", "y"), multiVarEq.variables)

        val multiVarExpr = MathIrNormalizer.normalize("""2x + 3y""")
        assertTrue(multiVarExpr.ok)
        assertEquals(MathExpressionKind.UNSUPPORTED_VARIABLES, multiVarExpr.kind)
        assertEquals(listOf("x", "y"), multiVarExpr.variables)
    }

    @Test
    fun rejectsMalformedSyntaxWithStructuredParseError() {
        val bad = MathIrNormalizer.normalize("""\frac{1+}{""")
        assertFalse(bad.ok)
        assertEquals(MathExpressionKind.PARSE_ERROR, bad.kind)
    }
}
