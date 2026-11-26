package com.calcsuits.features.ai.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.usecase.MathExpressionEvaluator
import com.calcsuits.domain.usecase.toMathUiText
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.formatDecimal
import com.calcsuits.ui.util.rememberCurrentLocale
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

private data class SolverRecord(
    val input: String,
    val headline: UiText,
    val steps: List<UiText>,
    val isError: Boolean = false
)

private data class QuickSolverPreset(
    @StringRes val titleRes: Int,
    val tag: UiText,
    val expression: UiText
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(onMenuClick: () -> Unit) {
    val extColors = CalcSuitsTheme.colors
    val locale = rememberCurrentLocale()
    var query by rememberSaveable { mutableStateOf("") }
    val records = remember { mutableStateListOf<SolverRecord>() }
    val listState = rememberLazyListState()
    val evaluator = remember { MathExpressionEvaluator(MathContext(14, RoundingMode.HALF_UP), useRadians = false) }
    val percentExample = stringResource(R.string.solver_example_percent)
    val isImeVisible = WindowInsets.isImeVisible

    val quickPresets = remember {
        listOf(
            QuickSolverPreset(
                titleRes = R.string.solver_preset_quadratic_title,
                tag = UiText.DynamicString("ax² + bx + c = 0"),
                expression = UiText.DynamicString("x^2 - 5x + 6 = 0")
            ),
            QuickSolverPreset(
                titleRes = R.string.solver_preset_linear_title,
                tag = UiText.DynamicString("ax + b = c"),
                expression = UiText.DynamicString("3x + 7 = 22")
            ),
            QuickSolverPreset(
                titleRes = R.string.solver_preset_percent_title,
                tag = UiText.StringResource(R.string.solver_preset_percent_tag),
                expression = UiText.StringResource(R.string.solver_example_percent)
            )
        )
    }

    val examples = remember(percentExample) {
        listOf(
            "x^2 - 5x + 6 = 0",
            "3x + 7 = 22",
            percentExample,
            "(12 + 8) * sin(30) + sqrt(144)",
            "2^10 - 5!"
        )
    }

    fun solveAndAppend(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        records.add(solveDeterministic(trimmed, evaluator, locale))
        query = ""
    }

    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) {
            listState.animateScrollToItem(records.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.solver),
                subtitle = stringResource(R.string.solver_subtitle),
                onMenuClick = onMenuClick
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("solver_input"),
                            placeholder = { Text(stringResource(R.string.solver_input_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { solveAndAppend(query) }),
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { solveAndAppend(query) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("solver_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_solver_submit)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.solver_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.solver_hero_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        quickPresets.forEachIndexed { idx, preset ->
                            val presetTitle = stringResource(preset.titleRes)
                            val presetExpression = preset.expression.asString()
                            Surface(
                                onClick = { solveAndAppend(presetExpression) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("solver_quick_card_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = presetTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = extColors.technicalContainer
                                            ) {
                                                Text(
                                                    text = preset.tag.asString(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                    color = extColors.onTechnicalContainer
                                                )
                                            }
                                        }
                                        Text(
                                            text = presetExpression,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = stringResource(R.string.cd_solver_try_preset, presetTitle),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records) { rec ->
                        CalcCard(
                            containerColor = if (rec.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                            borderColor = if (rec.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = rec.input,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = rec.headline.asString(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (rec.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                )
                                if (rec.steps.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    rec.steps.forEachIndexed { idx, step ->
                                        Text(
                                            text = "${idx + 1}. ${step.asString()}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isImeVisible) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    items(examples) { ex ->
                        Surface(
                            onClick = { solveAndAppend(ex) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ex,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun solveDeterministic(
    input: String,
    evaluator: MathExpressionEvaluator,
    locale: Locale
): SolverRecord {
    val clean = input.trim().replace("²", "^2").replace(",", ".")

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
            input = input,
            headline = UiText.DynamicString("= $resStr"),
            steps = listOf(
                UiText.StringResource(R.string.solver_step_percent_decimal, pctStr, decStr),
                UiText.StringResource(R.string.solver_step_percent_multiply, decStr, totalStr, resStr)
            )
        )
    }

    if (clean.contains("=") && clean.contains("x", ignoreCase = true)) {
        return solvePolynomialEquation(input, clean, evaluator, locale)
    }

    return runCatching {
        val result = evaluator.evaluate(clean).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val formatted = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 8
        }.format(result)
        SolverRecord(
            input = input,
            headline = UiText.DynamicString("= $formatted"),
            steps = listOf(
                UiText.StringResource(R.string.solver_step_eval_deg),
                UiText.StringResource(R.string.solver_step_eval_exact, formatted)
            )
        )
    }.getOrElse { err ->
        SolverRecord(
            input = input,
            headline = UiText.StringResource(R.string.solver_error_eval_headline),
            steps = listOf(
                err.toMathUiText(
                    fallback = UiText.StringResource(R.string.solver_error_syntax_fallback)
                )
            ),
            isError = true
        )
    }
}

private fun solvePolynomialEquation(
    original: String,
    clean: String,
    evaluator: MathExpressionEvaluator,
    locale: Locale
): SolverRecord {
    val parts = clean.split("=")
    if (parts.size != 2) {
        return SolverRecord(
            input = original,
            headline = UiText.StringResource(R.string.solver_error_invalid_equation),
            steps = listOf(UiText.StringResource(R.string.solver_error_single_equals)),
            isError = true
        )
    }
    val polyExpr = "(${parts[0]}) - (${parts[1]})"
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
                input = original,
                headline = UiText.StringResource(R.string.solver_error_solve_headline),
                steps = listOf(UiText.StringResource(R.string.solver_error_poly_degree)),
                isError = true
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
                    input = original,
                    headline = msg,
                    steps = listOf(UiText.StringResource(R.string.solver_step_reduced_form, fmtNum(c, locale)))
                )
            } else {
                val x = -c / b
                val bStr = fmtNum(b, locale)
                val cStr = fmtNum(c, locale)
                val xStr = fmtNum(x, locale)
                SolverRecord(
                    input = original,
                    headline = UiText.DynamicString("x = $xStr"),
                    steps = listOf(
                        UiText.StringResource(R.string.solver_step_linear_standard, bStr, cStr),
                        UiText.StringResource(R.string.solver_step_linear_solve, cStr, bStr, xStr)
                    )
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
                UiText.StringResource(R.string.solver_step_quad_discriminant, discStr)
            )
            when {
                disc > 1e-9 -> {
                    val x1 = (-b + sqrt(disc)) / (2.0 * a)
                    val x2 = (-b - sqrt(disc)) / (2.0 * a)
                    val x1Str = fmtNum(x1, locale)
                    val x2Str = fmtNum(x2, locale)
                    steps.add(UiText.StringResource(R.string.solver_step_quad_x1, x1Str))
                    steps.add(UiText.StringResource(R.string.solver_step_quad_x2, x2Str))
                    SolverRecord(original, UiText.DynamicString("x₁ = $x1Str,  x₂ = $x2Str"), steps)
                }
                abs(disc) <= 1e-9 -> {
                    val x = -b / (2.0 * a)
                    val xStr = fmtNum(x, locale)
                    steps.add(UiText.StringResource(R.string.solver_step_quad_double, xStr))
                    SolverRecord(
                        input = original,
                        headline = UiText.StringResource(R.string.solver_headline_double_root, xStr),
                        steps = steps
                    )
                }
                else -> {
                    val real = -b / (2.0 * a)
                    val imag = sqrt(-disc) / abs(2.0 * a)
                    val complexStr = "x = ${fmtNum(real, locale)} ± ${fmtNum(imag, locale)}i"
                    steps.add(UiText.StringResource(R.string.solver_step_quad_complex))
                    steps.add(UiText.DynamicString(complexStr))
                    SolverRecord(original, UiText.DynamicString(complexStr), steps)
                }
            }
        }
    }.getOrElse { err ->
        SolverRecord(
            input = original,
            headline = UiText.StringResource(R.string.solver_error_solve_headline),
            steps = listOf(
                err.toMathUiText(
                    fallback = UiText.StringResource(R.string.solver_error_unsupported_equation)
                )
            ),
            isError = true
        )
    }
}

private fun fmtNum(v: Double, locale: Locale): String {
    val cleaned = if (abs(v) < 1e-12) 0.0 else v
    val formatted = formatDecimal(cleaned, locale, minFraction = 0, maxFraction = 6)
    return if (formatted == "-0") "0" else formatted
}
