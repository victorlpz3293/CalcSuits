package com.calcsuits.features.ai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.features.ai.ocr.OcrTempFileManager
import com.calcsuits.features.ai.ui.viewmodel.OcrUiState
import com.calcsuits.features.ai.ui.viewmodel.SolverViewModel
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.rememberCurrentLocale

private data class QuickSolverPreset(
    @StringRes val titleRes: Int,
    val tag: UiText,
    val expression: UiText,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    onMenuClick: () -> Unit,
    viewModel: SolverViewModel = hiltViewModel(),
) {
    val extColors = CalcSuitsTheme.colors
    val context = LocalContext.current
    val locale = rememberCurrentLocale()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val percentExample = stringResource(R.string.solver_example_percent)
    val isImeVisible = WindowInsets.isImeVisible

    var showCameraPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var cameraPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var hasRequestedCameraOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(locale) {
        viewModel.updateLocale(locale)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModelAvailability()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenDisposed()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onCameraCaptureResult(success)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.processPickedImageUri(uri)
    }

    fun launchCameraCapture() {
        runCatching {
            val capture = OcrTempFileManager.createTempCapture(context)
            viewModel.registerPendingCameraCapture(capture.file)
            takePictureLauncher.launch(capture.uri)
        }.onFailure {
            viewModel.onCameraCaptureResult(false)
        }
    }

    fun launchGalleryPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedCameraOnce = true
        if (granted) {
            showCameraPermissionDialog = false
            cameraPermanentlyDenied = false
            launchCameraCapture()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            cameraPermanentlyDenied = !canAskAgain
            showCameraPermissionDialog = true
        }
    }

    fun onCameraButtonClick() {
        if (!state.ocrModelReady) {
            viewModel.requestModelDownloadPrompt()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            val activity = context.findActivity()
            val showRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            if (showRationale) {
                cameraPermanentlyDenied = false
                showCameraPermissionDialog = true
            } else if (hasRequestedCameraOnce) {
                cameraPermanentlyDenied = true
                showCameraPermissionDialog = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun onGalleryButtonClick() {
        if (!state.ocrModelReady) {
            viewModel.requestModelDownloadPrompt()
            return
        }
        launchGalleryPicker()
    }

    val quickPresets = remember {
        listOf(
            QuickSolverPreset(
                titleRes = R.string.solver_preset_quadratic_title,
                tag = UiText.DynamicString("ax² + bx + c = 0"),
                expression = UiText.DynamicString("x^2 - 5x + 6 = 0"),
            ),
            QuickSolverPreset(
                titleRes = R.string.solver_preset_linear_title,
                tag = UiText.DynamicString("ax + b = c"),
                expression = UiText.DynamicString("3x + 7 = 22"),
            ),
            QuickSolverPreset(
                titleRes = R.string.solver_preset_percent_title,
                tag = UiText.StringResource(R.string.solver_preset_percent_tag),
                expression = UiText.StringResource(R.string.solver_example_percent),
            ),
        )
    }

    val examples = remember(percentExample) {
        listOf(
            "x^2 - 5x + 6 = 0",
            "3x + 7 = 22",
            percentExample,
            "(12 + 8) * sin(30) + sqrt(144)",
            "2^10 - 5!",
        )
    }

    LaunchedEffect(state.records.size) {
        if (state.records.isNotEmpty()) {
            listState.animateScrollToItem(state.records.lastIndex)
        }
    }

    val toggleAngleCd = stringResource(R.string.cd_solver_toggle_angle)
    val ocrBusy = state.ocrState is OcrUiState.Downloading || state.ocrState is OcrUiState.Processing

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.solver),
                subtitle = stringResource(R.string.solver_subtitle),
                onMenuClick = onMenuClick,
                actions = {
                    Surface(
                        onClick = { viewModel.toggleAngleUnit() },
                        shape = RoundedCornerShape(8.dp),
                        color = extColors.technicalContainer,
                        border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = toggleAngleCd }
                            .testTag("solver_angle_toggle"),
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (state.useRadians) "RAD" else "DEG",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = extColors.onTechnicalContainer,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = { onCameraButtonClick() },
                            enabled = !ocrBusy,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("solver_ocr_camera_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.cd_ocr_scan_camera),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onGalleryButtonClick() },
                            enabled = !ocrBusy,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("solver_ocr_gallery_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.cd_ocr_pick_gallery),
                            )
                        }
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = { viewModel.onQueryChange(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("solver_input"),
                            placeholder = { Text(stringResource(R.string.solver_input_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.solveQuery() }),
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        )
                        FilledIconButton(
                            onClick = { viewModel.solveQuery() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("solver_submit_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_solver_submit),
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            if (state.ocrState !is OcrUiState.Idle) {
                OcrStatusPanel(
                    ocrState = state.ocrState,
                    ocrModelReady = state.ocrModelReady,
                    locale = locale,
                    onStartDownload = { viewModel.startModelDownload() },
                    onDraftEdited = { viewModel.onOcrDraftEdited(it) },
                    onConfirmExpression = { viewModel.confirmOcrExpression() },
                    onCancel = { viewModel.cancelOcrOperation() },
                    onDismiss = { viewModel.dismissOcrState() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            if (state.records.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.size(60.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.solver_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.solver_hero_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        quickPresets.forEachIndexed { idx, preset ->
                            val presetTitle = stringResource(preset.titleRes)
                            val presetExpression = preset.expression.asString()
                            Surface(
                                onClick = { viewModel.solveQuery(presetExpression) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("solver_quick_card_$idx"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = presetTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = extColors.technicalContainer,
                                            ) {
                                                Text(
                                                    text = preset.tag.asString(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                    ),
                                                    color = extColors.onTechnicalContainer,
                                                )
                                            }
                                        }
                                        Text(
                                            text = presetExpression,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = stringResource(R.string.cd_solver_try_preset, presetTitle),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.records, key = { it.id }) { rec ->
                        CalcCard(
                            containerColor = if (rec.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                            borderColor = if (rec.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = rec.input,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (rec.fromOcr) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = extColors.technicalContainer,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.solver_badge_ocr),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = extColors.onTechnicalContainer,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = rec.headline.asString(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (rec.isError) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.primary,
                                )
                                if (rec.steps.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    rec.steps.forEachIndexed { idx, step ->
                                        Text(
                                            text = "${idx + 1}. ${step.asString()}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                rec.graphExpression?.let { graphExpr ->
                                    Spacer(Modifier.height(2.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.openContextualGraph(graphExpr) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("solver_open_graph_${rec.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.solver_open_2d_graph))
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
                        .padding(vertical = 8.dp),
                ) {
                    items(examples) { ex ->
                        Surface(
                            onClick = { viewModel.solveQuery(ex) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ex,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCameraPermissionDialog) {
        CameraPermissionDialog(
            permanentlyDenied = cameraPermanentlyDenied,
            onRequestPermission = {
                showCameraPermissionDialog = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = {
                showCameraPermissionDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onUseGallery = {
                showCameraPermissionDialog = false
                launchGalleryPicker()
            },
            onDismiss = { showCameraPermissionDialog = false },
        )
    }

    state.activeGraphExpression?.let { activeGraph ->
        ContextualGraphSheet(
            expression = activeGraph,
            useRadians = state.useRadians,
            locale = locale,
            onToggleAngleUnit = { viewModel.toggleAngleUnit() },
            onDismiss = { viewModel.closeContextualGraph() },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
