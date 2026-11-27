package com.calcsuits.poc.ocr

/**
 * Pinned artifact manifest for TexTeller-ONNX (q4f16).
 *
 * Source: https://huggingface.co/onnx-community/TexTeller-ONNX
 * Revision: 9727784d91d7f8437dc7140941c4335284ce075e
 * License: Apache-2.0
 */
object TexTellerManifest {
    const val MODEL_ID = "onnx-community/TexTeller-ONNX"
    const val PINNED_REVISION = "9727784d91d7f8437dc7140941c4335284ce075e"

    const val ENCODER_FILE = "encoder_model_q4f16.onnx"
    const val DECODER_FILE = "decoder_model_q4f16.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"

    val EXPECTED_SHA256: Map<String, String> = linkedMapOf(
        ENCODER_FILE to "c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb",
        DECODER_FILE to "edebc4c90596d13313382eecfeef89f0041efe7bfa4e89391d5b5dbabab25100",
        TOKENIZER_FILE to "ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14",
        TOKENIZER_CONFIG_FILE to "74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea",
    )
}
