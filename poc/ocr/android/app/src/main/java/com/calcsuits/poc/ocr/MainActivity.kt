package com.calcsuits.poc.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Minimal offline-only physical ARM64 benchmark harness for Phase 4.1.
 *
 * - Zero INTERNET permission.
 * - Zero broad storage permissions (uses Photo Picker + app-private getExternalFilesDir).
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: TexTellerOnnxEngine? = null
    private lateinit var logView: TextView
    private lateinit var pickBtn: Button
    private lateinit var batchBtn: Button

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            runSingleUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }

        val title = TextView(this).apply {
            text = getString(R.string.poc_subtitle)
            textSize = 18f
        }
        val deviceInfo = TextView(this).apply {
            text = "Device: ${Build.MANUFACTURER} ${Build.MODEL} | API ${Build.VERSION.SDK_INT} | ABI=${Build.SUPPORTED_ABIS.joinToString()}"
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }

        val verifyBtn = Button(this).apply {
            text = getString(R.string.btn_verify_models)
            minimumHeight = 144
            setOnClickListener { loadModelVerified() }
        }
        pickBtn = Button(this).apply {
            text = getString(R.string.btn_pick_photo)
            minimumHeight = 144
            isEnabled = false
            setOnClickListener {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
        batchBtn = Button(this).apply {
            text = getString(R.string.btn_run_batch)
            minimumHeight = 144
            isEnabled = false
            setOnClickListener { runLocalBenchmarkDirectory() }
        }

        logView = TextView(this).apply {
            text = buildString {
                appendLine(getString(R.string.status_idle))
                appendLine("Models dir: ${modelsDir().absolutePath}")
                appendLine("Benchmark dir: ${benchmarkDir().absolutePath}")
                appendLine("Results dir: ${resultsDir().absolutePath}")
            }
            textSize = 13f
            setPadding(0, 20, 0, 20)
        }

        root.addView(title)
        root.addView(deviceInfo)
        root.addView(verifyBtn)
        root.addView(pickBtn)
        root.addView(batchBtn)
        root.addView(ScrollView(this).apply { addView(logView) })
        setContentView(root)
    }

    private fun modelsDir(): File = File(getExternalFilesDir(null), "models").apply { mkdirs() }
    private fun benchmarkDir(): File = File(getExternalFilesDir(null), "benchmark").apply { mkdirs() }
    private fun resultsDir(): File = File(getExternalFilesDir(null), "results").apply { mkdirs() }

    private fun appendLog(line: String) {
        logView.append("\n$line")
    }

    private fun loadModelVerified() {
        appendLog("Verifying SHA-256 of pinned TexTeller-ONNX q4f16 artifacts...")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    engine?.close()
                    TexTellerOnnxEngine.loadVerified(modelsDir())
                }
            }
            outcome.onSuccess { loaded ->
                engine = loaded
                pickBtn.isEnabled = true
                batchBtn.isEnabled = true
                appendLog(
                    "PASS: Verified ${loaded.verifiedArtifacts.size} files in ${loaded.verifyMs}ms; " +
                        "loaded ONNX sessions (${loaded.providerUsed}) in ${loaded.loadMs}ms.",
                )
            }.onFailure { err ->
                appendLog("FAIL: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    private fun runSingleUri(uri: Uri) {
        val currentEngine = engine ?: return
        appendLog("Running offline OCR on picked image...")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                            ?: error("Failed to decode selected image")
                        try {
                            currentEngine.recognize(bmp)
                        } finally {
                            bmp.recycle()
                        }
                    } ?: error("Cannot open URI stream")
                }
            }
            outcome.onSuccess { res ->
                appendLog(
                    "LaTeX: ${res.rawLatex}\n" +
                        "  total=${res.totalMs}ms (prep=${res.preprocessMs}ms, enc=${res.encoderMs}ms, dec=${res.decoderMs}ms) " +
                        "nativeHeap=${String.format(Locale.US, "%.1f", res.nativeHeapMb)}MB " +
                        "jvmHeap=${String.format(Locale.US, "%.1f", res.jvmHeapMb)}MB " +
                        "repeatGuard=${res.stoppedByRepeatGuard}",
                )
            }.onFailure { err ->
                appendLog("ERROR: ${err.message}")
            }
        }
    }

    private fun runLocalBenchmarkDirectory() {
        val currentEngine = engine ?: return
        val dir = benchmarkDir()
        appendLog("Scanning ${dir.absolutePath} for PNG/JPG images...")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val images = (dir.listFiles() ?: emptyArray())
                        .filter { it.isFile && (it.name.endsWith(".png", true) || it.name.endsWith(".jpg", true)) }
                        .sortedBy { it.name }
                    require(images.isNotEmpty()) { "No .png/.jpg files found in ${dir.absolutePath}" }

                    val items = JSONArray()
                    val latencies = ArrayList<Long>(images.size)
                    var peakNativeMb = 0.0
                    var peakJvmMb = 0.0

                    for (file in images) {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            ?: error("Failed to decode ${file.name}")
                        val res = try {
                            currentEngine.recognize(bmp)
                        } finally {
                            bmp.recycle()
                        }
                        latencies.add(res.totalMs)
                        if (res.nativeHeapMb > peakNativeMb) peakNativeMb = res.nativeHeapMb
                        if (res.jvmHeapMb > peakJvmMb) peakJvmMb = res.jvmHeapMb

                        items.put(
                            JSONObject().apply {
                                put("filename", file.name)
                                put("raw_latex", res.rawLatex)
                                put("preprocess_ms", res.preprocessMs)
                                put("encoder_ms", res.encoderMs)
                                put("decoder_ms", res.decoderMs)
                                put("total_ms", res.totalMs)
                                put("native_heap_mb", res.nativeHeapMb)
                                put("jvm_heap_mb", res.jvmHeapMb)
                                put("stopped_by_repeat_guard", res.stoppedByRepeatGuard)
                            },
                        )
                    }

                    latencies.sort()
                    val p50 = latencies[latencies.size / 2]
                    val p95 = latencies[((latencies.size - 1) * 0.95).toInt()]
                    val report = JSONObject().apply {
                        put("device_manufacturer", Build.MANUFACTURER)
                        put("device_model", Build.MODEL)
                        put("android_sdk", Build.VERSION.SDK_INT)
                        put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                        put("model_id", TexTellerManifest.MODEL_ID)
                        put("pinned_revision", TexTellerManifest.PINNED_REVISION)
                        put("provider_used", currentEngine.providerUsed)
                        put("verify_ms", currentEngine.verifyMs)
                        put("load_ms", currentEngine.loadMs)
                        put("images_processed", images.size)
                        put("latency_min_ms", latencies.first())
                        put("latency_p50_ms", p50)
                        put("latency_p95_ms", p95)
                        put("latency_max_ms", latencies.last())
                        put("peak_native_heap_mb", peakNativeMb)
                        put("peak_jvm_heap_mb", peakJvmMb)
                        put("results", items)
                    }
                    val outFile = File(resultsDir(), "arm64_benchmark.json")
                    outFile.writeText(report.toString(2))
                    Pair(outFile, report)
                }
            }
            outcome.onSuccess { (outFile, report) ->
                appendLog(
                    "BENCHMARK DONE (${report.getInt("images_processed")} images): " +
                        "p50=${report.getLong("latency_p50_ms")}ms, p95=${report.getLong("latency_p95_ms")}ms, " +
                        "peakNative=${String.format(Locale.US, "%.1f", report.getDouble("peak_native_heap_mb"))}MB\n" +
                        "Saved: ${outFile.absolutePath}",
                )
            }.onFailure { err ->
                appendLog("BENCHMARK ERROR: ${err.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        engine?.close()
    }
}
