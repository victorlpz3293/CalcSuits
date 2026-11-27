package com.calcsuits

import android.app.Application
import com.calcsuits.features.ai.ocr.OcrTempFileManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CalcSuitsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OcrTempFileManager.cleanupStaleFiles(this)
    }
}
