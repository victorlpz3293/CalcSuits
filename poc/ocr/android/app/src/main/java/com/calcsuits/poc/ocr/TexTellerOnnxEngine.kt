package com.calcsuits.poc.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Isolated on-device ARM64 ONNX Runtime engine for TexTeller-ONNX (q4f16).
 *
 * Security invariants:
 * - Verifies SHA-256 of ALL 4 required artifacts BEFORE opening any OrtSession or tokenizer file.
 * - Zero network usage (app manifest does not declare INTERNET).
 *
 * Note on latency:
 * - Upstream onnx-community/TexTeller-ONNX decoder does not export KV-cache
 *   (inputs: input_ids, encoder_hidden_states). MAX_NEW_TOKENS is capped to 36
 *   with a 5-repeat degeneration guard matching the Linux POC.
 */
class TexTellerOnnxEngine private constructor(
    private val env: OrtEnvironment,
    private val encoderSession: OrtSession,
    private val decoderSession: OrtSession,
    private val decoder: ByteLevelBpeDecoder,
    val verifiedArtifacts: Map<String, Sha256Verifier.VerifiedArtifact>,
    val verifyMs: Long,
    val loadMs: Long,
    val providerUsed: String,
) : Closeable {

    data class RecognitionResult(
        val rawLatex: String,
        val tokenIds: List<Int>,
        val preprocessMs: Long,
        val encoderMs: Long,
        val decoderMs: Long,
        val totalMs: Long,
        val nativeHeapMb: Double,
        val jvmHeapMb: Double,
        val stoppedByRepeatGuard: Boolean,
    )

    fun recognize(bitmap: Bitmap, maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS): RecognitionResult {
        val tStart = SystemClock.elapsedRealtime()

        val gray = TexTellerImagePreprocessor.fromBitmap(bitmap)
        val prep = TexTellerImagePreprocessor.preprocess(gray)
        val tPre = SystemClock.elapsedRealtime()

        val encInputName = encoderSession.inputNames.first()
        val encOutputName = encoderSession.outputNames.first()
        val decInputNames = decoderSession.inputNames.toList()
        val decOutputName = decoderSession.outputNames.first()

        val inputIdsName = decInputNames.firstOrNull { it.contains("input_ids") } ?: decInputNames[0]
        val hiddenName = decInputNames.firstOrNull { it.contains("encoder") || it.contains("hidden") } ?: decInputNames[1]

        val generated = ArrayList<Int>(maxNewTokens + 1)
        generated.add(decoder.bosTokenId)
        var repeatCount = 0
        var stoppedByRepeatGuard = false

        var tEncDone = tPre
        OnnxTensor.createTensor(env, FloatBuffer.wrap(prep.data), prep.shape).use { pixelTensor ->
            encoderSession.run(mapOf(encInputName to pixelTensor)).use { encOut ->
                tEncDone = SystemClock.elapsedRealtime()
                val encoderHidden = encOut[encOutputName].get() as OnnxTensor

                for (step in 0 until maxNewTokens) {
                    val seqLen = generated.size
                    val idsBuf = LongArray(seqLen) { generated[it].toLong() }
                    val bestId = OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(idsBuf),
                        longArrayOf(1L, seqLen.toLong()),
                    ).use { idsTensor ->
                        decoderSession.run(
                            mapOf(
                                inputIdsName to idsTensor,
                                hiddenName to encoderHidden,
                            ),
                        ).use { decOut ->
                            val logitsTensor = decOut[decOutputName].get() as OnnxTensor
                            argmaxLastToken(logitsTensor, seqLen)
                        }
                    }

                    if (generated.last() == bestId) {
                        repeatCount++
                    } else {
                        repeatCount = 0
                    }
                    generated.add(bestId)

                    if (bestId == decoder.eosTokenId) break
                    if (repeatCount >= REPEAT_GUARD_LIMIT) {
                        stoppedByRepeatGuard = true
                        break
                    }
                }
            }
        }

        val tEnd = SystemClock.elapsedRealtime()
        val rawLatex = decoder.decode(generated.drop(1), skipSpecialTokens = true)
        val runtime = Runtime.getRuntime()
        val jvmHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)

        return RecognitionResult(
            rawLatex = rawLatex,
            tokenIds = generated,
            preprocessMs = tPre - tStart,
            encoderMs = tEncDone - tPre,
            decoderMs = tEnd - tEncDone,
            totalMs = tEnd - tStart,
            nativeHeapMb = nativeHeapMb,
            jvmHeapMb = jvmHeapMb,
            stoppedByRepeatGuard = stoppedByRepeatGuard,
        )
    }

    private fun argmaxLastToken(logitsTensor: OnnxTensor, seqLen: Int): Int {
        val shape = logitsTensor.info.shape
        val vocabSize = shape.last().toInt()
        val buf = logitsTensor.floatBuffer
        val offset = (seqLen - 1) * vocabSize
        var bestId = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (v in 0 until vocabSize) {
            val value = buf.get(offset + v)
            if (value > bestVal) {
                bestVal = value
                bestId = v
            }
        }
        return bestId
    }

    override fun close() {
        runCatching { encoderSession.close() }
        runCatching { decoderSession.close() }
    }

    companion object {
        const val DEFAULT_MAX_NEW_TOKENS = 36
        const val REPEAT_GUARD_LIMIT = 5

        fun loadVerified(
            modelDir: File,
            intraOpThreads: Int = 4,
            tryXnnpack: Boolean = true,
        ): TexTellerOnnxEngine {
            val tVerify0 = SystemClock.elapsedRealtime()
            val verified = Sha256Verifier.verifyDirectory(modelDir, TexTellerManifest.EXPECTED_SHA256)
            val verifyMs = SystemClock.elapsedRealtime() - tVerify0

            val tLoad0 = SystemClock.elapsedRealtime()
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(intraOpThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            var providerUsed = "CPU"
            if (tryXnnpack) {
                runCatching {
                    opts.addXnnpack(mapOf("intra_op_num_threads" to intraOpThreads.toString()))
                    providerUsed = "XNNPACK+CPU"
                }
            }

            val encoderFile = verified.getValue(TexTellerManifest.ENCODER_FILE).file
            val decoderFile = verified.getValue(TexTellerManifest.DECODER_FILE).file
            val tokenizerFile = verified.getValue(TexTellerManifest.TOKENIZER_FILE).file
            val tokenizerConfigFile = verified.getValue(TexTellerManifest.TOKENIZER_CONFIG_FILE).file

            val encoderSession = env.createSession(encoderFile.absolutePath, opts)
            val decoderSession = env.createSession(decoderFile.absolutePath, opts)
            val bpeDecoder = ByteLevelBpeDecoder.fromFiles(tokenizerFile, tokenizerConfigFile)
            val loadMs = SystemClock.elapsedRealtime() - tLoad0

            return TexTellerOnnxEngine(
                env = env,
                encoderSession = encoderSession,
                decoderSession = decoderSession,
                decoder = bpeDecoder,
                verifiedArtifacts = verified,
                verifyMs = verifyMs,
                loadMs = loadMs,
                providerUsed = providerUsed,
            )
        }
    }
}
