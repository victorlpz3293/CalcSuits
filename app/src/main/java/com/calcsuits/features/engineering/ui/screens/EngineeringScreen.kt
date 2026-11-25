package com.calcsuits.features.engineering.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.features.engineering.ui.viewmodel.EngineeringState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.rememberCurrentLocale
import com.calcsuits.ui.util.resolve
import kotlinx.coroutines.launch

private val COMMON_CIDR_PRESETS = listOf(
    "/8" to "255.0.0.0",
    "/16" to "255.255.0.0",
    "/24" to "255.255.255.0",
    "/26" to "255.255.255.192",
    "/28" to "255.255.255.240",
    "/30" to "255.255.255.252"
)

@Composable
fun EngineeringScreen(
    state: EngineeringState,
    onMenuClick: () -> Unit,
    onFormulaSelected: (EngineeringFormula) -> Unit,
    onInputChange: (String, String) -> Unit,
    onClearInputs: () -> Unit = {}
) {
    val formulas = EngineeringFormula.all
    val extColors = CalcSuitsTheme.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val locale = rememberCurrentLocale()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isSubnetting = state.selectedFormula == EngineeringFormula.Subnetting

    LaunchedEffect(state.selectedFormula) {
        scrollState.scrollTo(0)
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.engineering),
                subtitle = stringResource(state.selectedFormula.titleRes).uppercase(locale),
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("engineering_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        pinnedPadding = PaddingValues(top = 8.dp, bottom = 6.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
        contentSpacing = 14.dp,
        scrollState = scrollState,
        compactHeightThreshold = 200.dp,
        pinnedContent = {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(formulas, key = { it.key }) { formula ->
                    CalcFilterChip(
                        selected = state.selectedFormula == formula,
                        onClick = { onFormulaSelected(formula) },
                        label = {
                            Text(
                                text = stringResource(formula.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = formula.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("eng_formula_${formula.key}")
                    )
                }
            }
        }
    ) {

            CalcCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.selectedFormula.equation.asString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = extColors.technicalAccent
                    )
                    Text(
                        text = stringResource(state.selectedFormula.hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSubnetting) {
                val maskFieldKey = EngineeringFormula.Subnetting.variables[1].key
                val currentMask = state.inputs[maskFieldKey]?.trim().orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.eng_quick_cidr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(COMMON_CIDR_PRESETS, key = { it.first }) { (cidr, dotted) ->
                            val numeric = cidr.removePrefix("/")
                            val selected = currentMask == cidr || currentMask == numeric || currentMask == dotted
                            CalcFilterChip(
                                selected = selected,
                                onClick = { onInputChange(maskFieldKey, cidr) },
                                label = {
                                    Text(
                                        text = cidr,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.testTag("eng_cidr_$numeric")
                            )
                        }
                    }
                }
            }

            CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.selectedFormula.variables.forEach { variable ->
                        OutlinedTextField(
                            value = state.inputs[variable.key] ?: "",
                            onValueChange = { onInputChange(variable.key, it) },
                            label = { Text(stringResource(variable.labelRes)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("eng_input_${variable.key}"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (state.selectedFormula.allowsCidrText) KeyboardType.Text else KeyboardType.Decimal
                            ),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            }

            val out = state.output
            if (out.error != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = out.error.asString(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (out.headline != null) {
                CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.eng_calculated_result),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = out.headline.asString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (out.details.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            out.details.forEach { detail ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = detail.label.asString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = detail.value.asString(),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (isSubnetting) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val headlineText = out.headline.resolve(context)
                                    val summary = buildString {
                                        appendLine(
                                            context.getString(
                                                R.string.eng_subnet_summary_header,
                                                headlineText
                                            )
                                        )
                                        out.details.forEach { detail ->
                                            appendLine("${detail.label.resolve(context)}: ${detail.value.resolve(context)}")
                                        }
                                    }.trim()
                                    clipboard.setText(AnnotatedString(summary))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.eng_subnet_copied)
                                        )
                                    }
                                },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("eng_copy_subnet_summary")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.eng_copy_summary),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
    }
}
