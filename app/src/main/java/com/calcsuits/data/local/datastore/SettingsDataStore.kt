package com.calcsuits.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
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

private val IS_SCIENTIFIC_KEY = booleanPreferencesKey("is_scientific")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val USE_OLED_BLACK_KEY = booleanPreferencesKey("use_oled_black")
private val USE_RADIANS_KEY = booleanPreferencesKey("use_radians")
private val DECIMAL_PRECISION_KEY = intPreferencesKey("decimal_precision")
private val HAPTIC_ENABLED_KEY = booleanPreferencesKey("haptic_enabled")

internal const val LEGACY_THEME_OLED = "oled"
internal const val LEGACY_THEME_CARBON = "carbon"

internal object LegacyThemeMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val rawTheme = currentData[THEME_MODE_KEY]
        return rawTheme == LEGACY_THEME_OLED || rawTheme == LEGACY_THEME_CARBON
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutable = currentData.toMutablePreferences()
        when (currentData[THEME_MODE_KEY]) {
            LEGACY_THEME_OLED -> {
                mutable[THEME_MODE_KEY] = AppThemeMode.DARK.storageKey
                mutable[USE_OLED_BLACK_KEY] = true
            }
            LEGACY_THEME_CARBON -> {
                mutable[THEME_MODE_KEY] = AppThemeMode.DARK.storageKey
                mutable[USE_OLED_BLACK_KEY] = false
            }
        }
        return mutable.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

internal fun resolveThemePreferences(
    rawThemeKey: String?,
    storedOledBlack: Boolean?
): Pair<AppThemeMode, Boolean> = when (rawThemeKey) {
    LEGACY_THEME_OLED -> AppThemeMode.DARK to true
    LEGACY_THEME_CARBON -> AppThemeMode.DARK to false
    else -> AppThemeMode.fromKey(rawThemeKey) to (storedOledBlack ?: false)
}

private val Context.dataStore by preferencesDataStore(
    name = "calcsuits_settings",
    produceMigrations = { listOf(LegacyThemeMigration) }
)

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val useOledBlack: Boolean = false,
    val isScientificExpanded: Boolean = false,
    val useRadians: Boolean = false,
    val decimalPrecision: Int = 8,
    val hapticEnabled: Boolean = true
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val (themeMode, useOledBlack) = resolveThemePreferences(
            rawThemeKey = prefs[THEME_MODE_KEY],
            storedOledBlack = prefs[USE_OLED_BLACK_KEY]
        )
        AppSettings(
            themeMode = themeMode,
            useOledBlack = useOledBlack,
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

    suspend fun setUseOledBlack(enabled: Boolean) {
        context.dataStore.edit { it[USE_OLED_BLACK_KEY] = enabled }
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
