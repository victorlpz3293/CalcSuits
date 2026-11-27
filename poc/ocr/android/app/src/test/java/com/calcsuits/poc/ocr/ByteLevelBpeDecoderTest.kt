package com.calcsuits.poc.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteLevelBpeDecoderTest {

    @Test
    fun decode_decodesLatexWithSpacesAndSkipsSpecialTokens() {
        val tokenizerJson = """
            {
              "model": {
                "type": "BPE",
                "vocab": {
                  "<s>": 0,
                  "<pad>": 1,
                  "</s>": 2,
                  "\\frac": 10,
                  "{": 11,
                  "3": 12,
                  "x": 13,
                  "Ġ+": 14,
                  "Ġ2": 15,
                  "}": 16,
                  "5": 17
                }
              },
              "added_tokens": [
                {"id": 0, "content": "<s>", "special": true},
                {"id": 1, "content": "<pad>", "special": true},
                {"id": 2, "content": "</s>", "special": true}
              ]
            }
        """.trimIndent()
        val tokenizerConfigJson = """
            {
              "bos_token": "<s>",
              "eos_token": "</s>",
              "pad_token": "<pad>"
            }
        """.trimIndent()

        val decoder = ByteLevelBpeDecoder.fromJsonStrings(tokenizerJson, tokenizerConfigJson)
        assertEquals(0, decoder.bosTokenId)
        assertEquals(2, decoder.eosTokenId)
        assertEquals(1, decoder.padTokenId)

        val decoded = decoder.decode(listOf(0, 10, 11, 12, 13, 14, 15, 16, 11, 17, 16, 2))
        assertEquals("\\frac{3x + 2}{5}", decoded)
    }

    @Test
    fun decode_decodesMultibyteUtf8ByteLevelTokens() {
        // UTF-8 bytes for "×" (U+00D7) are 0xC3 (195) and 0x97 (151).
        // In GPT-2 bytes_to_unicode():
        //   0xC3 (195) -> U+00C3 ('Ã')
        //   0x97 (151): 33 unmapped bytes in [0..32] + (151 - 127) = 57 -> 256 + 57 = 313 = U+0139 ('Ĺ')
        val tokenizerJson = """
            {
              "model": {
                "type": "BPE",
                "vocab": {
                  "<s>": 0,
                  "</s>": 2,
                  "12": 5,
                  "Ã\u0139": 6,
                  "4": 7
                }
              },
              "added_tokens": [
                {"id": 0, "content": "<s>", "special": true},
                {"id": 2, "content": "</s>", "special": true}
              ]
            }
        """.trimIndent()
        val tokenizerConfigJson = """{"bos_token":"<s>","eos_token":"</s>","pad_token":"<pad>"}"""

        val decoder = ByteLevelBpeDecoder.fromJsonStrings(tokenizerJson, tokenizerConfigJson)
        assertEquals("12×4", decoder.decode(listOf(5, 6, 7)))
    }
}
