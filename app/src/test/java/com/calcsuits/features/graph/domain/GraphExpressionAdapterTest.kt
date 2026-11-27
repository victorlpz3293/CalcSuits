package com.calcsuits.features.graph.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GraphExpressionAdapterTest {

    @Test
    fun domainSafeValidationFixesSinglePoint1234Bug() {
        // Previously failed when GraphScreen only probed x = 1.234
        assertNull(GraphExpressionAdapter.validateGraphExpression("sqrt(-x)", useRadians = false))
        assertNull(GraphExpressionAdapter.validateGraphExpression("ln(-x)", useRadians = false))
        assertNull(GraphExpressionAdapter.validateGraphExpression("asin(x)", useRadians = false))
        assertNull(GraphExpressionAdapter.validateGraphExpression("acos(x)", useRadians = false))
        assertNull(GraphExpressionAdapter.validateGraphExpression("1/(x-1.234)", useRadians = false))
    }

    @Test
    fun validationStillRejectsRealSyntaxErrors() {
        assertNotNull(GraphExpressionAdapter.validateGraphExpression("", useRadians = false))
        assertNotNull(GraphExpressionAdapter.validateGraphExpression("sin(", useRadians = false))
        assertNotNull(GraphExpressionAdapter.validateGraphExpression("2 + @", useRadians = false))
    }

    @Test
    fun adaptsFunctionsAndEquationsAndHidesNonGraphableExpressions() {
        val fn = GraphExpressionAdapter.adaptForContextualGraph("sin(x) + x^2", useRadians = false)
        assertNotNull(fn)
        assertFalse(fn!!.isEquation)
        assertEquals("sin(x) + x^2", fn.evaluableExpression)

        val eq = GraphExpressionAdapter.adaptForContextualGraph("2x + 5 = 15", useRadians = false)
        assertNotNull(eq)
        assertTrue(eq!!.isEquation)
        assertEquals("(2 * x + 5) - (15)", eq.evaluableExpression)

        // Non-graphable expressions MUST return null
        assertNull(GraphExpressionAdapter.adaptForContextualGraph("125 + 35 * 2", useRadians = false))
        assertNull(GraphExpressionAdapter.adaptForContextualGraph("15% de 250", useRadians = false))
        assertNull(GraphExpressionAdapter.adaptForContextualGraph("2x + 3y = 0", useRadians = false))
    }

    @Test
    fun curveSamplingRespectsRadVsDegSetting() {
        val degSamples = GraphExpressionAdapter.sampleCurve(
            evaluableExpression = "sin(x)",
            minX = 90.0,
            maxX = 90.0 + 1e-6,
            useRadians = false,
            sampleCount = 2,
        )
        val radSamples = GraphExpressionAdapter.sampleCurve(
            evaluableExpression = "sin(x)",
            minX = 90.0,
            maxX = 90.0 + 1e-6,
            useRadians = true,
            sampleCount = 2,
        )
        assertEquals(1.0, degSamples.first().y!!, 1e-6)
        assertTrue(
            "RAD sin(90) (~0.894) must differ from DEG sin(90) (1.0)",
            abs(radSamples.first().y!! - 0.89399666) < 1e-5,
        )
    }
}
