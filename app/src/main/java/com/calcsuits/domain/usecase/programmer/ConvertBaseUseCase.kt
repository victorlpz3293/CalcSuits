package com.calcsuits.domain.usecase.programmer

import javax.inject.Inject

class ConvertBaseUseCase @Inject constructor() {

    fun decode(value: String, fromBase: Int): Long? {
        val cleaned = value.trim().replace(" ", "")
        if (cleaned.isEmpty()) return 0L
        return try {
            if (fromBase == 10) {
                cleaned.toLong(10)
            } else {
                java.lang.Long.parseUnsignedLong(cleaned, fromBase)
            }
        } catch (_: Exception) {
            try {
                cleaned.toLong(fromBase)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun encodeUnsigned(value: Long, toBase: Int, wordSize: Int): String {
        val masked = if (wordSize == 64) value else value and maskFor(wordSize)
        return when (toBase) {
            2 -> {
                val padded = java.lang.Long.toBinaryString(masked).padStart(wordSize, '0')
                padded.chunked(4).joinToString(" ")
            }
            8 -> java.lang.Long.toOctalString(masked)
            10 -> normalizeSigned(value, wordSize).toString()
            16 -> formatGrouped(java.lang.Long.toHexString(masked).uppercase(), 4)
            else -> java.lang.Long.toUnsignedString(masked, toBase).uppercase()
        }
    }

    fun encodeRaw(value: Long, toBase: Int, wordSize: Int): String {
        val masked = if (wordSize == 64) value else value and maskFor(wordSize)
        return when (toBase) {
            2 -> java.lang.Long.toBinaryString(masked)
            8 -> java.lang.Long.toOctalString(masked)
            10 -> normalizeSigned(value, wordSize).toString()
            16 -> java.lang.Long.toHexString(masked).uppercase()
            else -> java.lang.Long.toUnsignedString(masked, toBase).uppercase()
        }
    }

    fun maskFor(size: Int): Long = when (size) {
        8 -> 0xFFL
        16 -> 0xFFFFL
        32 -> 0xFFFFFFFFL
        else -> -1L
    }

    fun normalizeSigned(value: Long, size: Int): Long {
        if (size == 64) return value
        val masked = value and maskFor(size)
        val signBit = 1L shl (size - 1)
        return if ((masked and signBit) != 0L) masked - (1L shl size) else masked
    }

    private fun formatGrouped(raw: String, groupSize: Int): String {
        if (raw.length <= groupSize) return raw
        val reversed = raw.reversed().chunked(groupSize).joinToString(" ")
        return reversed.reversed()
    }
}
