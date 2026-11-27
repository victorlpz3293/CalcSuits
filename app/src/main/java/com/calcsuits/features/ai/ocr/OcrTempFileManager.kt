package com.calcsuits.features.ai.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Manages temporary OCR capture files strictly inside the app's private cache directory (`cacheDir/ocr_tmp/`).
 * Never writes to shared external storage and guarantees deletion on completion, cancellation, error, or app start.
 */
object OcrTempFileManager {
    private const val OCR_TMP_DIR_NAME = "ocr_tmp"

    data class TempCapture(
        val file: File,
        val uri: Uri,
    )

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, OCR_TMP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createTempCapture(context: Context): TempCapture {
        val dir = getTempDir(context)
        val file = File(dir, "scan_${UUID.randomUUID()}.jpg")
        val authority = "${context.packageName}.ocr.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return TempCapture(file = file, uri = uri)
    }

    fun deleteSafely(file: File?): Boolean {
        if (file == null) return false
        return runCatching {
            if (file.exists()) file.delete() else true
        }.getOrDefault(false)
    }

    /**
     * Sweeps any stale temporary OCR captures left behind by an abrupt process termination.
     */
    fun cleanupStaleFiles(context: Context): Int {
        val dir = File(context.cacheDir, OCR_TMP_DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) return 0
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && runCatching { f.delete() }.getOrDefault(false)) {
                deleted++
            }
        }
        return deleted
    }
}
