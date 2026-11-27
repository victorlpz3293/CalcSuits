package com.calcsuits.features.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.features.ai.ui.viewmodel.OcrUiState
import com.calcsuits.features.graph.domain.AdaptedGraphExpression
import com.calcsuits.features.graph.ui.screens.ContextualFunctionGraphCanvas
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.formatDecimal
import java.util.Locale

@Composable
internal fun OcrStatusPanel(
    ocrState: OcrUiState,
    ocrModelReady: Boolean,
    locale: Locale,
    onStartDownload: () -> Unit,
    onDraftEdited: (String) -> Unit,
    onConfirmExpression: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ocrState) {
        OcrUiState.Idle -> Unit

        OcrUiState.ModelRequired -> {
            CalcCard(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("ocr_model_required_card"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(R.string.ocr_download_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ocr_download_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_download_cancel_button"),
                        ) {
                            Text(stringResource(R.string.ocr_cancel_action))
                        }
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_download_confirm_button"),
                        ) {
                            Text(stringResource(R.string.ocr_download_action))
                        }
                    }
                }
            }
        }

        is OcrUiState.Downloading -> {
            val progress = ocrState.progress
            CalcCard(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("ocr_downloading_card"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ocr_downloading_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (progress != null && progress.totalBytes > 0L) {
                        val fraction = progress.fraction
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val downloadedMb = formatDecimal(
                            progress.downloadedBytes / (1024.0 * 1024.0),
                            locale,
                            minFraction = 1,
                            maxFraction = 1,
                        )
                        val totalMb = formatDecimal(
                            progress.totalBytes / (1024.0 * 1024.0),
                            locale,
                            minFraction = 1,
                            maxFraction = 1,
                        )
                        Text(
                            text = stringResource(
                                R.string.ocr_downloading_progress_format,
                                progress.currentFileName,
                                progress.fileIndex,
                                progress.totalFiles,
                                downloadedMb,
                                totalMb,
                                (fraction * 100f).toInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.ocr_downloading_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_download_cancel_active"),
                        ) {
                            Text(stringResource(R.string.ocr_cancel_action))
                        }
                    }
                }
            }
        }

        OcrUiState.Processing -> {
            CalcCard(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("ocr_processing_card"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ocr_processing_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.ocr_processing_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_processing_cancel_button"),
                        ) {
                            Text(stringResource(R.string.ocr_cancel_action))
                        }
                    }
                }
            }
        }

        is OcrUiState.PendingConfirmation -> {
            val astValid = ocrState.astValidation.ok
            CalcCard(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("ocr_confirmation_card"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = if (astValid) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ocr_confirm_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.ocr_confirm_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = ocrState.editableExpression,
                        onValueChange = onDraftEdited,
                        label = { Text(stringResource(R.string.ocr_confirm_field_label)) },
                        singleLine = true,
                        isError = !astValid && ocrState.editableExpression.isNotBlank(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_confirm_input"),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (astValid) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (astValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (astValid) {
                                stringResource(R.string.ocr_confirm_ast_valid)
                            } else {
                                stringResource(
                                    R.string.ocr_confirm_ast_warning,
                                    ocrState.astValidation.error ?: "",
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (astValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    Text(
                        text = stringResource(R.string.ocr_confirm_raw_latex, ocrState.rawLatex),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = stringResource(
                            R.string.ocr_confirm_telemetry_format,
                            ocrState.telemetry.totalMs,
                            ocrState.telemetry.generatedTokens,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_cancel_button"),
                        ) {
                            Text(stringResource(R.string.ocr_cancel_action))
                        }
                        Button(
                            onClick = onConfirmExpression,
                            enabled = ocrState.editableExpression.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_confirm_button"),
                        ) {
                            Text(stringResource(R.string.ocr_confirm_action))
                        }
                    }
                }
            }
        }

        is OcrUiState.Error -> {
            CalcCard(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("ocr_error_card"),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = ocrState.message.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (!ocrModelReady) {
                            TextButton(
                                onClick = onStartDownload,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ocr_retry_download_button"),
                            ) {
                                Text(stringResource(R.string.ocr_retry_download_action))
                            }
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ocr_error_dismiss_button"),
                        ) {
                            Text(stringResource(R.string.ocr_dismiss_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraPermissionDialog(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.ocr_camera_permission_title))
        },
        text = {
            Text(
                text = stringResource(
                    if (permanentlyDenied) {
                        R.string.ocr_camera_permission_denied_permanent
                    } else {
                        R.string.ocr_camera_permission_rationale
                    }
                )
            )
        },
        confirmButton = {
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("ocr_camera_perm_confirm"),
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) {
                            R.string.ocr_camera_permission_settings
                        } else {
                            R.string.ocr_camera_permission_allow
                        }
                    )
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onUseGallery,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ocr_camera_perm_gallery"),
                ) {
                    Text(stringResource(R.string.ocr_camera_permission_use_gallery))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.ocr_cancel_action))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextualGraphSheet(
    expression: AdaptedGraphExpression,
    useRadians: Boolean,
    locale: Locale,
    onToggleAngleUnit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extColors = CalcSuitsTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toggleAngleCd = stringResource(R.string.cd_solver_toggle_angle)

    BackHandler(onBack = onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("solver_contextual_graph_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.solver_graph_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = expression.displayLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onToggleAngleUnit,
                    shape = RoundedCornerShape(8.dp),
                    color = extColors.technicalContainer,
                    border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = toggleAngleCd }
                        .testTag("solver_graph_angle_toggle"),
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (useRadians) "RAD" else "DEG",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = extColors.onTechnicalContainer,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("solver_graph_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_solver_close_graph),
                    )
                }
            }

            if (expression.isEquation) {
                Text(
                    text = stringResource(R.string.solver_graph_equation_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ContextualFunctionGraphCanvas(
                expression = expression.evaluableExpression,
                useRadians = useRadians,
                locale = locale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            )

            Text(
                text = stringResource(R.string.graph_interaction_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
