package com.calcsuits.features.ai.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuits.R
import com.calcsuits.data.local.datastore.SettingsDataStore
import com.calcsuits.domain.model.UiText
import com.calcsuits.features.ai.domain.LocalMathSolverUseCase
import com.calcsuits.features.ai.domain.MathIrNormalizationResult
import com.calcsuits.features.ai.domain.MathIrNormalizer
import com.calcsuits.features.ai.domain.SolverRecord
import com.calcsuits.features.ai.ocr.OcrImageDecoder
import com.calcsuits.features.ai.ocr.OcrModelManager
import com.calcsuits.features.ai.ocr.OcrRecognitionOutput
import com.calcsuits.features.ai.ocr.OcrRepository
import com.calcsuits.features.ai.ocr.OcrTempFileManager
import com.calcsuits.features.ai.ocr.Sha256Verifier
import com.calcsuits.features.graph.domain.AdaptedGraphExpression
import com.calcsuits.features.graph.domain.GraphExpressionAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

sealed interface OcrUiState {
    data object Idle : OcrUiState
    data object ModelRequired : OcrUiState
    data class Downloading(val progress: OcrModelManager.DownloadProgress?) : OcrUiState
    data object Processing : OcrUiState
    data class PendingConfirmation(
        val rawLatex: String,
        val editableExpression: String,
        val astValidation: MathIrNormalizationResult,
        val telemetry: OcrRecognitionOutput,
    ) : OcrUiState

    data class Error(val message: UiText) : OcrUiState
}

data class SolverUiState(
    val query: String = "",
    val records: List<SolverRecord> = emptyList(),
    val useRadians: Boolean = false,
    val ocrModelReady: Boolean = false,
    val ocrState: OcrUiState = OcrUiState.Idle,
    val activeGraphExpression: AdaptedGraphExpression? = null,
)

