package com.calcsuits.features.programmer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.R
import com.calcsuits.features.programmer.ui.viewmodel.ProgrammerState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString

private val MIN_PROPORTIONAL_KEYPAD_HEIGHT = 270.dp

@Composable
fun ProgrammerScreen(
    state: ProgrammerState,
    onMenuClick: () -> Unit,
    onBaseSelected: (Int) -> Unit,
    onWordSizeSelected: (Int) -> Unit,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onBitwise: (String) -> Unit,
    onToggleBit: (Int) -> Unit
) {
    var showBitGrid by remember { mutableStateOf(false) }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.programmer),
                subtitle = stringResource(R.string.prog_subtitle, state.wordSize, state.activeBase),
                onMenuClick = onMenuClick
            )
        },
        applyImePadding = false,
        enableContentScroll = false,
        pinnedPadding = PaddingValues(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 4.dp),
        pinnedSpacing = 4.dp,
        contentPadding = PaddingValues(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 8.dp),
        contentSpacing = 0.dp,
        compactHeightThreshold = 340.dp,
        pinnedContent = {
            ProgrammerPinnedControls(
                state = state,
                showBitGrid = showBitGrid,
                onShowBitGridChange = { showBitGrid = it },
                onWordSizeSelected = onWordSizeSelected,
                onBaseSelected = onBaseSelected
            )
        }
    ) {
        ProgrammerWorkspaceBody(
            state = state,
            showBitGrid = showBitGrid,
            onKeyPress = onKeyPress,
            onBackspace = onBackspace,
            onClear = onClear,
            onBitwise = onBitwise,
            onToggleBit = onToggleBit
        )
    }
}

@Composable
fun ProgrammerPinnedControls(
    state: ProgrammerState,
    showBitGrid: Boolean,
    onShowBitGridChange: (Boolean) -> Unit,
    onWordSizeSelected: (Int) -> Unit,
    onBaseSelected: (Int) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(8, 16, 32, 64).forEach { size ->
                CalcFilterChip(
                    selected = state.wordSize == size,
                    onClick = { onWordSizeSelected(size) },
                    label = {
                        Text(
                            text = stringResource(R.string.prog_word_size_chip, size),
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    },
                    modifier = Modifier.testTag("prog_word_$size")
                )
            }
        }
    }

    CalcCard(
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (state.pendingExpression.isNotBlank()) {
                Text(
                    text = state.pendingExpression,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = extColors.technicalAccent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            } else if (state.lastOperation != null) {
                Text(
                    text = state.lastOperation.asString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }

            BaseRow("HEX", state.hexValue, state.activeBase == 16) { onBaseSelected(16) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BaseRow("DEC", state.decValue, state.activeBase == 10) { onBaseSelected(10) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BaseRow("OCT", state.octValue, state.activeBase == 8) { onBaseSelected(8) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BaseRow("BIN", state.binValue, state.activeBase == 2) { onBaseSelected(2) }
        }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showBitGrid,
            onClick = { onShowBitGridChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            colors = calcSegmentedButtonColors(),
            modifier = Modifier.testTag("prog_view_keypad")
        ) {
            Text(
                text = stringResource(R.string.prog_tab_keypad),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SegmentedButton(
            selected = showBitGrid,
            onClick = { onShowBitGridChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            colors = calcSegmentedButtonColors(),
            modifier = Modifier.testTag("prog_view_bits")
        ) {
            Text(
                text = stringResource(R.string.prog_tab_bits),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProgrammerWorkspaceBody(
    state: ProgrammerState,
    showBitGrid: Boolean,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onBitwise: (String) -> Unit,
    onToggleBit: (Int) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (showBitGrid) {
            val bitContainerModifier = if (constraints.hasBoundedHeight) {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
            Column(modifier = bitContainerModifier) {
                BitToggleMatrix(
                    rawValue = state.rawValue,
                    wordSize = state.wordSize.coerceAtMost(32),
                    onToggleBit = onToggleBit
                )
                Spacer(Modifier.height(4.dp))
            }
        } else {
            val bitwiseRows = listOf(
                listOf("AND", "OR", "XOR", "NOT"),
                listOf("NAND", "NOR", "SHL", "SHR")
            )
            val fitProportionally = constraints.hasBoundedHeight &&
                maxHeight >= MIN_PROPORTIONAL_KEYPAD_HEIGHT
            val spacing = if (fitProportionally) 5.dp else 6.dp
            val keypadContainerModifier = when {
                fitProportionally -> Modifier.fillMaxSize()
                constraints.hasBoundedHeight -> Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                else -> Modifier.fillMaxWidth()
            }

            Column(
                modifier = keypadContainerModifier,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                bitwiseRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (fitProportionally) it.weight(0.78f) else it.height(38.dp) },
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        row.forEach { op ->
                            val isActiveOp = state.pendingOperator == op
                            Surface(
                                onClick = { onBitwise(op) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("prog_op_$op"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isActiveOp) extColors.technicalContainer else extColors.scientificKey,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActiveOp) extColors.technicalAccent else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = op,
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = if (isActiveOp) extColors.onTechnicalContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                ProgrammerKeypad(
                    activeBase = state.activeBase,
                    fillHeight = fitProportionally,
                    spacing = spacing,
                    onKeyPress = onKeyPress,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    onEquals = { onBitwise("=") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (fitProportionally) it.weight(5f) else it }
                )
            }
        }
    }
}

@Composable
private fun BaseRow(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prog_base_$label")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (label == "BIN" && value.length > 20) 12.sp else 15.sp,
                    lineHeight = if (label == "BIN" && value.length > 20) 17.sp else 20.sp
                ),
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = if (label == "BIN") 4 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgrammerKeypad(
    activeBase: Int,
    fillHeight: Boolean,
    spacing: Dp,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEquals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowedDigits = when (activeBase) {
        2 -> setOf("0", "1", "00")
        8 -> setOf("0", "1", "2", "3", "4", "5", "6", "7", "00")
        10 -> setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "00")
        else -> setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "00")
    }

    val minKeyHeight = 44.dp
    val rowKeyHeight: Dp? = if (fillHeight) null else minKeyHeight

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // Dedicated A-F hex bar cleanly visible even when disabled in DEC/OCT/BIN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillHeight) it.weight(1f) else it },
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            listOf("A", "B", "C", "D", "E", "F").forEach { hexKey ->
                ProgrammerKey(
                    key = hexKey,
                    enabled = hexKey in allowedDigits,
                    height = rowKeyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { onKeyPress(hexKey) }
                )
            }
        }

        // Row 1: 7 8 9 AC
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillHeight) it.weight(1f) else it },
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            listOf("7", "8", "9", "AC").forEach { key ->
                ProgrammerKey(
                    key = key,
                    enabled = key == "AC" || key in allowedDigits,
                    height = rowKeyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { if (key == "AC") onClear() else onKeyPress(key) }
                )
            }
        }

        // Row 2: 4 5 6 DEL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillHeight) it.weight(1f) else it },
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            listOf("4", "5", "6", "DEL").forEach { key ->
                ProgrammerKey(
                    key = key,
                    enabled = key == "DEL" || key in allowedDigits,
                    height = rowKeyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { if (key == "DEL") onBackspace() else onKeyPress(key) }
                )
            }
        }

        // Rows 3 & 4: standard muscle-memory layout with 0 / 00 on bottom row and = on bottom-right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillHeight) it.weight(2f) else it },
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(3f)
                    .let { if (fillHeight) it.fillMaxHeight() else it },
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (fillHeight) it.weight(1f) else it },
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    listOf("1", "2", "3").forEach { key ->
                        ProgrammerKey(
                            key = key,
                            enabled = key in allowedDigits,
                            height = rowKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onKeyPress(key) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (fillHeight) it.weight(1f) else it },
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    ProgrammerKey(
                        key = "0",
                        enabled = "0" in allowedDigits,
                        height = rowKeyHeight,
                        modifier = Modifier.weight(2f),
                        onClick = { onKeyPress("0") }
                    )
                    ProgrammerKey(
                        key = "00",
                        enabled = "00" in allowedDigits,
                        height = rowKeyHeight,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onKeyPress("0")
                            onKeyPress("0")
                        }
                    )
                }
            }

            ProgrammerKey(
                key = "=",
                enabled = true,
                height = if (fillHeight) null else minKeyHeight * 2 + spacing,
                modifier = Modifier.weight(1f),
                onClick = onEquals
            )
        }
    }
}

