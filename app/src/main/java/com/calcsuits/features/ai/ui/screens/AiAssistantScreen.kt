package com.calcsuits.features.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcsuits.domain.usecase.MathExpressionEvaluator
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

private data class SolverRecord(
    val input: String,
    val headline: String,
    val steps: List<String>,
    val isError: Boolean = false
)

private data class QuickSolverPreset(
    val title: String,
    val tag: String,
    val expression: String
)

@Composable
fun AiAssistantScreen(onMenuClick: () -> Unit) {
    val extColors = CalcSuitsTheme.colors
    var query by remember { mutableStateOf("") }
    val records = remember { mutableStateListOf<SolverRecord>() }
    val listState = rememberLazyListState()
    val evaluator = remember { MathExpressionEvaluator(MathContext(14, RoundingMode.HALF_UP), useRadians = false) }

    val quickPresets = listOf(
        QuickSolverPreset(
            title = "Ecuación cuadrática",
            tag = "ax² + bx + c = 0",
            expression = "x^2 - 5x + 6 = 0"
        ),
        QuickSolverPreset(
            title = "Ecuación lineal",
            tag = "ax + b = c",
            expression = "3x + 7 = 22"
        ),
        QuickSolverPreset(
            title = "Porcentaje directo",
            tag = "% de base",
            expression = "15% de 480"
        )
    )

    val examples = listOf(
        "x^2 - 5x + 6 = 0",
        "3x + 7 = 22",
        "15% de 480",
        "(12 + 8) * sin(30) + sqrt(144)",
        "2^10 - 5!"
    )

    fun solveAndAppend(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        records.add(solveDeterministic(trimmed, evaluator))
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
                title = "Solucionador",
                subtitle = "MOTOR DETERMINISTA PASO A PASO",
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
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
                        text = "Solucionador Matemático Local",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Evalúa expresiones científicas completas, porcentajes, ecuaciones lineales y cuadráticas paso a paso sin depender de internet.",
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
                            Surface(
                                onClick = { solveAndAppend(preset.expression) },
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = preset.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = extColors.technicalContainer
                                            ) {
                                                Text(
                                                    text = preset.tag,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                    color = extColors.onTechnicalContainer
                                                )
                                            }
                                        }
                                        Text(
                                            text = preset.expression,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Probar ${preset.title}",
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
                                    text = rec.headline,
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
                                            text = "${idx + 1}. $step",
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

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("solver_input"),
                    placeholder = { Text("Ej. x^2 - 5x + 6 = 0 o 25% de 400") },
                    singleLine = true,
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
                        contentDescription = "Resolver expresión"
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun solveDeterministic(input: String, evaluator: MathExpressionEvaluator): SolverRecord {
    val clean = input.trim().replace("²", "^2").replace(",", ".")

    val pctRegex = Regex("""^(-?\d+(?:\.\d+)?)\s*%\s*(?:de|of)\s*(-?\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
    pctRegex.matchEntire(clean)?.let { m ->
        val pct = m.groupValues[1].toDouble()
        val total = m.groupValues[2].toDouble()
        val result = total * (pct / 100.0)
        return SolverRecord(
            input = input,
            headline = "= ${fmtNum(result)}",
            steps = listOf(
                "Convertir porcentaje a decimal: $pct / 100 = ${fmtNum(pct / 100.0)}",
                "Multiplicar por la base: ${fmtNum(pct / 100.0)} × ${fmtNum(total)} = ${fmtNum(result)}"
            )
        )
    }

    if (clean.contains("=") && clean.contains("x", ignoreCase = true)) {
        return solvePolynomialEquation(input, clean, evaluator)
    }

    return runCatching {
        val result = evaluator.evaluate(clean)
        val formatted = result.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        SolverRecord(
            input = input,
            headline = "= $formatted",
            steps = listOf(
                "Expresión evaluada en grados (DEG)",
                "Resultado determinista exacto: $formatted"
            )
        )
    }.getOrElse { err ->
        SolverRecord(
            input = input,
            headline = "No se pudo evaluar",
            steps = listOf(err.message ?: "Revisa la sintaxis. Ejemplos: 'x^2 - 5x + 6 = 0', '15% de 480', 'sqrt(144) + sin(30)*10'."),
            isError = true
        )
    }
}

private fun solvePolynomialEquation(
    original: String,
    clean: String,
    evaluator: MathExpressionEvaluator
): SolverRecord {
    val parts = clean.split("=")
    if (parts.size != 2) {
        return SolverRecord(original, "Ecuación inválida", listOf("Usa un solo signo '='."), isError = true)
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

        require(abs(check2 - f2) < 1e-6) {
            "Solo se soportan ecuaciones lineales o cuadráticas en x"
        }

        if (abs(a) < 1e-9) {
            if (abs(b) < 1e-9) {
                val msg = if (abs(c) < 1e-9) "Identidad (infinitas soluciones)" else "Sin solución"
                SolverRecord(original, msg, listOf("Forma reducida: ${fmtNum(c)} = 0"))
            } else {
                val x = -c / b
                SolverRecord(
                    input = original,
                    headline = "x = ${fmtNum(x)}",
                    steps = listOf(
                        "Forma estándar: ${fmtNum(b)}x + (${fmtNum(c)}) = 0",
                        "Despejar x: x = -(${fmtNum(c)}) / ${fmtNum(b)} = ${fmtNum(x)}"
                    )
                )
            }
        } else {
            val disc = b * b - 4.0 * a * c
            val steps = mutableListOf(
                "Forma estándar: (${fmtNum(a)})x² + (${fmtNum(b)})x + (${fmtNum(c)}) = 0",
                "Discriminante Δ = b² - 4ac = ${fmtNum(disc)}"
            )
            when {
                disc > 1e-9 -> {
                    val x1 = (-b + sqrt(disc)) / (2.0 * a)
                    val x2 = (-b - sqrt(disc)) / (2.0 * a)
                    steps.add("x₁ = (-b + √Δ) / 2a = ${fmtNum(x1)}")
                    steps.add("x₂ = (-b - √Δ) / 2a = ${fmtNum(x2)}")
                    SolverRecord(original, "x₁ = ${fmtNum(x1)},  x₂ = ${fmtNum(x2)}", steps)
                }
                abs(disc) <= 1e-9 -> {
                    val x = -b / (2.0 * a)
                    steps.add("Raíz doble: x = -b / 2a = ${fmtNum(x)}")
                    SolverRecord(original, "x = ${fmtNum(x)} (doble)", steps)
                }
                else -> {
                    val real = -b / (2.0 * a)
                    val imag = sqrt(-disc) / abs(2.0 * a)
                    steps.add("Δ < 0 → Raíces complejas conjugadas")
                    steps.add("x = ${fmtNum(real)} ± ${fmtNum(imag)}i")
                    SolverRecord(original, "x = ${fmtNum(real)} ± ${fmtNum(imag)}i", steps)
                }
            }
        }
    }.getOrElse { err ->
        SolverRecord(original, "No se pudo resolver", listOf(err.message ?: "Ecuación no soportada"), isError = true)
    }
}

private fun fmtNum(v: Double): String {
    if (abs(v) < 1e-12) return "0"
    val rounded = String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
    return if (rounded == "-0") "0" else rounded
}
