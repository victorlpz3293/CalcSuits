package com.calcsuits.features.ai.ocr

/**
 * Pinned artifact manifest for TexTeller3-ONNX (q4f16) with merged KV-cache decoder.
 *
 * Source: https://huggingface.co/onnx-community/TexTeller3-ONNX
 * Revision: ed90c6164810242882ca90863bb85697a76f4841
 * License: Apache-2.0 (100% compatible with GNU GPL-3.0)
 */
object TexTellerManifest {
    const val MODEL_ID = "onnx-community/TexTeller3-ONNX"
    const val PINNED_REVISION = "ed90c6164810242882ca90863bb85697a76f4841"
    const val BASE_HTTPS_URL =
        "https://huggingface.co/$MODEL_ID/resolve/$PINNED_REVISION"

    const val ENCODER_FILE = "encoder_model_q4f16.onnx"
    const val DECODER_MERGED_FILE = "decoder_model_merged_q4f16.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"

    data class ArtifactSpec(
        val fileName: String,
        val remoteRelativePath: String,
        val expectedBytes: Long,
        val expectedSha256: String,
    ) {
        val downloadUrl: String
            get() = "$BASE_HTTPS_URL/$remoteRelativePath"
    }

    val ARTIFACTS: List<ArtifactSpec> = listOf(
        ArtifactSpec(
            fileName = ENCODER_FILE,
            remoteRelativePath = "onnx/$ENCODER_FILE",
            expectedBytes = 49_751_260L,
            expectedSha256 = "c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb",
        ),
        ArtifactSpec(
            fileName = DECODER_MERGED_FILE,
            remoteRelativePath = "onnx/$DECODER_MERGED_FILE",
            expectedBytes = 152_805_414L,
            expectedSha256 = "1961768786dfe44227c8b8ccd4951cb300eb546c89d64070ab282285c30cf9ca",
        ),
        ArtifactSpec(
            fileName = TOKENIZER_FILE,
            remoteRelativePath = TOKENIZER_FILE,
            expectedBytes = 1_370_259L,
            expectedSha256 = "ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14",
        ),
        ArtifactSpec(
            fileName = TOKENIZER_CONFIG_FILE,
            remoteRelativePath = TOKENIZER_CONFIG_FILE,
            expectedBytes = 592_769L,
            expectedSha256 = "74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea",
        ),
    )

    val TOTAL_BYTES: Long = ARTIFACTS.sumOf { it.expectedBytes }

    val EXPECTED_SHA256: Map<String, String> = ARTIFACTS.associate {
        it.fileName to it.expectedSha256
    }

    fun isAllowedDownloadUrl(url: String): Boolean {
        return url.startsWith("$BASE_HTTPS_URL/") && ARTIFACTS.any { it.downloadUrl == url }
    }
}
