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
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark);

    companion object {
        fun fromKey(key: String?): AppThemeMode = when (key) {
            SYSTEM.storageKey -> SYSTEM
            LIGHT.storageKey -> LIGHT
            DARK.storageKey, "carbon", "oled" -> DARK
            else -> DARK
        }
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
    secondary = TextSecondaryDark,
    onSecondary = CarbonBackground,
    secondaryContainer = CarbonKeyNumeric,
    onSecondaryContainer = TextPrimaryDark,
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
    onSecondary = OledBackground,
    secondaryContainer = OledKeyNumeric,
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
    secondary = TextSecondaryLight,
    onSecondary = PaperSurface,
    secondaryContainer = PaperKeyNumeric,
    onSecondaryContainer = TextPrimaryLight,
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
    usePureBlackOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        !isDark -> PaperLightColorScheme
        usePureBlackOled -> OledDarkColorScheme
        else -> CarbonDarkColorScheme
    }

    val extendedColors = when {
        !isDark -> PaperExtendedColors
        usePureBlackOled -> OledExtendedColors
        else -> CarbonExtendedColors
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
