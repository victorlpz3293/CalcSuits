package com.calcsuits.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import com.calcsuits.R

enum class AppThemeMode(val storageKey: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.theme_system),
    DARK("dark", R.string.theme_dark),
    OLED("oled", R.string.theme_oled),
    LIGHT("light", R.string.theme_light);

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: DARK
    }
}

@Immutable
data class CalcSuitsExtendedColors(
    val numericKey: Color,
    val operatorKey: Color,
    val scientificKey: Color,
    val borderSubtle: Color,
    val positive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val technicalAccent: Color,
    val technicalContainer: Color,
    val onTechnicalContainer: Color,
    val destructiveText: Color
)

private val CarbonExtendedColors = CalcSuitsExtendedColors(
    numericKey = CarbonKeyNumeric,
    operatorKey = CarbonKeyOperator,
    scientificKey = CarbonKeyScientific,
    borderSubtle = CarbonBorder,
    positive = BraunSage,
    positiveContainer = BraunSageContainerDark,
    onPositiveContainer = BraunOnSageContainerDark,
    technicalAccent = BraunAmber,
    technicalContainer = BraunAmberContainerDark,
    onTechnicalContainer = BraunOnAmberContainerDark,
    destructiveText = Color(0xFFE07467)
)

private val OledExtendedColors = CarbonExtendedColors.copy(
    numericKey = OledKeyNumeric,
    operatorKey = OledKeyOperator,
    scientificKey = OledKeyScientific,
    borderSubtle = OledBorder
)

private val PaperExtendedColors = CalcSuitsExtendedColors(
    numericKey = PaperKeyNumeric,
    operatorKey = PaperKeyOperator,
    scientificKey = PaperKeyScientific,
    borderSubtle = PaperBorder,
    positive = BraunSage,
    positiveContainer = BraunSageContainerLight,
    onPositiveContainer = BraunOnSageContainerLight,
    technicalAccent = BraunAmber,
    technicalContainer = BraunAmberContainerLight,
    onTechnicalContainer = BraunOnAmberContainerLight,
    destructiveText = BraunTerracottaLight
)

val LocalCalcSuitsColors = staticCompositionLocalOf { CarbonExtendedColors }

private val CarbonDarkColorScheme = darkColorScheme(
    primary = BraunOrange,
    onPrimary = BraunOnOrange,
    primaryContainer = BraunOrangeContainerDark,
    onPrimaryContainer = BraunOnOrangeContainerDark,
    secondary = BraunSage,
    onSecondary = BraunOnSage,
    secondaryContainer = BraunSageContainerDark,
    onSecondaryContainer = BraunOnSageContainerDark,
    tertiary = BraunAmber,
    onTertiary = BraunOnAmber,
    tertiaryContainer = BraunAmberContainerDark,
    onTertiaryContainer = BraunOnAmberContainerDark,
    background = CarbonBackground,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonKeyScientific,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = CarbonBackground,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonKeyNumeric,
    surfaceContainerHigh = CarbonKeyOperator,
    surfaceContainerHighest = CarbonBorder,
    outline = CarbonOutline,
    outlineVariant = CarbonBorder,
    error = BraunTerracottaDark,
    onError = BraunOnTerracotta,
    errorContainer = BraunTerracottaContainerDark,
    onErrorContainer = BraunOnTerracottaContainerDark
)

private val OledDarkColorScheme = CarbonDarkColorScheme.copy(
    background = OledBackground,
    surface = OledSurface,
    surfaceVariant = OledKeyScientific,
    surfaceContainerLowest = OledBackground,
    surfaceContainerLow = OledSurface,
    surfaceContainer = OledKeyNumeric,
    surfaceContainerHigh = OledKeyOperator,
    surfaceContainerHighest = OledBorder,
    outline = OledOutline,
    outlineVariant = OledBorder
)

private val PaperLightColorScheme = lightColorScheme(
    primary = BraunOrange,
    onPrimary = BraunOnOrange,
    primaryContainer = BraunOrangeContainerLight,
    onPrimaryContainer = BraunOnOrangeContainerLight,
    secondary = BraunSage,
    onSecondary = BraunOnSage,
    secondaryContainer = BraunSageContainerLight,
    onSecondaryContainer = BraunOnSageContainerLight,
    tertiary = BraunAmber,
    onTertiary = BraunOnAmber,
    tertiaryContainer = BraunAmberContainerLight,
    onTertiaryContainer = BraunOnAmberContainerLight,
    background = PaperBackground,
    onBackground = TextPrimaryLight,
    surface = PaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperKeyScientific,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperBackground,
    surfaceContainer = PaperKeyNumeric,
    surfaceContainerHigh = PaperKeyOperator,
    surfaceContainerHighest = PaperBorder,
    outline = PaperOutline,
    outlineVariant = PaperBorder,
    error = BraunTerracottaLight,
    onError = BraunOnTerracotta,
    errorContainer = BraunTerracottaContainerLight,
    onErrorContainer = BraunOnTerracottaContainerLight
)

val CalcSuitsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object CalcSuitsTheme {
    val colors: CalcSuitsExtendedColors
        @Composable
        get() = LocalCalcSuitsColors.current
}

@Composable
fun CalcSuitsTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK, AppThemeMode.OLED -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when (themeMode) {
        AppThemeMode.OLED -> OledDarkColorScheme
        AppThemeMode.DARK -> CarbonDarkColorScheme
        AppThemeMode.LIGHT -> PaperLightColorScheme
        AppThemeMode.SYSTEM -> if (systemDark) CarbonDarkColorScheme else PaperLightColorScheme
    }

    val extendedColors = when (themeMode) {
        AppThemeMode.OLED -> OledExtendedColors
        AppThemeMode.DARK -> CarbonExtendedColors
        AppThemeMode.LIGHT -> PaperExtendedColors
        AppThemeMode.SYSTEM -> if (systemDark) CarbonExtendedColors else PaperExtendedColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalCalcSuitsColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CalcSuitsShapes,
            content = content
        )
    }
}