@HiltViewModel
class SolverViewModel private constructor(
    private val solverUseCase: LocalMathSolverUseCase,
    private val ocrRepository: OcrRepository,
    private val useRadiansFlow: Flow<Boolean>,
    private val onPersistUseRadians: suspend (Boolean) -> Unit,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    @Inject
    constructor(
        solverUseCase: LocalMathSolverUseCase,
        ocrRepository: OcrRepository,
        settingsDataStore: SettingsDataStore,
    ) : this(
        solverUseCase = solverUseCase,
        ocrRepository = ocrRepository,
        useRadiansFlow = settingsDataStore.settings.map { it.useRadians },
        onPersistUseRadians = { settingsDataStore.setUseRadians(it) },
    )

    internal companion object {
        fun createForTest(
            solverUseCase: LocalMathSolverUseCase,
            ocrRepository: OcrRepository,
            useRadiansFlow: Flow<Boolean>,
            onPersistUseRadians: suspend (Boolean) -> Unit = {},
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        ): SolverViewModel = SolverViewModel(
            solverUseCase = solverUseCase,
            ocrRepository = ocrRepository,
            useRadiansFlow = useRadiansFlow,
            onPersistUseRadians = onPersistUseRadians,
            mainDispatcher = dispatcher,
        )
    }

    private val _state = MutableStateFlow(
        SolverUiState(ocrModelReady = ocrRepository.isModelReady())
    )
    val state: StateFlow<SolverUiState> = _state.asStateFlow()

    private var activeOcrJob: Job? = null
    private var pendingTempCaptureFile: File? = null
    private var currentLocale: Locale = Locale.getDefault()

    init {
        viewModelScope.launch(mainDispatcher) {
            useRadiansFlow.collect { rad ->
                _state.update { current ->
                    val updatedGraph = current.activeGraphExpression?.let { active ->
                        GraphExpressionAdapter.adaptForContextualGraph(
                            rawExpression = current.records.lastOrNull { it.graphExpression == active }?.normalizedExpression
                                ?: active.evaluableExpression,
                            useRadians = rad,
                        ) ?: active
                    }
                    current.copy(useRadians = rad, activeGraphExpression = updatedGraph)
                }
            }
        }
    }

    fun updateLocale(locale: Locale) {
        currentLocale = locale
    }

    fun refreshModelAvailability() {
        val ready = ocrRepository.isModelReady()
        _state.update { current ->
            current.copy(
                ocrModelReady = ready,
                ocrState = if (ready && current.ocrState is OcrUiState.ModelRequired) {
                    OcrUiState.Idle
                } else {
                    current.ocrState
                },
            )
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun toggleAngleUnit() {
        val next = !_state.value.useRadians
        _state.update { it.copy(useRadians = next) }
        viewModelScope.launch(mainDispatcher) {
            runCatching { onPersistUseRadians(next) }
        }
    }

    fun solveQuery(raw: String = _state.value.query) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val record = solverUseCase.solve(
            rawInput = trimmed,
            useRadians = _state.value.useRadians,
            locale = currentLocale,
            fromOcr = false,
        )
        _state.update {
            it.copy(
                records = it.records + record,
                query = "",
            )
        }
    }

    fun requestModelDownloadPrompt() {
        val ready = ocrRepository.isModelReady()
        _state.update {
            it.copy(
                ocrModelReady = ready,
                ocrState = if (ready) OcrUiState.Idle else OcrUiState.ModelRequired,
            )
        }
    }

    fun startModelDownload() {
        if (activeOcrJob?.isActive == true) return
        activeOcrJob = viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(ocrState = OcrUiState.Downloading(null)) }
            try {
                ocrRepository.downloadModels { progress ->
                    _state.update { current ->
                        if (current.ocrState is OcrUiState.Downloading) {
                            current.copy(ocrState = OcrUiState.Downloading(progress))
                        } else {
                            current
                        }
                    }
                }
                _state.update {
                    it.copy(
                        ocrModelReady = true,
                        ocrState = OcrUiState.Idle,
                    )
                }
            } catch (_: CancellationException) {
                _state.update {
                    it.copy(
                        ocrModelReady = ocrRepository.isModelReady(),
                        ocrState = OcrUiState.Idle,
                    )
                }
            } catch (e: Sha256Verifier.IntegrityException) {
                _state.update {
                    it.copy(
                        ocrModelReady = false,
                        ocrState = OcrUiState.Error(UiText.StringResource(R.string.ocr_error_integrity)),
                    )
                }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        ocrModelReady = ocrRepository.isModelReady(),
                        ocrState = OcrUiState.Error(UiText.StringResource(R.string.ocr_error_download_failed)),
                    )
                }
            }
        }
    }

    fun registerPendingCameraCapture(file: File) {
        OcrTempFileManager.deleteSafely(pendingTempCaptureFile)
        pendingTempCaptureFile = file
    }

    fun onCameraCaptureResult(success: Boolean) {
        val file = pendingTempCaptureFile
        pendingTempCaptureFile = null
        if (!success || file == null) {
            OcrTempFileManager.deleteSafely(file)
            return
        }
        processTempImageFile(file)
    }

    fun processTempImageFile(tempFile: File) {
        if (!ocrRepository.isModelReady()) {
            OcrTempFileManager.deleteSafely(tempFile)
            _state.update { it.copy(ocrModelReady = false, ocrState = OcrUiState.ModelRequired) }
            return
        }
        launchOcrRecognition { ocrRepository.recognizeFromTempFile(tempFile) }
    }

    fun processPickedImageUri(uri: Uri?) {
        if (uri == null) return
        if (!ocrRepository.isModelReady()) {
            _state.update { it.copy(ocrModelReady = false, ocrState = OcrUiState.ModelRequired) }
            return
        }
        launchOcrRecognition { ocrRepository.recognizeFromUri(uri) }
    }

    private fun launchOcrRecognition(block: suspend () -> OcrRecognitionOutput) {
        activeOcrJob?.cancel()
        activeOcrJob = viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(ocrState = OcrUiState.Processing) }
            try {
                val output = block()
                val normalized = MathIrNormalizer.normalize(output.rawLatex)
                val initialEditable = if (normalized.ok) normalized.normalized else normalized.cleaned
                if (initialEditable.isBlank()) {
                    _state.update {
                        it.copy(
                            ocrState = OcrUiState.Error(
                                UiText.StringResource(R.string.ocr_error_no_formula_detected)
                            )
                        )
                    }
                    return@launch
                }
                // Mandatory editable confirmation step: NEVER evaluate or graph automatically.
                _state.update {
                    it.copy(
                        ocrState = OcrUiState.PendingConfirmation(
                            rawLatex = output.rawLatex,
                            editableExpression = initialEditable,
                            astValidation = normalized,
                            telemetry = output,
                        )
                    )
                }
            } catch (_: CancellationException) {
                _state.update { it.copy(ocrState = OcrUiState.Idle) }
            } catch (_: Sha256Verifier.IntegrityException) {
                _state.update {
                    it.copy(
                        ocrModelReady = false,
                        ocrState = OcrUiState.Error(UiText.StringResource(R.string.ocr_error_integrity)),
                    )
                }
            } catch (_: OcrImageDecoder.InvalidImageException) {
                _state.update {
                    it.copy(
                        ocrState = OcrUiState.Error(UiText.StringResource(R.string.ocr_error_invalid_image)),
                    )
                }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        ocrState = OcrUiState.Error(UiText.StringResource(R.string.ocr_error_recognition_failed)),
                    )
                }
            }
        }
    }

    fun onOcrDraftEdited(newExpression: String) {
        _state.update { current ->
            val pending = current.ocrState as? OcrUiState.PendingConfirmation ?: return@update current
            val revalidated = MathIrNormalizer.normalize(newExpression)
            current.copy(
                ocrState = pending.copy(
                    editableExpression = newExpression,
                    astValidation = revalidated,
                )
            )
        }
    }

    /**
     * Explicit user confirmation: sends the user-approved expression to `LocalMathSolverUseCase`
     * (which reuses `MathExpressionEvaluator`) and closes the OCR confirmation state.
     */
    fun confirmOcrExpression() {
        val pending = _state.value.ocrState as? OcrUiState.PendingConfirmation ?: return
        val approved = pending.editableExpression.trim()
        if (approved.isBlank()) return
        val record = solverUseCase.solve(
            rawInput = approved,
            useRadians = _state.value.useRadians,
            locale = currentLocale,
            fromOcr = true,
        )
        _state.update {
            it.copy(
                records = it.records + record,
                ocrState = OcrUiState.Idle,
            )
        }
    }

    fun cancelOcrOperation() {
        activeOcrJob?.cancel()
        activeOcrJob = null
        OcrTempFileManager.deleteSafely(pendingTempCaptureFile)
        pendingTempCaptureFile = null
        _state.update { it.copy(ocrState = OcrUiState.Idle) }
    }

    fun dismissOcrState() {
        _state.update { it.copy(ocrState = OcrUiState.Idle) }
    }

    fun openContextualGraph(expression: AdaptedGraphExpression) {
        _state.update { it.copy(activeGraphExpression = expression) }
    }

    fun closeContextualGraph() {
        _state.update { it.copy(activeGraphExpression = null) }
    }

    fun onScreenDisposed() {
        activeOcrJob?.cancel()
        activeOcrJob = null
        OcrTempFileManager.deleteSafely(pendingTempCaptureFile)
        pendingTempCaptureFile = null
        viewModelScope.launch(mainDispatcher) {
            ocrRepository.releaseResources()
        }
    }

    override fun onCleared() {
        activeOcrJob?.cancel()
        OcrTempFileManager.deleteSafely(pendingTempCaptureFile)
        pendingTempCaptureFile = null
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            ocrRepository.releaseResources()
        }
        super.onCleared()
    }
}
