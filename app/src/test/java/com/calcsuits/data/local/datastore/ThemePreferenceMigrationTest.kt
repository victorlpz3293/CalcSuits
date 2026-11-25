package com.calcsuits.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.calcsuits.ui.theme.AppThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceMigrationTest {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val useOledBlackKey = booleanPreferencesKey("use_oled_black")

    @Test
    fun legacyOledMigratesToDarkWithOledEnabled() = runTest {
        val oldPrefs = preferencesOf(themeModeKey to LEGACY_THEME_OLED)

        assertTrue(LegacyThemeMigration.shouldMigrate(oldPrefs))
        val migrated = LegacyThemeMigration.migrate(oldPrefs)

        assertEquals(AppThemeMode.DARK.storageKey, migrated[themeModeKey])
        assertEquals(true, migrated[useOledBlackKey])
        assertEquals(AppThemeMode.DARK to true, resolveThemePreferences(LEGACY_THEME_OLED, null))
    }

    @Test
    fun legacyCarbonMigratesToDarkWithOledDisabled() = runTest {
        val oldPrefs = preferencesOf(themeModeKey to LEGACY_THEME_CARBON)

        assertTrue(LegacyThemeMigration.shouldMigrate(oldPrefs))
        val migrated = LegacyThemeMigration.migrate(oldPrefs)

        assertEquals(AppThemeMode.DARK.storageKey, migrated[themeModeKey])
        assertEquals(false, migrated[useOledBlackKey])
        assertEquals(AppThemeMode.DARK to false, resolveThemePreferences(LEGACY_THEME_CARBON, true))
    }

    @Test
    fun modernThemeKeysDoNotTriggerMigrationAndPreserveOledPreference() = runTest {
        val systemWithOled = preferencesOf(
            themeModeKey to AppThemeMode.SYSTEM.storageKey,
            useOledBlackKey to true
        )
        val lightWithoutOled = preferencesOf(
            themeModeKey to AppThemeMode.LIGHT.storageKey,
            useOledBlackKey to false
        )
        val darkWithOled = preferencesOf(
            themeModeKey to AppThemeMode.DARK.storageKey,
            useOledBlackKey to true
        )

        assertFalse(LegacyThemeMigration.shouldMigrate(systemWithOled))
        assertFalse(LegacyThemeMigration.shouldMigrate(lightWithoutOled))
        assertFalse(LegacyThemeMigration.shouldMigrate(darkWithOled))
        assertFalse(LegacyThemeMigration.shouldMigrate(preferencesOf()))

        assertEquals(AppThemeMode.SYSTEM to true, resolveThemePreferences("system", true))
        assertEquals(AppThemeMode.LIGHT to false, resolveThemePreferences("light", false))
        assertEquals(AppThemeMode.DARK to true, resolveThemePreferences("dark", true))
        assertEquals(AppThemeMode.DARK to false, resolveThemePreferences(null, null))
    }
}
