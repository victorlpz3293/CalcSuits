package com.calcsuits.features.graph.domain

import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.usecase.MathArithmeticException
import com.calcsuits.domain.usecase.MathError
import com.calcsuits.domain.usecase.MathEvaluationException
import com.calcsuits.domain.usecase.MathExpressionEvaluator
import com.calcsuits.domain.usecase.toMathUiText
import com.calcsuits.features.ai.domain.MathExpressionKind
import com.calcsuits.features.ai.domain.MathIrNormalizer
import java.math.MathContext

data class AdaptedGraphExpression(
    val displayLabel: String,
    val evaluableExpression: String,
    val isEquation: Boolean,
)

data class GraphCurveSample(
    val x: Double,
    val y: Double?,
)

/**
 * Pure domain adapter & domain-safe validator for 2D graphing.
 *
 * Fixes Phase 4.1 GraphScreen defects:
 * - Adapts both `f(x)` and equations `LHS = RHS` -> `(LHS) - (RHS)`.
 * - Hides contextual graphing for non-graphable expressions (pure constants, percentages, multi-variable).
 * - Validates syntax across multiple domain probe points instead of only `x = 1.234`, allowing
 *   restricted-domain functions like `sqrt(-x)`, `ln(-x)`, `asin(x)`, `acos(x)`, `1/(x-1.234)`.
 * - Pre-samples curve points outside `Canvas.onDraw`.
 */
object GraphExpressionAdapter {
    private val PROBE_POINTS = doubleArrayOf(
        -10.0, -3.5, -1.0, -0.5, -0.1, 0.0, 0.1, 0.5, 1.0, 1.234, 3.5, 10.0
    )

    private fun isDomainOnlyError(error: MathError): Boolean = when (error) {
        MathError.DivisionByZero,
        MathError.ModuloByZero,
        MathError.UndefinedTangent,
        is MathError.UndefinedTangentAtDegrees,
        MathError.DomainMinusOneOne,
        is MathError.DomainFnMinusOneOne,
        MathError.DomainPositive,
        MathError.LogPositive,
        MathError.LnPositive,
        MathError.NegativeRoot,
        MathError.UndefinedResult,
        MathError.FactorialRange,
        MathError.ResultOutOfRange -> true
        else -> false
    }

    /**
     * Adapts a raw or AST-normalized Solver expression into a plottable expression in `x`,
     * or returns `null` when the expression is not graphable in 2D (e.g., constants, percentages,
     * multi-variable equations, or invalid syntax).
     */
    fun adaptForContextualGraph(
        rawExpression: String,
        useRadians: Boolean,
    ): AdaptedGraphExpression? {
        val trimmed = rawExpression.trim()
        if (trimmed.isBlank()) return null

        val ir = MathIrNormalizer.normalize(trimmed)
        if (!ir.ok) return null

        return when (ir.kind) {
            MathExpressionKind.GRAPHABLE_FUNCTION_OF_X -> {
                val evalExpr = ir.normalized
                val err = validateGraphExpression(evalExpr, useRadians)
                if (err != null) null else AdaptedGraphExpression(
                    displayLabel = "f(x) = $evalExpr",
                    evaluableExpression = evalExpr,
                    isEquation = false,
                )
            }
            MathExpressionKind.EQUATION_IN_X -> {
                val parts = ir.normalized.split("=")
                if (parts.size != 2) return null
                val lhs = parts[0].trim()
                val rhs = parts[1].trim()
                if (lhs.isEmpty() || rhs.isEmpty()) return null
                val diffExpr = "($lhs) - ($rhs)"
                val err = validateGraphExpression(diffExpr, useRadians)
                if (err != null) null else AdaptedGraphExpression(
                    displayLabel = "f(x) = ($lhs) - ($rhs)  [= 0]",
                    evaluableExpression = diffExpr,
                    isEquation = true,
                )
            }
            MathExpressionKind.NUMERIC_EVALUABLE,
            MathExpressionKind.PERCENTAGE_OF,
            MathExpressionKind.UNSUPPORTED_EQUATION,
            MathExpressionKind.UNSUPPORTED_VARIABLES,
            MathExpressionKind.PARSE_ERROR -> null
        }
    }

