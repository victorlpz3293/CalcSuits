package com.calcsuits.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.calcsuits.R

enum class DrawerSection(@StringRes val labelRes: Int) {
    CORE(R.string.drawer_section_core),
    TECHNICAL(R.string.drawer_section_technical),
    SYSTEM(R.string.drawer_section_system)
}

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val section: DrawerSection
) {
    object Calculator : Screen("calculator", R.string.calculator, R.string.drawer_sub_calculator, Icons.Default.Calculate, DrawerSection.CORE)
    object Graph : Screen("graph", R.string.graphs, R.string.drawer_sub_graphs, Icons.AutoMirrored.Filled.ShowChart, DrawerSection.CORE)
    object Ai : Screen("ai_assistant", R.string.solver, R.string.drawer_sub_solver, Icons.Default.Functions, DrawerSection.CORE)

    object Converter : Screen("converter", R.string.converter, R.string.drawer_sub_converter, Icons.Default.SwapHoriz, DrawerSection.TECHNICAL)
    object Finance : Screen("finance", R.string.finance, R.string.drawer_sub_finance, Icons.Default.Payments, DrawerSection.TECHNICAL)
    object Engineering : Screen("engineering", R.string.engineering, R.string.drawer_sub_engineering, Icons.Default.Engineering, DrawerSection.TECHNICAL)
    object Programmer : Screen("programmer", R.string.programmer, R.string.drawer_sub_programmer, Icons.Default.Terminal, DrawerSection.TECHNICAL)

    object Settings : Screen("settings", R.string.settings, R.string.drawer_sub_settings, Icons.Default.Settings, DrawerSection.SYSTEM)

    companion object {
        val items: List<Screen> by lazy {
            listOf(
                Calculator,
                Graph,
                Ai,
                Converter,
                Finance,
                Engineering,
                Programmer,
                Settings
            )
        }
    }
}
