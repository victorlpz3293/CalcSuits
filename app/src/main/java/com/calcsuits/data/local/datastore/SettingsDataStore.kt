package com.calcsuits.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calcsuits.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "calcsuits_settings")

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val isScientificExpanded: Boolean = false,
    val useRadians: Boolean = false,
    val decimalPrecision: Int = 8,
    val hapticEnabled: Boolean = true
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_SCIENTIFIC_KEY = booleanPreferencesKey("is_scientific")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val USE_RADIANS_KEY = booleanPreferencesKey("use_radians")
    private val DECIMAL_PRECISION_KEY = intPreferencesKey("decimal_precision")
    private val HAPTIC_ENABLED_KEY = booleanPreferencesKey("haptic_enabled")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = AppThemeMode.fromKey(prefs[THEME_MODE_KEY]),
            isScientificExpanded = prefs[IS_SCIENTIFIC_KEY] ?: false,
            useRadians = prefs[USE_RADIANS_KEY] ?: false,
            decimalPrecision = (prefs[DECIMAL_PRECISION_KEY] ?: 8).coerceIn(2, 12),
            hapticEnabled = prefs[HAPTIC_ENABLED_KEY] ?: true
        )
    }

    val isScientificExpanded: Flow<Boolean> = settings.map { it.isScientificExpanded }

    suspend fun setScientificExpanded(isExpanded: Boolean) {
        context.dataStore.edit { it[IS_SCIENTIFIC_KEY] = isExpanded }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.storageKey }
    }

    suspend fun setUseRadians(useRadians: Boolean) {
        context.dataStore.edit { it[USE_RADIANS_KEY] = useRadians }
    }

    suspend fun setDecimalPrecision(precision: Int) {
        context.dataStore.edit { it[DECIMAL_PRECISION_KEY] = precision.coerceIn(2, 12) }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTIC_ENABLED_KEY] = enabled }
    }
}
