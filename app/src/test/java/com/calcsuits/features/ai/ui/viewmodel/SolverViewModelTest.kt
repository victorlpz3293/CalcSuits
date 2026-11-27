package com.calcsuits.features.ai.ui.viewmodel

import android.net.Uri
import com.calcsuits.features.ai.domain.LocalMathSolverUseCase
import com.calcsuits.features.ai.ocr.OcrModelManager
import com.calcsuits.features.ai.ocr.OcrRecognitionOutput
import com.calcsuits.features.ai.ocr.OcrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class SolverViewModelTest {

    private class FakeOcrRepository(
        var modelReady: Boolean = true,
        var nextOutput: OcrRecognitionOutput = OcrRecognitionOutput(
            rawLatex = """\[x^{ \wedge}2-5x+6=0\]""",
            totalMs = 1900,
            preprocessMs = 80,
            encoderMs = 520,
            decoderMs = 1300,
            nativeHeapMb = 180.0,
            jvmHeapMb = 64.0,
            polarityInverted = true,
            usesKvCache = true,
            sampleSize = 2,
            generatedTokens = 12,
        ),
    ) : OcrRepository {
        var releaseCalls = 0
        var downloadCalls = 0

        override fun isModelReady(): Boolean = modelReady
        override fun isEngineLoaded(): Boolean = false

        override suspend fun downloadModels(onProgress: (OcrModelManager.DownloadProgress) -> Unit) {
            downloadCalls++
            modelReady = true
        }

        override suspend fun recognizeFromUri(uri: Uri): OcrRecognitionOutput = nextOutput

        override suspend fun recognizeFromTempFile(tempFile: File): OcrRecognitionOutput {
            tempFile.delete()
            return nextOutput
        }

        override suspend fun releaseResources() {
            releaseCalls++
        }
    }

    @Test
    fun ocrNeverEvaluatesAutomaticallyAndRequiresExplicitUserConfirmation() {
        val radFlow = MutableStateFlow(false)
        val fakeRepo = FakeOcrRepository(modelReady = true)
        val vm = SolverViewModel.createForTest(
            solverUseCase = LocalMathSolverUseCase(),
            ocrRepository = fakeRepo,
            useRadiansFlow = radFlow,
        )
        vm.updateLocale(Locale.US)

        val tempFile = File.createTempFile("solver_vm_test_", ".jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        vm.registerPendingCameraCapture(tempFile)
        vm.onCameraCaptureResult(success = true)

        // MUST pause in PendingConfirmation without adding any solved record
        assertTrue(vm.state.value.records.isEmpty())
        val pending = vm.state.value.ocrState as? OcrUiState.PendingConfirmation
        assertNotNull("Expected PendingConfirmation state", pending)
        assertEquals("x^2 - 5 * x + 6 = 0", pending!!.editableExpression)
        assertTrue(pending.astValidation.ok)

        // User edits expression before confirming
        vm.onOcrDraftEdited("3x + 7 = 22")
        val edited = vm.state.value.ocrState as OcrUiState.PendingConfirmation
        assertEquals("3x + 7 = 22", edited.editableExpression)
        assertTrue(edited.astValidation.ok)
        assertTrue("Editing draft must not evaluate yet", vm.state.value.records.isEmpty())

        // Explicit user confirmation evaluates and exposes contextual 2D graph
        vm.confirmOcrExpression()
        assertEquals(OcrUiState.Idle, vm.state.value.ocrState)
        assertEquals(1, vm.state.value.records.size)
        val rec = vm.state.value.records.single()
        assertTrue(rec.fromOcr)
        assertNotNull("Linear equation in x must expose contextual 2D graph", rec.graphExpression)
    }

    @Test
    fun cancelledCameraCaptureDeletesTempFileAndDisposalReleasesNativeResources() {
        val radFlow = MutableStateFlow(false)
        val fakeRepo = FakeOcrRepository(modelReady = true)
        val vm = SolverViewModel.createForTest(
            solverUseCase = LocalMathSolverUseCase(),
            ocrRepository = fakeRepo,
            useRadiansFlow = radFlow,
        )

        val tempFile = File.createTempFile("solver_cancel_", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(tempFile.exists())
        vm.registerPendingCameraCapture(tempFile)
        vm.onCameraCaptureResult(success = false)
        assertFalse("Cancelled camera capture temp file must be deleted immediately", tempFile.exists())
        assertEquals(OcrUiState.Idle, vm.state.value.ocrState)

        vm.onScreenDisposed()
        assertEquals(1, fakeRepo.releaseCalls)
    }

    @Test
    fun radDegSyncAffectsEvaluationAndContextualGraphVisibilityFollowsExpressionKind() {
        val radFlow = MutableStateFlow(false)
        var persistedRad: Boolean? = null
        val vm = SolverViewModel.createForTest(
            solverUseCase = LocalMathSolverUseCase(),
            ocrRepository = FakeOcrRepository(modelReady = false),
            useRadiansFlow = radFlow,
            onPersistUseRadians = {
                persistedRad = it
                radFlow.value = it
            },
        )
        vm.updateLocale(Locale.US)

        // Constant expression -> NO 2D graph button
        vm.solveQuery("125 + 35 * 2")
        assertNull(vm.state.value.records.last().graphExpression)

        // Percentage -> NO 2D graph button
        vm.solveQuery("15% de 250")
        assertNull(vm.state.value.records.last().graphExpression)

        // Quadratic equation -> YES 2D graph button
        vm.solveQuery("x^2 - 5x + 6 = 0")
        assertNotNull(vm.state.value.records.last().graphExpression)

        // Function f(x) without '=' -> YES 2D graph button
        vm.solveQuery("x^2 + 5x + 6")
        assertNotNull(vm.state.value.records.last().graphExpression)

        // Toggle DEG -> RAD persists and updates state
        assertFalse(vm.state.value.useRadians)
        vm.toggleAngleUnit()
        assertTrue(vm.state.value.useRadians)
        assertEquals(true, persistedRad)
    }
}
