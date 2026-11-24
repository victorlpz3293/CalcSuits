package com.calcsuits.features.engineering.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.features.engineering.ui.viewmodel.EngineeringState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isSubnetting = state.selectedFormula == EngineeringFormula.Subnetting

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = "Ingeniería",
                subtitle = state.selectedFormula.title.uppercase(),
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("engineering_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Limpiar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(formulas, key = { it.title }) { formula ->
                    CalcFilterChip(
                        selected = state.selectedFormula == formula,
                        onClick = { onFormulaSelected(formula) },
                        label = { Text(formula.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = formula.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("eng_formula_${formula.title}")
                    )
                }
            }

            CalcCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.selectedFormula.equation,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = extColors.technicalAccent
                    )
                    Text(
                        text = state.selectedFormula.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSubnetting) {
                val maskFieldKey = EngineeringFormula.Subnetting.variables[1]
                val currentMask = state.inputs[maskFieldKey]?.trim().orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "CIDR RÁPIDO",
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
                            value = state.inputs[variable] ?: "",
                            onValueChange = { onInputChange(variable, it) },
                            label = { Text(variable) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("eng_input_$variable"),
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
                        text = out.error,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (out.headline.isNotBlank()) {
                CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "RESULTADO CALCULADO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = out.headline,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (out.details.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            out.details.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
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
                                    val summary = buildString {
                                        appendLine("Subnetting IPv4 — ${out.headline}")
                                        out.details.forEach { (label, value) ->
                                            appendLine("$label: $value")
                                        }
                                    }.trim()
                                    clipboard.setText(AnnotatedString(summary))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Resumen IPv4 copiado")
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
                                Text("Copiar resumen", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