@Composable
private fun ProgrammerKey(
    key: String,
    enabled: Boolean,
    height: Dp?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    val isHex = key in setOf("A", "B", "C", "D", "E", "F")
    val isEqual = key == "="
    val isClear = key == "AC"
    val isDelete = key == "DEL"

    val bg = when {
        isEqual -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.error
        isDelete -> extColors.operatorKey
        !enabled -> extColors.scientificKey
        else -> extColors.numericKey
    }
    val fg = when {
        isEqual -> MaterialTheme.colorScheme.onPrimary
        isClear -> MaterialTheme.colorScheme.onError
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
        isHex -> extColors.technicalAccent
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .let { if (height != null) it.height(height) else it.fillMaxHeight() }
            .testTag("prog_key_$key"),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = if (isEqual || isClear) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.55f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDelete) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = stringResource(R.string.cd_delete_last_digit),
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = if (isEqual) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (isEqual) 24.sp else 16.sp
                    ),
                    color = fg
                )
            }
        }
    }
}

@Composable
private fun BitToggleMatrix(
    rawValue: Long,
    wordSize: Int,
    onToggleBit: (Int) -> Unit
) {
    CalcCard(contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.prog_bit_matrix_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val totalBits = wordSize.coerceIn(8, 32)
            for (rowStart in (totalBits - 1) downTo 0 step 8) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (offset in 0 until 8) {
                        val bitIdx = rowStart - offset
                        val isOne = ((rawValue ushr bitIdx) and 1L) == 1L
                        Surface(
                            onClick = { onToggleBit(bitIdx) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOne) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isOne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isOne) "1" else "0",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isOne) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$bitIdx",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = if (isOne) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
