package com.calcsuits.ui.navigation

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

enum class DrawerSection(val label: String) {
    CORE("CÁLCULO"),
    TECHNICAL("HERRAMIENTAS TÉCNICAS"),
    SYSTEM("SISTEMA")
}

sealed class Screen(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val section: DrawerSection
) {
    object Calculator : Screen("calculator", "Calculadora", "Básica y científica", Icons.Default.Calculate, DrawerSection.CORE)
    object Graph : Screen("graph", "Gráficas 2D", "Plotter interactivo", Icons.AutoMirrored.Filled.ShowChart, DrawerSection.CORE)
    object Ai : Screen("ai_assistant", "Solucionador", "Ecuaciones y expresiones", Icons.Default.Functions, DrawerSection.CORE)

    object Converter : Screen("converter", "Conversores", "9 categorías de unidades", Icons.Default.SwapHoriz, DrawerSection.TECHNICAL)
    object Finance : Screen("finance", "Finanzas", "Préstamos, interés e IVA", Icons.Default.Payments, DrawerSection.TECHNICAL)
    object Engineering : Screen("engineering", "Ingeniería", "Ohm, potencia e IPv4", Icons.Default.Engineering, DrawerSection.TECHNICAL)
    object Programmer : Screen("programmer", "Programador", "HEX, BIN y bitwise", Icons.Default.Terminal, DrawerSection.TECHNICAL)

    object Settings : Screen("settings", "Ajustes", "Tema y precisión", Icons.Default.Settings, DrawerSection.SYSTEM)

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
