package com.calcsuits.features.converter.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.converter.ConversionCategory
import com.calcsuits.domain.model.converter.ConversionUnit
import com.calcsuits.features.converter.ui.viewmodel.ConverterState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.rememberCurrentLocale
import kotlinx.coroutines.launch

private const val EXCHANGE_RATE_API_NAME = "ExchangeRate-API"
private const val EXCHANGE_RATE_API_URL = "https://www.exchangerate-api.com"

/**
 * Resolves the responsive grid column count for the 9-category converter hub:
 * 2 columns on mobile phones (`< 600dp`) and 3 columns on tablets / wide screens (`>= 600dp`).
 */
internal fun resolveConverterGridColumns(availableWidthDp: Float): Int =
    if (availableWidthDp >= 600f) 3 else 2

@Composable
fun ConverterScreen(
    state: ConverterState,
    onMenuClick: () -> Unit,
    onCategorySelected: (ConversionCategory) -> Unit,
    onBackToCategories: () -> Unit = {},
    onValueChanged: (String) -> Unit,
    onUnitChange: (Boolean, ConversionUnit) -> Unit = { _, _ -> },
    onSwap: () -> Unit = {},
    onRefreshCurrency: () -> Unit = {}
) {
    BackHandler(enabled = state.activeCategory != null) {
        onBackToCategories()
    }

    val activeCategory = state.activeCategory
    if (activeCategory == null) {
        ConverterCategoriesHub(
            onMenuClick = onMenuClick,
            onCategorySelected = onCategorySelected
        )
    } else {
        ConverterWorkspaceView(
            state = state,
            activeCategory = activeCategory,
            onBackToCategories = onBackToCategories,
            onValueChanged = onValueChanged,
            onUnitChange = onUnitChange,
            onSwap = onSwap,
            onRefreshCurrency = onRefreshCurrency
        )
    }
}

