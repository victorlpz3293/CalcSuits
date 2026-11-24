package com.calcsuits.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.calcsuits.features.ai.ui.screens.AiAssistantScreen
import com.calcsuits.features.converter.ui.screens.ConverterScreen
import com.calcsuits.features.converter.ui.viewmodel.ConverterViewModel
import com.calcsuits.features.engineering.ui.screens.EngineeringScreen
import com.calcsuits.features.engineering.ui.viewmodel.EngineeringViewModel
import com.calcsuits.features.finance.ui.screens.FinanceScreen
import com.calcsuits.features.finance.ui.viewmodel.FinanceViewModel
import com.calcsuits.features.graph.ui.screens.GraphScreen
import com.calcsuits.features.programmer.ui.screens.ProgrammerScreen
import com.calcsuits.features.programmer.ui.viewmodel.ProgrammerViewModel
import com.calcsuits.features.settings.ui.screens.SettingsScreen
import com.calcsuits.features.settings.ui.viewmodel.SettingsViewModel
import com.calcsuits.ui.screens.CalculatorScreen
import com.calcsuits.viewmodel.CalculatorViewModel

@Composable
fun NavGraph(navController: NavHostController, onOpenDrawer: () -> Unit) {
    NavHost(navController, startDestination = Screen.Calculator.route) {
        composable(Screen.Calculator.route) {
            val vm: CalculatorViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            CalculatorScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onAction = vm::onAction
            )
        }
        composable(Screen.Converter.route) {
            val vm: ConverterViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            ConverterScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onCategorySelected = vm::onCategorySelected,
                onValueChanged = vm::onValueChange,
                onUnitChange = vm::onUnitChange,
                onSwap = vm::swapUnits
            )
        }
        composable(Screen.Finance.route) {
            val vm: FinanceViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            FinanceScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onTabSelected = vm::onTabSelected,
                onAmountChange = vm::onAmountChange,
                onInterestRateChange = vm::onInterestRateChange,
                onYearsChange = vm::onYearsChange,
                onPrincipalChange = vm::onPrincipalChange,
                onAnnualRateChange = vm::onAnnualRateChange,
                onTermYearsChange = vm::onTermYearsChange,
                onFrequencyChange = vm::onFrequencyChange,
                onTaxBaseChange = vm::onTaxBaseChange,
                onTaxRateChange = vm::onTaxRateChange,
                onTaxIncludedChange = vm::onTaxIncludedChange
            )
        }
        composable(Screen.Programmer.route) {
            val vm: ProgrammerViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            ProgrammerScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onBaseSelected = vm::selectBase,
                onWordSizeSelected = vm::selectWordSize,
                onKeyPress = vm::onKeyPress,
                onBackspace = vm::onBackspace,
                onClear = vm::onClear,
                onBitwise = vm::onBitwise,
                onToggleBit = vm::toggleBit
            )
        }
        composable(Screen.Engineering.route) {
            val vm: EngineeringViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            EngineeringScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onFormulaSelected = vm::onFormulaSelected,
                onInputChange = vm::onInputChange,
                onClearInputs = vm::clearInputs
            )
        }
        composable(Screen.Graph.route) {
            GraphScreen(onMenuClick = onOpenDrawer)
        }
        composable(Screen.Ai.route) {
            AiAssistantScreen(onMenuClick = onOpenDrawer)
        }
        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = hiltViewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                onMenuClick = onOpenDrawer,
                onThemeModeChange = vm::setThemeMode,
                onUseRadiansChange = vm::setUseRadians,
                onDecimalPrecisionChange = vm::setDecimalPrecision,
                onHapticChange = vm::setHapticEnabled,
                onClearHistory = vm::clearHistory
            )
        }
    }
}
