package com.calcsuits.data.repository

import com.calcsuits.data.local.datastore.AppSettings
import com.calcsuits.data.local.datastore.SettingsDataStore
import com.calcsuits.data.local.room.HistoryDao
import com.calcsuits.data.local.room.HistoryEntity
import com.calcsuits.domain.model.History
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculatorRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    private val settingsDataStore: SettingsDataStore
) : CalculatorRepository {

    override fun getHistory(): Flow<List<History>> {
        return historyDao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveHistory(expression: String, result: String) {
        historyDao.insertHistory(
            HistoryEntity(expression = expression, result = result)
        )
    }

    override suspend fun clearHistory() {
        historyDao.deleteAllHistory()
    }

    override fun getSettings(): Flow<AppSettings> = settingsDataStore.settings

    override fun isScientificExpanded(): Flow<Boolean> = settingsDataStore.isScientificExpanded

    override suspend fun setScientificExpanded(isExpanded: Boolean) {
        settingsDataStore.setScientificExpanded(isExpanded)
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        settingsDataStore.setThemeMode(mode)
    }

    override suspend fun setUseRadians(useRadians: Boolean) {
        settingsDataStore.setUseRadians(useRadians)
    }

    override suspend fun setDecimalPrecision(precision: Int) {
        settingsDataStore.setDecimalPrecision(precision)
    }

    override suspend fun setHapticEnabled(enabled: Boolean) {
        settingsDataStore.setHapticEnabled(enabled)
    }

    private fun HistoryEntity.toDomain(): History = History(
        id = id,
        expression = expression,
        result = result,
        timestamp = timestamp
    )
}
