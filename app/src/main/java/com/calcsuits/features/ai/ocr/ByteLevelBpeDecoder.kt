package com.calcsuits.features.ai.ocr

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin ByteLevel BPE decoder compatible with HuggingFace `tokenizer.json`
 * used by TexTeller3-ONNX (RoBERTa/GPT-2 ByteLevel BPE, 11,500 vocab).
 */
class ByteLevelBpeDecoder(
    private val idToToken: Map<Int, String>,
    private val specialTokenIds: Set<Int>,
    val bosTokenId: Int,
    val eosTokenId: Int,
    val padTokenId: Int,
) {
    private val unicodeToByte: Map<Int, Int> = buildUnicodeToByteMap()

    fun decode(tokenIds: List<Int>, skipSpecialTokens: Boolean = true): String {
        val textBuilder = StringBuilder()
        for (id in tokenIds) {
            if (skipSpecialTokens && specialTokenIds.contains(id)) continue
            val token = idToToken[id] ?: continue
            textBuilder.append(token)
        }
        return decodeByteLevelString(textBuilder.toString()).trim()
    }

    internal fun decodeByteLevelString(encoded: String): String {
        val bytes = ByteArrayOutputStream(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val cp = encoded.codePointAt(i)
            val mapped = unicodeToByte[cp]
            if (mapped != null) {
                bytes.write(mapped)
            } else {
                val utf8 = String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8)
                bytes.write(utf8)
            }
            i += Character.charCount(cp)
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    companion object {
        fun fromFiles(tokenizerJson: File, tokenizerConfigJson: File): ByteLevelBpeDecoder {
            return fromJsonStrings(
                tokenizerJson.readText(StandardCharsets.UTF_8),
                tokenizerConfigJson.readText(StandardCharsets.UTF_8),
            )
        }

        fun fromJsonStrings(tokenizerJsonText: String, tokenizerConfigJsonText: String): ByteLevelBpeDecoder {
            val root = JSONObject(tokenizerJsonText)
            val config = JSONObject(tokenizerConfigJsonText)

            val idToToken = HashMap<Int, String>(12000)
            val specialIds = HashSet<Int>()

            val modelObj = root.optJSONObject("model")
                ?: throw IllegalArgumentException("tokenizer.json missing 'model' object")
            val vocabObj = modelObj.optJSONObject("vocab")
                ?: throw IllegalArgumentException("tokenizer.json missing 'model.vocab' object")

            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabObj.getInt(token)
                idToToken[id] = token
            }

            val addedTokens = root.optJSONArray("added_tokens")
            if (addedTokens != null) {
                for (idx in 0 until addedTokens.length()) {
                    val entry = addedTokens.getJSONObject(idx)
                    val id = entry.getInt("id")
                    val content = entry.getString("content")
                    idToToken[id] = content
                    if (entry.optBoolean("special", false)) {
                        specialIds.add(id)
                    }
                }
            }

            val tokenToId = HashMap<String, Int>(idToToken.size)
            for ((id, tok) in idToToken) {
                tokenToId[tok] = id
            }

            val bosTokenStr = extractTokenContent(config.opt("bos_token")) ?: "<s>"
            val eosTokenStr = extractTokenContent(config.opt("eos_token")) ?: "</s>"
            val padTokenStr = extractTokenContent(config.opt("pad_token")) ?: "<pad>"

            val bosId = tokenToId[bosTokenStr] ?: 0
            val padId = tokenToId[padTokenStr] ?: 1
            val eosId = tokenToId[eosTokenStr] ?: 2
            specialIds.add(bosId)
            specialIds.add(padId)
            specialIds.add(eosId)

            return ByteLevelBpeDecoder(
                idToToken = idToToken,
                specialTokenIds = specialIds,
                bosTokenId = bosId,
                eosTokenId = eosId,
                padTokenId = padId,
            )
        }

        private fun extractTokenContent(raw: Any?): String? = when (raw) {
            is String -> raw.ifEmpty { null }
            is JSONObject -> raw.optString("content", "").ifEmpty { null }
            else -> null
        }

        internal fun buildUnicodeToByteMap(): Map<Int, Int> {
            val bs = ArrayList<Int>(256)
            for (b in 33..126) bs.add(b)
            for (b in 161..172) bs.add(b)
            for (b in 174..255) bs.add(b)

            val cs = ArrayList<Int>(bs)
            var n = 0
            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val map = HashMap<Int, Int>(256)
            for (i in bs.indices) {
                map[cs[i]] = bs[i]
            }
            return map
        }
    }
}
