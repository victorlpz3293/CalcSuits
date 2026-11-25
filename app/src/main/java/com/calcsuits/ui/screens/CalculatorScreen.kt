package com.calcsuits.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.model.CalculatorAction
import com.calcsuits.model.CalculatorHistoryEntry
import com.calcsuits.model.CalculatorOperation
import com.calcsuits.model.CalculatorState
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.CalculatorButton
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onMenuClick: () -> Unit = {},
    onAction: (CalculatorAction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }

    val expression = buildString {
        append(state.number1)
        state.operation?.let { op ->
            append(" ")
            append(op.symbol)
            if (state.number2.isNotEmpty()) {
                append(" ")
                append(state.number2)
            }
        }
    }.ifEmpty { "0" }

    val triggerHaptic: () -> Unit = {
        if (state.hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.calculator),
                subtitle = stringResource(
                    if (state.isScientificExpanded) {
                        R.string.calc_subtitle_scientific
                    } else {
                        R.string.calc_subtitle_standard
                    }
                ),
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(
                        onClick = { showHistory = true },
                        modifier = Modifier.testTag("calc_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.cd_calc_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = {
                            triggerHaptic()
                            onAction(CalculatorAction.ToggleScientific)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.isScientificExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.isScientificExpanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 36.dp)
                            .testTag("calc_scientific_toggle")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "f(x)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (state.isScientificExpanded) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorDisplayPanel(
                expression = expression,
                previousExpression = state.previousExpression,
                livePreview = state.livePreview,
                errorMessage = state.errorMessage,
                memory = state.memory,
                useRadians = state.useRadians,
                isScientificExpanded = state.isScientificExpanded,
                onToggleAngle = {
                    triggerHaptic()
                    onAction(CalculatorAction.ToggleAngleUnit)
                },
                onCopy = {
                    val toCopy = state.livePreview.ifBlank { state.number1.ifBlank { "0" } }
                    clipboard.setText(AnnotatedString(toCopy))
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.copied_value_format, toCopy)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.isScientificExpanded) 0.30f else 0.36f)
            )

            AnimatedVisibility(
                visible = state.isScientificExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ScientificPanel(
                    onSymbol = { symbol ->
                        triggerHaptic()
                        when (symbol) {
                            "√" -> onAction(CalculatorAction.SquareRoot)
                            "x²" -> onAction(CalculatorAction.Square)
                            "x!" -> onAction(CalculatorAction.ScientificFunction("fact"))
                            "π", "e" -> onAction(CalculatorAction.Constant(symbol))
                            "^" -> onAction(CalculatorAction.Operation(CalculatorOperation.Power))
                            "mod" -> onAction(CalculatorAction.Operation(CalculatorOperation.Modulo))
                            else -> onAction(CalculatorAction.ScientificFunction(symbol))
                        }
                    }
                )
            }

            MemoryStrip(
                hasMemory = state.memory != "0",
                onAction = { action ->
                    triggerHaptic()
                    onAction(action)
                }
            )

            CalculatorKeypad(
                onAction = { action ->
                    triggerHaptic()
                    onAction(action)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.isScientificExpanded) 0.56f else 0.64f)
            )
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                HistorySheetContent(
                    history = state.history,
                    onSelectResult = { res ->
                        onAction(CalculatorAction.ReuseHistoryResult(res))
                        showHistory = false
                    },
                    onClear = {
                        onAction(CalculatorAction.ClearHistory)
                        showHistory = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CalculatorDisplayPanel(
    expression: String,
    previousExpression: String,
    livePreview: String,
    errorMessage: UiText?,
    memory: String,
    useRadians: Boolean,
    isScientificExpanded: Boolean,
    onToggleAngle: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extColors = CalcSuitsTheme.colors
    val exprScroll = rememberScrollState()
    LaunchedEffect(expression) {
        exprScroll.scrollTo(exprScroll.maxValue)
    }

    val displayFontSize = when {
        expression.length > 22 -> 24.sp
        expression.length > 16 -> 32.sp
        expression.length > 12 -> 40.sp
        expression.length > 8 -> 48.sp
        else -> 58.sp
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScientificExpanded) {
                        Surface(
                            onClick = onToggleAngle,
                            shape = RoundedCornerShape(8.dp),
                            color = extColors.technicalContainer,
                            border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("calc_angle_toggle")
                        ) {
                            Text(
                                text = if (useRadians) "RAD" else "DEG",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = extColors.onTechnicalContainer
                            )
                        }
                    }

                    if (memory != "0") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = extColors.technicalContainer,
                            border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = stringResource(R.string.calc_memory_badge, memory),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = extColors.onTechnicalContainer,
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(34.dp).testTag("calc_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_result),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                if (previousExpression.isNotBlank()) {
                    Text(
                        text = previousExpression,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = errorMessage.asString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            textAlign = TextAlign.End
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(exprScroll),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = expression,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = displayFontSize,
                            lineHeight = (displayFontSize.value + 4f).sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-1.0).sp,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (livePreview.isNotBlank()) "= $livePreview" else " ",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ScientificPanel(onSymbol: (String) -> Unit) {
    val extColors = CalcSuitsTheme.colors
    val rows = listOf(
        listOf("sin", "cos", "tan", "log", "ln", "√"),
        listOf("x²", "x!", "π", "e", "^", "mod")
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { fn ->
                    val isConstOrBinary = fn in setOf("π", "e", "^", "mod")
                    CalculatorButton(
                        symbol = fn,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = extColors.scientificKey,
                        contentColor = if (isConstOrBinary) {
                            extColors.technicalAccent
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        onClick = { onSymbol(fn) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryStrip(
    hasMemory: Boolean,
    onAction: (CalculatorAction) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val items = listOf(
            "MC" to CalculatorAction.MemoryClear,
            "MR" to CalculatorAction.MemoryRecall,
            "M+" to CalculatorAction.MemoryAdd,
            "M-" to CalculatorAction.MemorySubtract,
            "1/x" to CalculatorAction.ScientificFunction("inv")
        )
        items.forEach { (symbol, action) ->
            val highlight = (symbol == "MR" || symbol == "MC") && hasMemory
            CalculatorButton(
                symbol = symbol,
                modifier = Modifier.weight(1f),
                height = 38.dp,
                shape = RoundedCornerShape(10.dp),
                backgroundColor = extColors.scientificKey,
                contentColor = if (highlight) extColors.technicalAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                onClick = { onAction(action) }
            )
        }
    }
}

@Composable
private fun CalculatorKeypad(
    onAction: (CalculatorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val extColors = CalcSuitsTheme.colors
    val deleteCd = stringResource(R.string.cd_delete_last_digit)
    val rows = listOf(
        listOf("AC", "DEL", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { symbol ->
                    val isEqual = symbol == "="
                    val isOperator = symbol in setOf("÷", "×", "-", "+")
                    val isClear = symbol == "AC"
                    val isDelete = symbol == "DEL"
                    val isUtility = symbol in setOf("DEL", "%", "±")

                    val bgColor = when {
                        isEqual -> MaterialTheme.colorScheme.primary
                        isClear -> MaterialTheme.colorScheme.error
                        isOperator -> extColors.operatorKey
                        isUtility -> extColors.scientificKey
                        else -> extColors.numericKey
                    }
                    val fgColor = when {
                        isEqual -> MaterialTheme.colorScheme.onPrimary
                        isClear -> MaterialTheme.colorScheme.onError
                        isUtility -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val borderColor = when {
                        isEqual || isClear -> null
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }

                    CalculatorButton(
                        symbol = symbol,
                        icon = if (isDelete) Icons.AutoMirrored.Rounded.Backspace else null,
                        iconContentDescription = if (isDelete) deleteCd else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        backgroundColor = bgColor,
                        contentColor = fgColor,
                        borderColor = borderColor,
                        isEqualButton = isEqual,
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            when (symbol) {
                                "AC" -> onAction(CalculatorAction.Clear)
                                "DEL" -> onAction(CalculatorAction.Delete)
                                "%" -> onAction(CalculatorAction.Percentage)
                                "÷" -> onAction(CalculatorAction.Operation(CalculatorOperation.Divide))
                                "×" -> onAction(CalculatorAction.Operation(CalculatorOperation.Multiply))
                                "-" -> onAction(CalculatorAction.Operation(CalculatorOperation.Subtract))
                                "+" -> onAction(CalculatorAction.Operation(CalculatorOperation.Add))
                                "=" -> onAction(CalculatorAction.Calculate)
                                "±" -> onAction(CalculatorAction.ToggleSign)
                                "." -> onAction(CalculatorAction.Decimal)
                                else -> onAction(CalculatorAction.Number(symbol.toInt()))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySheetContent(
    history: List<CalculatorHistoryEntry>,
    onSelectResult: (String) -> Unit,
    onClear: () -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.calc_history_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = extColors.destructiveText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.calc_history_clear_action),
                        color = extColors.destructiveText
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.calc_history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    Surface(
                        onClick = { onSelectResult(item.result) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = item.expression,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "= ${item.result}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