@Composable
private fun ConverterCategoriesHub(
    onMenuClick: () -> Unit,
    onCategorySelected: (ConversionCategory) -> Unit
) {
    val categories = ConversionCategory.all
    val extColors = CalcSuitsTheme.colors

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.converter),
                subtitle = stringResource(R.string.converter_hub_subtitle),
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            val columns = resolveConverterGridColumns(maxWidth.value)
            val rows = remember(categories, columns) { categories.chunked(columns) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.converter_hub_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                )

                rows.forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCategories.forEach { category ->
                            val previewSymbols = remember(category) {
                                category.units.take(3).joinToString(" · ") { it.symbol }
                            }
                            Card(
                                onClick = { onCategorySelected(category) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 132.dp)
                                    .testTag("converter_cat_${category.key}"),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = category.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = stringResource(category.titleRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = previewSymbols,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = extColors.technicalAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.converter_units_count,
                                                category.units.size,
                                                category.units.size
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        repeat(columns - rowCategories.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterWorkspaceView(
    state: ConverterState,
    activeCategory: ConversionCategory,
    onBackToCategories: () -> Unit,
    onValueChanged: (String) -> Unit,
    onUnitChange: (Boolean, ConversionUnit) -> Unit,
    onSwap: () -> Unit,
    onRefreshCurrency: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locale = rememberCurrentLocale()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(activeCategory) {
        scrollState.scrollTo(0)
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(activeCategory.titleRes),
                subtitle = stringResource(R.string.converter).uppercase(locale),
                onMenuClick = onBackToCategories,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_converter)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        pinnedPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
        pinnedSpacing = 12.dp,
        contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp),
        contentSpacing = 12.dp,
        scrollState = scrollState,
        compactHeightThreshold = 420.dp,
        pinnedContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalcCard(shape = MaterialTheme.shapes.extraLarge) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.converter_from),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                UnitDropdownPill(
                                    selected = state.fromUnit,
                                    units = state.availableUnits,
                                    onSelected = { onUnitChange(true, it) },
                                    tag = "converter_from_unit",
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            OutlinedTextField(
                                value = state.fromValue,
                                onValueChange = onValueChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("converter_value_input"),
                                placeholder = { Text("0") },
                                suffix = {
                                    Text(
                                        text = state.fromUnit.symbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    CalcResultCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.converter_result),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                UnitDropdownPill(
                                    selected = state.toUnit,
                                    units = state.availableUnits,
                                    onSelected = { onUnitChange(false, it) },
                                    tag = "converter_to_unit",
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.toValue.isBlank()) "—" else "${state.toValue} ${state.toUnit.symbol}",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val toUnitSubtitle = if (state.toUnit.symbol in state.referenceCurrencySymbols) {
                                        "${stringResource(state.toUnit.nameRes)} · ${stringResource(R.string.converter_rate_reference_tag)}"
                                    } else {
                                        stringResource(state.toUnit.nameRes)
                                    }
                                    Text(
                                        text = toUnitSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.toValue.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(state.toValue))
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        R.string.copied_value_format,
                                                        "${state.toValue} ${state.toUnit.symbol}"
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("converter_copy_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.cd_copy_result),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                FilledIconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .testTag("converter_swap_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.cd_converter_swap)
                    )
                }
            }
        }
    ) {
            if (state.selectedCategory == ConversionCategory.Currency) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (state.isCurrencyOffline) Icons.Default.CloudOff else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.currencyStatusText?.asString()
                            ?: stringResource(R.string.cat_currency_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("converter_currency_status")
                    )
                    IconButton(
                        onClick = onRefreshCurrency,
                        enabled = !state.isRefreshingCurrency,
                        modifier = Modifier.testTag("converter_currency_refresh")
                    ) {
                        if (state.isRefreshingCurrency) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("converter_currency_loading"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_converter_refresh_rates),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                state.selectedCategory.noteRes?.let { noteRes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(noteRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.allUnitPreviews.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.converter_equivalences),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    CalcCard(contentPadding = PaddingValues(0.dp)) {
                        state.allUnitPreviews.forEachIndexed { idx, preview ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(preview.unit.nameRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val previewSymbolLabel = if (preview.unit.symbol in state.referenceCurrencySymbols) {
                                        "${preview.unit.symbol} · ${stringResource(R.string.converter_rate_reference_tag)}"
                                    } else {
                                        preview.unit.symbol
                                    }
                                    Text(
                                        text = previewSymbolLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "${preview.formattedValue} ${preview.unit.symbol}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (preview.unit.symbol == state.toUnit.symbol) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textAlign = TextAlign.End
                                )
                            }
                            if (idx < state.allUnitPreviews.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            if (state.selectedCategory == ConversionCategory.Currency) {
                val attributionFull = stringResource(
                    R.string.converter_rates_attribution,
                    EXCHANGE_RATE_API_NAME
                )
                val linkColor = MaterialTheme.colorScheme.primary
                val attributionText = remember(attributionFull, linkColor) {
                    buildAnnotatedString {
                        append(attributionFull)
                        val start = attributionFull.indexOf(EXCHANGE_RATE_API_NAME)
                        if (start >= 0) {
                            addStyle(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                ),
                                start = start,
                                end = start + EXCHANGE_RATE_API_NAME.length
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = attributionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = EXCHANGE_RATE_API_NAME
                            ) {
                                runCatching { uriHandler.openUri(EXCHANGE_RATE_API_URL) }
                            }
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp)
                            .testTag("converter_currency_attribution")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun UnitDropdownPill(
    selected: ConversionUnit,
    units: List<ConversionUnit>,
    onSelected: (ConversionUnit) -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag(tag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(selected.nameRes)} (${selected.symbol})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.cd_converter_select_unit),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${stringResource(unit.nameRes)} (${unit.symbol})") },
                    onClick = {
                        expanded = false
                        onSelected(unit)
                    }
                )
            }
        }
    }
}
