package com.calcsuits.features.graph.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.usecase.MathExpressionEvaluator
import com.calcsuits.domain.usecase.toMathUiText
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.formatDecimal
import com.calcsuits.ui.util.rememberCurrentLocale
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

@Composable
fun GraphScreen(onMenuClick: () -> Unit) {
    val extColors = CalcSuitsTheme.colors
    val density = LocalDensity.current
    val locale = rememberCurrentLocale()
    var expression by remember { mutableStateOf("sin(x)") }
    var useRadians by remember { mutableStateOf(true) }
    var centerX by remember { mutableDoubleStateOf(0.0) }
    var centerY by remember { mutableDoubleStateOf(0.0) }
    var spanX by remember { mutableDoubleStateOf(14.0) }
    var spanY by remember { mutableDoubleStateOf(6.0) }
    var tracedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val evaluator = remember(useRadians) {
        MathExpressionEvaluator(MathContext(12), useRadians = useRadians)
    }

    val quickInsertTokens = listOf("x", "^", "(", ")", "sin(", "cos(", "√(", "π")

    val presets = listOf(
        "sin(x)",
        "cos(x)",
        "x^2 - 4",
        "x^3 - 3*x",
        "1/x",
        "ln(x)",
        "exp(-x^2/4)",
        "abs(x) - 2"
    )

    val minX = centerX - spanX / 2.0
    val maxX = centerX + spanX / 2.0
    val minY = centerY - spanY / 2.0
    val maxY = centerY + spanY / 2.0

    val syntaxError: UiText? = remember(expression, useRadians) {
        if (expression.isBlank()) {
            UiText.StringResource(R.string.graph_error_empty)
        } else {
            runCatching { evaluator.evaluate(expression, 1.234) }
                .exceptionOrNull()
                ?.toMathUiText()
        }
    }

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.graphs),
                subtitle = stringResource(R.string.graph_subtitle),
                onMenuClick = onMenuClick,
                actions = {
                    Surface(
                        onClick = { useRadians = !useRadians },
                        shape = RoundedCornerShape(8.dp),
                        color = extColors.technicalContainer,
                        border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.padding(end = 12.dp).testTag("graph_angle_toggle")
                    ) {
                        Text(
                            text = if (useRadians) "RAD" else "DEG",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = extColors.onTechnicalContainer
                        )
                    }
                }
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
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = expression,
                onValueChange = {
                    expression = it
                    tracedPoint = null
                },
                label = { Text("f(x) =") },
                placeholder = { Text(stringResource(R.string.graph_input_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("graph_expression_input"),
                singleLine = true,
                isError = syntaxError != null,
                supportingText = syntaxError?.let { msg -> { Text(msg.asString()) } },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            // Quick math symbol insertion strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickInsertTokens) { token ->
                    Surface(
                        onClick = {
                            expression += token
                            tracedPoint = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = extColors.numericKey,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("graph_insert_$token")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (token == "x" || token == "π") {
                                    extColors.technicalAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    CalcFilterChip(
                        selected = expression == preset,
                        onClick = {
                            expression = preset
                            tracedPoint = null
                        },
                        label = {
                            Text(
                                text = preset,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                val curveColor = MaterialTheme.colorScheme.primary
                val traceColor = extColors.technicalAccent
                val axisLabelColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                val labelTextSizePx = with(density) { 10.sp.toPx() }

                val axisPaint = remember(axisLabelColorArgb, labelTextSizePx) {
                    Paint().apply {
                        isAntiAlias = true
                        color = axisLabelColorArgb
                        textSize = labelTextSizePx
                        typeface = Typeface.MONOSPACE
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(spanX, spanY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (size.width > 0 && size.height > 0) {
                                        centerX -= (dragAmount.x / size.width.toDouble()) * spanX
                                        centerY += (dragAmount.y / size.height.toDouble()) * spanY
                                        tracedPoint = null
                                    }
                                }
                            }
                            .pointerInput(expression, minX, maxX, useRadians) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) {
                                        val tappedX = minX + (offset.x / size.width.toDouble()) * (maxX - minX)
                                        val tappedY = runCatching { evaluator.evaluate(expression, tappedX).toDouble() }.getOrNull()
                                        if (tappedY != null && tappedY.isFinite()) {
                                            tracedPoint = tappedX to tappedY
                                        }
                                    }
                                }
                            }
                    ) {
                        val width = size.width
                        val height = size.height
                        if (width <= 0f || height <= 0f || maxX <= minX || maxY <= minY) return@Canvas

                        fun px(x: Double) = ((x - minX) / (maxX - minX) * width).toFloat()
                        fun py(y: Double) = (height - (y - minY) / (maxY - minY) * height).toFloat()

                        val stepX = niceTickStep(spanX, targetTicks = 6)
                        val stepY = niceTickStep(spanY, targetTicks = 6)

                        val startX = ceil(minX / stepX) * stepX
                        val startY = ceil(minY / stepY) * stepY

                        // Aligned grid lines
                        var gxVal = startX
                        while (gxVal <= maxX) {
                            val xPos = px(gxVal)
                            drawLine(gridColor, Offset(xPos, 0f), Offset(xPos, height), strokeWidth = 1f)
                            gxVal += stepX
                        }
                        var gyVal = startY
                        while (gyVal <= maxY) {
                            val yPos = py(gyVal)
                            drawLine(gridColor, Offset(0f, yPos), Offset(width, yPos), strokeWidth = 1f)
                            gyVal += stepY
                        }

                        // Primary axes
                        val zeroY = if (0.0 in minY..maxY) py(0.0) else null
                        val zeroX = if (0.0 in minX..maxX) px(0.0) else null

                        zeroY?.let { zy ->
                            drawLine(axisColor, Offset(0f, zy), Offset(width, zy), strokeWidth = 2f)
                        }
                        zeroX?.let { zx ->
                            drawLine(axisColor, Offset(zx, 0f), Offset(zx, height), strokeWidth = 2f)
                        }

                        // Clean numeric axis labels scaled to current zoom
                        val tickHalf = 4.dp.toPx()
                        val labelMargin = 6.dp.toPx()
                        val xAxisLabelY = (zeroY ?: (height - labelMargin * 2.5f))
                            .coerceIn(labelTextSizePx + labelMargin, height - labelMargin)
                        val drawXAbove = zeroY != null && zeroY > height - (labelTextSizePx + labelMargin * 2.5f)
                        val xBaselineY = if (drawXAbove) {
                            xAxisLabelY - labelMargin
                        } else {
                            (xAxisLabelY + labelTextSizePx + 3.dp.toPx()).coerceAtMost(height - 4.dp.toPx())
                        }

                        axisPaint.textAlign = Paint.Align.CENTER
                        gxVal = startX
                        while (gxVal <= maxX) {
                            if (abs(gxVal) > stepX * 1e-4) {
                                val xPos = px(gxVal)
                                if (xPos in 24f..(width - 24f)) {
                                    zeroY?.let { zy ->
                                        drawLine(axisColor, Offset(xPos, zy - tickHalf), Offset(xPos, zy + tickHalf), strokeWidth = 1.5f)
                                    }
                                    drawContext.canvas.nativeCanvas.drawText(
                                        fmtAxisTick(gxVal, stepX, locale),
                                        xPos,
                                        xBaselineY,
                                        axisPaint
                                    )
                                }
                            }
                            gxVal += stepX
                        }

                        val yAxisX = (zeroX ?: labelMargin).coerceIn(labelMargin, width - 38.dp.toPx())
                        val drawYLeft = zeroX != null && zeroX > width - 48.dp.toPx()
                        axisPaint.textAlign = if (drawYLeft) Paint.Align.RIGHT else Paint.Align.LEFT
                        val yLabelX = if (drawYLeft) yAxisX - labelMargin else yAxisX + labelMargin

                        gyVal = startY
                        while (gyVal <= maxY) {
                            if (abs(gyVal) > stepY * 1e-4) {
                                val yPos = py(gyVal)
                                if (yPos in 36f..(height - 24f)) {
                                    zeroX?.let { zx ->
                                        drawLine(axisColor, Offset(zx - tickHalf, yPos), Offset(zx + tickHalf, yPos), strokeWidth = 1.5f)
                                    }
                                    drawContext.canvas.nativeCanvas.drawText(
                                        fmtAxisTick(gyVal, stepY, locale),
                                        yLabelX,
                                        yPos + labelTextSizePx * 0.35f,
                                        axisPaint
                                    )
                                }
                            }
                            gyVal += stepY
                        }

                        // Function curve
                        val path = Path()
                        var hasPoints = false
                        var started = false
                        var prevY: Double? = null
                        val samples = max(360, (width / 2).toInt().coerceAtMost(900))
                        val yJumpThreshold = (maxY - minY) * 1.2

                        for (i in 0..samples) {
                            val x = minX + (maxX - minX) * i / samples
                            val y = runCatching { evaluator.evaluate(expression, x).toDouble() }.getOrNull()
                            val valid = y != null && y.isFinite() &&
                                y in (minY - spanY * 2.0)..(maxY + spanY * 2.0)

                            if (valid) {
                                val yVal = y!!
                                val discontinuous = prevY != null && abs(yVal - prevY) > yJumpThreshold
                                val point = Offset(px(x), py(yVal))
                                if (!started || discontinuous) {
                                    path.moveTo(point.x, point.y)
                                    started = true
                                } else {
                                    path.lineTo(point.x, point.y)
                                }
                                prevY = yVal
                                hasPoints = true
                            } else {
                                started = false
                                prevY = null
                            }
                        }

                        if (hasPoints) {
                            drawPath(
                                path = path,
                                color = curveColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        tracedPoint?.let { (tx, ty) ->
                            if (tx in minX..maxX && ty in minY..maxY) {
                                val cx = px(tx)
                                val cy = py(ty)
                                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                drawLine(traceColor.copy(alpha = 0.65f), Offset(cx, 0f), Offset(cx, height), strokeWidth = 1.5f, pathEffect = dash)
                                drawLine(traceColor.copy(alpha = 0.65f), Offset(0f, cy), Offset(width, cy), strokeWidth = 1.5f, pathEffect = dash)
                                drawCircle(traceColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.graph_range_format,
                                fmtRange(minX, locale),
                                fmtRange(maxX, locale),
                                fmtRange(minY, locale),
                                fmtRange(maxY, locale)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    tracedPoint?.let { (tx, ty) ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = extColors.technicalContainer,
                            border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.graph_trace_format,
                                    fmtCoord(tx, locale),
                                    fmtCoord(ty, locale)
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = extColors.onTechnicalContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val zoomColors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                spanX = (spanX * 0.7).coerceAtLeast(0.4)
                                spanY = (spanY * 0.7).coerceAtLeast(0.4)
                            },
                            colors = zoomColors,
                            modifier = Modifier.size(40.dp).testTag("graph_zoom_in")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_graph_zoom_in))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                spanX = (spanX * 1.4).coerceAtMost(500.0)
                                spanY = (spanY * 1.4).coerceAtMost(500.0)
                            },
                            colors = zoomColors,
                            modifier = Modifier.size(40.dp).testTag("graph_zoom_out")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_graph_zoom_out))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                centerX = 0.0
                                centerY = 0.0
                                spanX = 14.0
                                spanY = 6.0
                                tracedPoint = null
                            },
                            colors = zoomColors,
                            modifier = Modifier.size(40.dp).testTag("graph_reset")
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = stringResource(R.string.cd_graph_reset))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.graph_interaction_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun niceTickStep(span: Double, targetTicks: Int): Double {
    val rawStep = span / targetTicks
    val exponent = floor(log10(rawStep.coerceAtLeast(1e-9)))
    val magnitude = 10.0.pow(exponent)
    val residual = rawStep / magnitude
    val niceResidual = when {
        residual <= 1.5 -> 1.0
        residual <= 3.5 -> 2.0
        residual <= 7.5 -> 5.0
        else -> 10.0
    }
    return niceResidual * magnitude
}

private fun fmtAxisTick(value: Double, step: Double, locale: Locale): String {
    val cleaned = if (abs(value) < 1e-9) 0.0 else value
    return when {
        step >= 1.0 && abs(cleaned - cleaned.toLong()) < 1e-6 -> cleaned.toLong().toString()
        step >= 0.1 -> formatDecimal(cleaned, locale, minFraction = 0, maxFraction = 1)
        else -> formatDecimal(cleaned, locale, minFraction = 0, maxFraction = 2)
    }
}

private fun fmtRange(v: Double, locale: Locale): String =
    formatDecimal(v, locale, minFraction = 1, maxFraction = 1)

private fun fmtCoord(v: Double, locale: Locale): String =
    formatDecimal(v, locale, minFraction = 3, maxFraction = 3)