    /**
     * Normalizes user-typed expression (superscripts, decimal commas, and optional `LHS = RHS`)
     * into an evaluable expression for standalone `GraphScreen` or contextual graph.
     */
    fun prepareEvaluableExpression(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""
        val ir = MathIrNormalizer.normalize(trimmed)
        if (ir.ok) {
            if (ir.kind == MathExpressionKind.EQUATION_IN_X) {
                val parts = ir.normalized.split("=")
                if (parts.size == 2) {
                    return "(${parts[0].trim()}) - (${parts[1].trim()})"
                }
            }
            return ir.normalized
        }
        return trimmed.replace("²", "^2").replace(",", ".")
    }

    /**
     * Validates an expression without relying solely on `x = 1.234`.
     *
     * Rules:
     * - Blank expression -> `graph_error_empty`.
     * - Any structural/syntax error (`InvalidCharacter`, `UnknownIdentifier`, `UnbalancedParentheses`, etc.)
     *   fails immediately.
     * - Domain-only errors (e.g. `sqrt(-x)` at `x = 1.234` or `asin(x)` at `x = 1.234`) do NOT fail
     *   if the expression evaluates cleanly at any probe point in `[minX, maxX]` or standard domain probes.
     */
    fun validateGraphExpression(
        expression: String,
        useRadians: Boolean,
        minX: Double = -7.0,
        maxX: Double = 7.0,
    ): UiText? {
        val prepared = prepareEvaluableExpression(expression)
        if (prepared.isBlank()) {
            return UiText.StringResource(R.string.graph_error_empty)
        }
        val evaluator = MathExpressionEvaluator(MathContext(12), useRadians = useRadians)

        val probes = buildList {
            for (p in PROBE_POINTS) add(p)
            if (maxX > minX) {
                val steps = 8
                for (i in 0..steps) {
                    add(minX + (maxX - minX) * i / steps)
                }
            }
        }

        var lastDomainError: Throwable? = null
        for (x in probes) {
            try {
                val value = evaluator.evaluate(prepared, x).toDouble()
                if (value.isFinite()) {
                    return null
                }
            } catch (e: MathEvaluationException) {
                if (!isDomainOnlyError(e.error)) {
                    return e.toMathUiText()
                }
                lastDomainError = e
            } catch (e: MathArithmeticException) {
                if (!isDomainOnlyError(e.error)) {
                    return e.toMathUiText()
                }
                lastDomainError = e
            } catch (e: Throwable) {
                return e.toMathUiText()
            }
        }

        // Expression is syntactically valid (only domain errors occurred on all probes);
        // allow plotting so panning into its valid domain works seamlessly.
        return if (lastDomainError != null) null else null
    }

    /**
     * Pre-computes curve sample coordinates outside `Canvas.onDraw`.
     */
    fun sampleCurve(
        evaluableExpression: String,
        minX: Double,
        maxX: Double,
        useRadians: Boolean,
        sampleCount: Int,
    ): List<GraphCurveSample> {
        if (evaluableExpression.isBlank() || maxX <= minX || sampleCount <= 0) {
            return emptyList()
        }
        val evaluator = MathExpressionEvaluator(MathContext(12), useRadians = useRadians)
        val result = ArrayList<GraphCurveSample>(sampleCount + 1)
        for (i in 0..sampleCount) {
            val x = minX + (maxX - minX) * i / sampleCount
            val y = runCatching { evaluator.evaluate(evaluableExpression, x).toDouble() }
                .getOrNull()
                ?.takeIf { it.isFinite() }
            result.add(GraphCurveSample(x = x, y = y))
        }
        return result
    }
}
