package com.calcsuits.features.ai.domain

import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.usecase.MathExpressionEvaluator
import com.calcsuits.domain.usecase.toMathUiText
import com.calcsuits.features.graph.domain.AdaptedGraphExpression
import com.calcsuits.features.graph.domain.GraphExpressionAdapter
import com.calcsuits.ui.util.formatDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

data class SolverRecord(
    val id: Long,
    val input: String,
    val normalizedExpression: String,
    val headline: UiText,
    val steps: List<UiText>,
    val isError: Boolean = false,
    val graphExpression: AdaptedGraphExpression? = null,
    val fromOcr: Boolean = false,
)

/**
 * Deterministic local math solver reusing `MathExpressionEvaluator` and `MathIrNormalizer`.
 * Synchronizes angular mode (`RAD`/`DEG`) with `SettingsDataStore` and exposes contextual 2D graphing
 * exclusively for graphable functions `f(x)` and equations `LHS = RHS`.
 */
class LocalMathSolverUseCase @Inject constructor() {

    fun solve(
        rawInput: String,
        useRadians: Boolean,
        locale: Locale,
        recordId: Long = System.nanoTime(),
        fromOcr: Boolean = false,
    ): SolverRecord {
        val trimmed = rawInput.trim()
        val ir = MathIrNormalizer.normalize(trimmed)
        val clean = if (ir.ok) ir.normalized else trimmed.replace("²", "^2").replace(",", ".")
        val evaluator = MathExpressionEvaluator(MathContext(14, RoundingMode.HALF_UP), useRadians = useRadians)

        val pctRegex = Regex("""^(-?\d+(?:\.\d+)?)\s*%\s*(?:de|of)\s*(-?\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        pctRegex.matchEntire(clean)?.let { m ->
            val pct = m.groupValues[1].toDouble()
            val total = m.groupValues[2].toDouble()
            val result = total * (pct / 100.0)
            val pctStr = fmtNum(pct, locale)
            val decStr = fmtNum(pct / 100.0, locale)
            val totalStr = fmtNum(total, locale)
            val resStr = fmtNum(result, locale)
            return SolverRecord(
                id = recordId,
                input = trimmed,
                normalizedExpression = clean,
                headline = UiText.DynamicString("= $resStr"),
                steps = listOf(
                    UiText.StringResource(R.string.solver_step_percent_decimal, pctStr, decStr),
                    UiText.StringResource(R.string.solver_step_percent_multiply, decStr, totalStr, resStr),
                ),
                graphExpression = null,
                fromOcr = fromOcr,
            )
        }

        if (clean.contains("=") && clean.contains("x", ignoreCase = true)) {
            return solvePolynomialEquation(
                original = trimmed,
                clean = clean,
                evaluator = evaluator,
                useRadians = useRadians,
                locale = locale,
                recordId = recordId,
                fromOcr = fromOcr,
            )
        }

        // Recognize graphable free-variable functions f(x) without '=' (e.g., "x^2 + 5x + 6", "(3x + 2) / 5")
        if (ir.ok && ir.kind == MathExpressionKind.GRAPHABLE_FUNCTION_OF_X) {
            val graphExpr = GraphExpressionAdapter.adaptForContextualGraph(clean, useRadians)
            if (graphExpr != null) {
                val sampleSteps = buildList {
                    add(UiText.StringResource(R.string.solver_step_function_graphable, clean))
                    val f0 = runCatching { evaluator.evaluate(clean, 0.0).toDouble() }.getOrNull()?.takeIf { it.isFinite() }
                    val f1 = runCatching { evaluator.evaluate(clean, 1.0).toDouble() }.getOrNull()?.takeIf { it.isFinite() }
                    if (f0 != null) {
                        add(UiText.DynamicString("f(0) = ${fmtNum(f0, locale)}"))
                    }
                    if (f1 != null) {
                        add(UiText.DynamicString("f(1) = ${fmtNum(f1, locale)}"))
                    }
                }
                return SolverRecord(
                    id = recordId,
                    input = trimmed,
                    normalizedExpression = clean,
                    headline = UiText.DynamicString("f(x) = $clean"),
                    steps = sampleSteps,
                    isError = false,
                    graphExpression = graphExpr,
                    fromOcr = fromOcr,
                )
            }
        }

        return runCatching {
            val result = evaluator.evaluate(clean).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
            val formatted = NumberFormat.getNumberInstance(locale).apply {
                isGroupingUsed = false
                minimumFractionDigits = 0
                maximumFractionDigits = 8
            }.format(result)
            val angleStep = if (useRadians) {
                UiText.StringResource(R.string.solver_step_eval_rad)
            } else {
                UiText.StringResource(R.string.solver_step_eval_deg)
            }
            SolverRecord(
                id = recordId,
                input = trimmed,
                normalizedExpression = clean,
                headline = UiText.DynamicString("= $formatted"),
                steps = listOf(
                    angleStep,
                    UiText.StringResource(R.string.solver_step_eval_exact, formatted),
                ),
                graphExpression = null,
                fromOcr = fromOcr,
            )
        }.getOrElse { err ->
            SolverRecord(
                id = recordId,
                input = trimmed,
                normalizedExpression = clean,
                headline = UiText.StringResource(R.string.solver_error_eval_headline),
                steps = listOf(
                    err.toMathUiText(
                        fallback = UiText.StringResource(R.string.solver_error_syntax_fallback)
                    )
                ),
                isError = true,
                graphExpression = null,
                fromOcr = fromOcr,
            )
        }
    }

    private fun solvePolynomialEquation(
        original: String,
        clean: String,
        evaluator: MathExpressionEvaluator,
        useRadians: Boolean,
        locale: Locale,
        recordId: Long,
        fromOcr: Boolean,
    ): SolverRecord {
        val parts = clean.split("=")
        if (parts.size != 2) {
            return SolverRecord(
                id = recordId,
                input = original,
                normalizedExpression = clean,
                headline = UiText.StringResource(R.string.solver_error_invalid_equation),
                steps = listOf(UiText.StringResource(R.string.solver_error_single_equals)),
                isError = true,
                graphExpression = null,
                fromOcr = fromOcr,
            )
        }
        val polyExpr = "(${parts[0].trim()}) - (${parts[1].trim()})"
        val graphExpr = GraphExpressionAdapter.adaptForContextualGraph(clean, useRadians)

        return runCatching {
            val f0 = evaluator.evaluate(polyExpr, 0.0).toDouble()
            val f1 = evaluator.evaluate(polyExpr, 1.0).toDouble()
            val fm1 = evaluator.evaluate(polyExpr, -1.0).toDouble()
            val f2 = evaluator.evaluate(polyExpr, 2.0).toDouble()

            val c = f0
            val a = (f1 + fm1 - 2.0 * c) / 2.0
            val b = (f1 - fm1) / 2.0
            val check2 = a * 4.0 + b * 2.0 + c

            if (abs(check2 - f2) >= 1e-6) {
                return SolverRecord(
                    id = recordId,
                    input = original,
                    normalizedExpression = clean,
                    headline = UiText.StringResource(R.string.solver_error_solve_headline),
                    steps = listOf(UiText.StringResource(R.string.solver_error_poly_degree)),
                    isError = true,
                    graphExpression = graphExpr,
                    fromOcr = fromOcr,
                )
            }

            if (abs(a) < 1e-9) {
                if (abs(b) < 1e-9) {
                    val msg = if (abs(c) < 1e-9) {
                        UiText.StringResource(R.string.solver_identity_infinite)
                    } else {
                        UiText.StringResource(R.string.solver_no_solution)
                    }
                    SolverRecord(
                        id = recordId,
                        input = original,
                        normalizedExpression = clean,
                        headline = msg,
                        steps = listOf(UiText.StringResource(R.string.solver_step_reduced_form, fmtNum(c, locale))),
                        graphExpression = graphExpr,
                        fromOcr = fromOcr,
                    )
                } else {
                    val x = -c / b
                    val bStr = fmtNum(b, locale)
                    val cStr = fmtNum(c, locale)
                    val xStr = fmtNum(x, locale)
                    SolverRecord(
                        id = recordId,
                        input = original,
                        normalizedExpression = clean,
                        headline = UiText.DynamicString("x = $xStr"),
                        steps = listOf(
                            UiText.StringResource(R.string.solver_step_linear_standard, bStr, cStr),
                            UiText.StringResource(R.string.solver_step_linear_solve, cStr, bStr, xStr),
                        ),
                        graphExpression = graphExpr,
                        fromOcr = fromOcr,
                    )
                }
            } else {
                val disc = b * b - 4.0 * a * c
                val aStr = fmtNum(a, locale)
                val bStr = fmtNum(b, locale)
                val cStr = fmtNum(c, locale)
                val discStr = fmtNum(disc, locale)
                val steps = mutableListOf<UiText>(
                    UiText.StringResource(R.string.solver_step_quad_standard, aStr, bStr, cStr),
                    UiText.StringResource(R.string.solver_step_quad_discriminant, discStr),
                )
                when {
                    disc > 1e-9 -> {
                        val x1 = (-b + sqrt(disc)) / (2.0 * a)
                        val x2 = (-b - sqrt(disc)) / (2.0 * a)
                        val x1Str = fmtNum(x1, locale)
                        val x2Str = fmtNum(x2, locale)
                        steps.add(UiText.StringResource(R.string.solver_step_quad_x1, x1Str))
                        steps.add(UiText.StringResource(R.string.solver_step_quad_x2, x2Str))
                        SolverRecord(
                            id = recordId,
                            input = original,
                            normalizedExpression = clean,
                            headline = UiText.DynamicString("x₁ = $x1Str,  x₂ = $x2Str"),
                            steps = steps,
                            graphExpression = graphExpr,
                            fromOcr = fromOcr,
                        )
                    }
                    abs(disc) <= 1e-9 -> {
                        val x = -b / (2.0 * a)
                        val xStr = fmtNum(x, locale)
                        steps.add(UiText.StringResource(R.string.solver_step_quad_double, xStr))
                        SolverRecord(
                            id = recordId,
                            input = original,
                            normalizedExpression = clean,
                            headline = UiText.StringResource(R.string.solver_headline_double_root, xStr),
                            steps = steps,
                            graphExpression = graphExpr,
                            fromOcr = fromOcr,
                        )
                    }
                    else -> {
                        val real = -b / (2.0 * a)
                        val imag = sqrt(-disc) / abs(2.0 * a)
                        val complexStr = "x = ${fmtNum(real, locale)} ± ${fmtNum(imag, locale)}i"
                        steps.add(UiText.StringResource(R.string.solver_step_quad_complex))
                        steps.add(UiText.DynamicString(complexStr))
                        SolverRecord(
                            id = recordId,
                            input = original,
                            normalizedExpression = clean,
                            headline = UiText.DynamicString(complexStr),
                            steps = steps,
                            graphExpression = graphExpr,
                            fromOcr = fromOcr,
                        )
                    }
                }
            }
        }.getOrElse { err ->
            SolverRecord(
                id = recordId,
                input = original,
                normalizedExpression = clean,
                headline = UiText.StringResource(R.string.solver_error_solve_headline),
                steps = listOf(
                    err.toMathUiText(
                        fallback = UiText.StringResource(R.string.solver_error_unsupported_equation)
                    )
                ),
                isError = true,
                graphExpression = graphExpr,
                fromOcr = fromOcr,
            )
        }
    }

    private fun fmtNum(v: Double, locale: Locale): String {
        val cleaned = if (abs(v) < 1e-12) 0.0 else v
        val formatted = formatDecimal(cleaned, locale, minFraction = 0, maxFraction = 6)
        return if (formatted == "-0") "0" else formatted
    }
}
