package com.calcsuits.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.calcsuits.features.programmer.ui.viewmodel.ProgrammerViewModel
import com.calcsuits.features.settings.ui.screens.SettingsScreen
import com.calcsuits.features.settings.ui.viewmodel.SettingsViewModel
import com.calcsuits.ui.screens.CalculatorScreen
import com.calcsuits.ui.util.rememberCurrentLocale
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
        composable(Screen.Ai.route) {
            AiAssistantScreen(onMenuClick = onOpenDrawer)
        }
        composable(Screen.Converter.route) {
            val vm: ConverterViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val locale = rememberCurrentLocale()
            LaunchedEffect(locale) { vm.updateLocale(locale) }
            ConverterScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onCategorySelected = vm::onCategorySelected,
                onValueChanged = vm::onValueChange,
                onUnitChange = vm::onUnitChange,
                onSwap = vm::swapUnits,
                onRefreshCurrency = vm::refreshCurrencyRates
            )
        }
        composable(Screen.Finance.route) {
            val vm: FinanceViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            FinanceScreen(
                state = state,
                onMenuClick = onOpenDrawer,
                onTabSelected = vm::onTabSelected,
                onCurrencySelected = vm::onCurrencySelected,
                onLoanSimulationModeSelected = vm::onLoanSimulationModeSelected,
                onAmortizationSystemSelected = vm::onAmortizationSystemSelected,
                onPaymentFrequencySelected = vm::onPaymentFrequencySelected,
                onLoanTermUnitSelected = vm::onLoanTermUnitSelected,
                onRateConventionSelected = vm::onRateConventionSelected,
                onAmountChange = vm::onAmountChange,
                onDownPaymentChange = vm::onDownPaymentChange,
                onInterestRateChange = vm::onInterestRateChange,
                onYearsChange = vm::onYearsChange,
                onPrincipalChange = vm::onPrincipalChange,
                onAnnualRateChange = vm::onAnnualRateChange,
                onTermYearsChange = vm::onTermYearsChange,
                onFrequencyChange = vm::onFrequencyChange,
                onTaxTypeSelected = vm::onTaxTypeSelected,
                onCustomTaxLabelChange = vm::onCustomTaxLabelChange,
                onTaxBaseChange = vm::onTaxBaseChange,
                onTaxRateChange = vm::onTaxRateChange,
                onTaxIncludedChange = vm::onTaxIncludedChange
            )
        }
        composable(Screen.Engineering.route) {
            val vm: EngineeringViewModel = hiltViewModel()
            val programmerVm: ProgrammerViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val programmerState by programmerVm.state.collectAsStateWithLifecycle()
            val locale = rememberCurrentLocale()
            LaunchedEffect(locale) { vm.recalculate() }
            EngineeringScreen(
                state = state,
                programmerState = programmerState,
                onMenuClick = onOpenDrawer,
                onSpecialtySelected = vm::onSpecialtySelected,
                onBackToSpecialties = vm::onBackToSpecialties,
                onNetworkingToolSelected = vm::onNetworkingToolSelected,
                onFormulaSelected = vm::onFormulaSelected,
                onInputChange = vm::onInputChange,
                onClearInputs = vm::clearInputs,
                onIpv4IpChange = vm::onIpv4IpChange,
                onIpv4CidrChange = vm::onIpv4CidrChange,
                onIpv4DecimalMaskChange = vm::onIpv4DecimalMaskChange,
                onSubnetDesignModeSelected = vm::onSubnetDesignModeSelected,
                onDesignBaseIpChange = vm::onDesignBaseIpChange,
                onDesignBaseMaskChange = vm::onDesignBaseMaskChange,
                onFlsmCriteriaSelected = vm::onFlsmCriteriaSelected,
                onFlsmTargetChange = vm::onFlsmTargetChange,
                onAddVlsmRequirement = vm::onAddVlsmRequirement,
                onUpdateVlsmRequirementName = vm::onUpdateVlsmRequirementName,
                onUpdateVlsmRequirementHosts = vm::onUpdateVlsmRequirementHosts,
                onRemoveVlsmRequirement = vm::onRemoveVlsmRequirement,
                onBaseSelected = programmerVm::selectBase,
                onWordSizeSelected = programmerVm::selectWordSize,
                onKeyPress = programmerVm::onKeyPress,
                onBackspace = programmerVm::onBackspace,
                onClearProgrammer = programmerVm::onClear,
                onBitwise = programmerVm::onBitwise,
                onToggleBit = programmerVm::toggleBit
            )
        }
        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = hiltViewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                onMenuClick = onOpenDrawer,
                onThemeModeChange = vm::setThemeMode,
                onUseOledBlackChange = vm::setUseOledBlack,
                onUseRadiansChange = vm::setUseRadians,
                onDecimalPrecisionChange = vm::setDecimalPrecision,
                onHapticChange = vm::setHapticEnabled,
                onClearHistory = vm::clearHistory
            )
        }
    }
}
