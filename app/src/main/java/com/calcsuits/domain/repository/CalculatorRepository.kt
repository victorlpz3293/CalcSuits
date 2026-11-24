package com.calcsuits.domain.repository

import com.calcsuits.data.local.datastore.AppSettings
import com.calcsuits.domain.model.History
import com.calcsuits.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface CalculatorRepository {
    fun getHistory(): Flow<List<History>>
    suspend fun saveHistory(expression: String, result: String)
    suspend fun clearHistory()

    fun getSettings(): Flow<AppSettings>
    fun isScientificExpanded(): Flow<Boolean>
    suspend fun setScientificExpanded(isExpanded: Boolean)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setUseRadians(useRadians: Boolean)
    suspend fun setDecimalPrecision(precision: Int)
    suspend fun setHapticEnabled(enabled: Boolean)
}
