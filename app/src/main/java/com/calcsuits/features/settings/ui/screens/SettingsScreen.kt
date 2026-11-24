package com.calcsuits.features.settings.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calcsuits.data.local.datastore.AppSettings
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.AppThemeMode
import com.calcsuits.ui.theme.BraunOrange
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.CarbonSurface
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.theme.OledBackground
import com.calcsuits.ui.theme.PaperBackground
import kotlin.math.PI
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onMenuClick: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onUseRadiansChange: (Boolean) -> Unit,
    onDecimalPrecisionChange: (Int) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var historyClearedFeedback by remember { mutableStateOf(false) }
    val extColors = CalcSuitsTheme.colors

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = "Ajustes",
                subtitle = "CALCSUITS",
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Default.Palette, title = "Apariencia")
            CalcCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tema de la interfaz",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { mode ->
                                    ThemeOptionTile(
                                        mode = mode,
                                        selected = settings.themeMode == mode,
                                        onClick = { onThemeModeChange(mode) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionHeader(icon = Icons.Default.Tune, title = "Motor de cálculo")
            CalcCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unidad angular", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Usada en funciones trigonométricas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CalcFilterChip(
                                selected = !settings.useRadians,
                                onClick = { onUseRadiansChange(false) },
                                label = { Text("DEG") },
                                modifier = Modifier.testTag("settings_deg_chip")
                            )
                            CalcFilterChip(
                                selected = settings.useRadians,
                                onClick = { onUseRadiansChange(true) },
                                label = { Text("RAD") },
                                modifier = Modifier.testTag("settings_rad_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Precisión decimal", style = MaterialTheme.typography.titleSmall)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = extColors.technicalContainer,
                                border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${settings.decimalPrecision} dígitos",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extColors.onTechnicalContainer
                                )
                            }
                        }
                        Slider(
                            value = settings.decimalPrecision.toFloat(),
                            onValueChange = { onDecimalPrecisionChange(it.roundToInt()) },
                            valueRange = 2f..12f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("settings_precision_slider")
                        )
                        val preview = "%.${settings.decimalPrecision}f".format(java.util.Locale.US, PI)
                        Text(
                            text = "Ejemplo: π ≈ $preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }
            }

            SectionHeader(icon = Icons.Default.Vibration, title = "Interacción y datos")
            CalcCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Respuesta háptica", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Vibración sutil al pulsar teclas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.hapticEnabled,
                            onCheckedChange = onHapticChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.testTag("settings_haptic_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_history_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = extColors.destructiveText
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Limpiar historial de cálculos")
                    }

                    if (historyClearedFeedback) {
                        Text(
                            text = "Historial eliminado correctamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.positive
                        )
                    }
                }
            }

            SectionHeader(icon = Icons.Default.Info, title = "Acerca de")
            CalcCard(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CalcSuits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Suite matemática y técnica de precisión",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Desarrollada por Victor R. Lopez",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Versión 1.0 · Motor determinista BigDecimal + Jetpack Compose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán todas las operaciones guardadas en la base de datos local.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                        historyClearedFeedback = true
                    }
                ) {
                    Text("Eliminar", color = extColors.destructiveText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ThemeOptionTile(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchBorderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onClick,
        modifier = modifier.testTag("theme_option_${mode.storageKey}"),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, swatchBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    AppThemeMode.SYSTEM -> {
                        // Distinct split dark/paper preview swatch for System mode
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = CarbonSurface,
                                topLeft = Offset.Zero,
                                size = Size(size.width / 2f, size.height)
                            )
                            drawRect(
                                color = PaperBackground,
                                topLeft = Offset(size.width / 2f, 0f),
                                size = Size(size.width / 2f, size.height)
                            )
                        }
                    }
                    AppThemeMode.DARK -> {
                        Box(modifier = Modifier.fillMaxSize().background(CarbonSurface))
                    }
                    AppThemeMode.OLED -> {
                        Box(modifier = Modifier.fillMaxSize().background(OledBackground))
                    }
                    AppThemeMode.LIGHT -> {
                        Box(modifier = Modifier.fillMaxSize().background(PaperBackground))
                    }
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(BraunOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
