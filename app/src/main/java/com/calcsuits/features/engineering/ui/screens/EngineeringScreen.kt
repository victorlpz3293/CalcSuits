package com.calcsuits.features.engineering.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuits.R
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.model.engineering.EngineeringSpecialty
import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.NetworkingTool
import com.calcsuits.domain.model.engineering.SubnetDesignMode
import com.calcsuits.features.engineering.ui.viewmodel.EngineeringState
import com.calcsuits.features.programmer.ui.screens.ProgrammerPinnedControls
import com.calcsuits.features.programmer.ui.screens.ProgrammerWorkspaceBody
import com.calcsuits.features.programmer.ui.viewmodel.ProgrammerState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcPinnedScaffold
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.CalcSuitsHeader
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.asString
import com.calcsuits.ui.util.rememberCurrentLocale

@Composable
fun EngineeringScreen(
    state: EngineeringState,
    programmerState: ProgrammerState,
    onMenuClick: () -> Unit,
    onSpecialtySelected: (EngineeringSpecialty) -> Unit,
    onBackToSpecialties: () -> Unit,
    onNetworkingToolSelected: (NetworkingTool) -> Unit,
    onFormulaSelected: (EngineeringFormula) -> Unit,
    onInputChange: (String, String) -> Unit,
    onClearInputs: () -> Unit = {},
    onIpv4IpChange: (String) -> Unit,
    onIpv4CidrChange: (String) -> Unit,
    onIpv4DecimalMaskChange: (String) -> Unit,
    onSubnetDesignModeSelected: (SubnetDesignMode) -> Unit,
    onDesignBaseIpChange: (String) -> Unit,
    onDesignBaseMaskChange: (String) -> Unit,
    onFlsmCriteriaSelected: (FlsmCriteria) -> Unit,
    onFlsmTargetChange: (String) -> Unit,
    onAddVlsmRequirement: (String) -> Unit,
    onUpdateVlsmRequirementName: (Long, String) -> Unit,
    onUpdateVlsmRequirementHosts: (Long, String) -> Unit,
    onRemoveVlsmRequirement: (Long) -> Unit,
    onBaseSelected: (Int) -> Unit,
    onWordSizeSelected: (Int) -> Unit,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearProgrammer: () -> Unit,
    onBitwise: (String) -> Unit,
    onToggleBit: (Int) -> Unit
) {
    BackHandler(enabled = state.selectedSpecialty != null) {
        onBackToSpecialties()
    }

    when (state.selectedSpecialty) {
        null -> EngineeringSpecialtiesHub(
            onMenuClick = onMenuClick,
            onSpecialtySelected = onSpecialtySelected
        )

        EngineeringSpecialty.ELECTRICAL -> ElectricalEngineeringView(
            state = state,
            onBackClick = onBackToSpecialties,
            onFormulaSelected = onFormulaSelected,
            onInputChange = onInputChange,
            onClearInputs = onClearInputs
        )

        EngineeringSpecialty.NETWORKING -> NetworkingEngineeringView(
            state = state,
            onBackClick = onBackToSpecialties,
            onToolSelected = onNetworkingToolSelected,
            onClearInputs = onClearInputs,
            onIpv4IpChange = onIpv4IpChange,
            onIpv4CidrChange = onIpv4CidrChange,
            onIpv4DecimalMaskChange = onIpv4DecimalMaskChange,
            onSubnetDesignModeSelected = onSubnetDesignModeSelected,
            onDesignBaseIpChange = onDesignBaseIpChange,
            onDesignBaseMaskChange = onDesignBaseMaskChange,
            onFlsmCriteriaSelected = onFlsmCriteriaSelected,
            onFlsmTargetChange = onFlsmTargetChange,
            onAddVlsmRequirement = onAddVlsmRequirement,
            onUpdateVlsmRequirementName = onUpdateVlsmRequirementName,
            onUpdateVlsmRequirementHosts = onUpdateVlsmRequirementHosts,
            onRemoveVlsmRequirement = onRemoveVlsmRequirement
        )

        EngineeringSpecialty.DIGITAL_ELECTRONICS -> DigitalElectronicsView(
            state = programmerState,
            onBackClick = onBackToSpecialties,
            onBaseSelected = onBaseSelected,
            onWordSizeSelected = onWordSizeSelected,
            onKeyPress = onKeyPress,
            onBackspace = onBackspace,
            onClear = onClearProgrammer,
            onBitwise = onBitwise,
            onToggleBit = onToggleBit
        )
    }
}

@Composable
private fun EngineeringSpecialtiesHub(
    onMenuClick: () -> Unit,
    onSpecialtySelected: (EngineeringSpecialty) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    Scaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.engineering),
                subtitle = stringResource(R.string.eng_specialties_subtitle),
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.eng_specialties_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )

            EngineeringSpecialty.entries.forEach { specialty ->
                Card(
                    onClick = { onSpecialtySelected(specialty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eng_specialty_${specialty.key}"),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = specialty.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(specialty.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(specialty.toolSummaryRes),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = extColors.technicalAccent
                            )
                            Text(
                                text = stringResource(specialty.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectricalEngineeringView(
    state: EngineeringState,
    onBackClick: () -> Unit,
    onFormulaSelected: (EngineeringFormula) -> Unit,
    onInputChange: (String, String) -> Unit,
    onClearInputs: () -> Unit
) {
    val formulas = EngineeringFormula.electricalFormulas
    val locale = rememberCurrentLocale()
    val scrollState = rememberScrollState()

    LaunchedEffect(state.selectedFormula) {
        scrollState.scrollTo(0)
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.eng_tool_electrical_calculator),
                subtitle = stringResource(R.string.eng_specialty_electrical).uppercase(locale),
                onMenuClick = onBackClick,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_engineering),
                navigationTestTag = "engineering_back_button",
                actions = {
                    IconButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("engineering_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        pinnedPadding = PaddingValues(top = 8.dp, bottom = 6.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
        contentSpacing = 14.dp,
        scrollState = scrollState,
        compactHeightThreshold = 200.dp,
        pinnedContent = {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(formulas, key = { it.key }) { formula ->
                    CalcFilterChip(
                        selected = state.selectedFormula == formula,
                        onClick = { onFormulaSelected(formula) },
                        label = {
                            Text(
                                text = stringResource(formula.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = formula.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("eng_formula_${formula.key}")
                    )
                }
            }
        }
    ) {
        ElectricalFormulaBody(
            state = state,
            onInputChange = onInputChange
        )
    }
}

@Composable
private fun NetworkingEngineeringView(
    state: EngineeringState,
    onBackClick: () -> Unit,
    onToolSelected: (NetworkingTool) -> Unit,
    onClearInputs: () -> Unit,
    onIpv4IpChange: (String) -> Unit,
    onIpv4CidrChange: (String) -> Unit,
    onIpv4DecimalMaskChange: (String) -> Unit,
    onSubnetDesignModeSelected: (SubnetDesignMode) -> Unit,
    onDesignBaseIpChange: (String) -> Unit,
    onDesignBaseMaskChange: (String) -> Unit,
    onFlsmCriteriaSelected: (FlsmCriteria) -> Unit,
    onFlsmTargetChange: (String) -> Unit,
    onAddVlsmRequirement: (String) -> Unit,
    onUpdateVlsmRequirementName: (Long, String) -> Unit,
    onUpdateVlsmRequirementHosts: (Long, String) -> Unit,
    onRemoveVlsmRequirement: (Long) -> Unit
) {
    val locale = rememberCurrentLocale()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val isIpv4Analysis = state.selectedNetworkingTool == NetworkingTool.IPV4_ANALYSIS

    LaunchedEffect(state.selectedNetworkingTool) {
        scrollState.scrollTo(0)
    }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(state.selectedNetworkingTool.titleRes),
                subtitle = stringResource(R.string.eng_specialty_networking).uppercase(locale),
                onMenuClick = onBackClick,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_engineering),
                navigationTestTag = "engineering_back_button",
                actions = {
                    IconButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("engineering_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        pinnedPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = if (isIpv4Analysis) PaddingValues(top = 6.dp, bottom = 12.dp) else PaddingValues(0.dp),
        contentSpacing = if (isIpv4Analysis) 14.dp else 0.dp,
        scrollState = scrollState,
        enableContentScroll = isIpv4Analysis,
        compactHeightThreshold = 200.dp,
        pinnedContent = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NetworkingTool.entries.forEachIndexed { index, tool ->
                    SegmentedButton(
                        selected = state.selectedNetworkingTool == tool,
                        onClick = { onToolSelected(tool) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = NetworkingTool.entries.size
                        ),
                        colors = calcSegmentedButtonColors(),
                        modifier = Modifier.testTag("eng_net_tool_${tool.key}")
                    ) {
                        Text(
                            text = stringResource(tool.titleRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) {
        if (isIpv4Analysis) {
            Ipv4AnalysisPanel(
                state = state,
                snackbarHostState = snackbarHostState,
                onIpChange = onIpv4IpChange,
                onCidrChange = onIpv4CidrChange,
                onDecimalMaskChange = onIpv4DecimalMaskChange
            )
        } else {
            SubnetDesignPanel(
                state = state,
                snackbarHostState = snackbarHostState,
                onModeSelected = onSubnetDesignModeSelected,
                onBaseIpChange = onDesignBaseIpChange,
                onBaseMaskChange = onDesignBaseMaskChange,
                onFlsmCriteriaSelected = onFlsmCriteriaSelected,
                onFlsmTargetChange = onFlsmTargetChange,
                onAddVlsmRequirement = onAddVlsmRequirement,
                onUpdateVlsmRequirementName = onUpdateVlsmRequirementName,
                onUpdateVlsmRequirementHosts = onUpdateVlsmRequirementHosts,
                onRemoveVlsmRequirement = onRemoveVlsmRequirement
            )
        }
    }
}

@Composable
private fun DigitalElectronicsView(
    state: ProgrammerState,
    onBackClick: () -> Unit,
    onBaseSelected: (Int) -> Unit,
    onWordSizeSelected: (Int) -> Unit,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onBitwise: (String) -> Unit,
    onToggleBit: (Int) -> Unit
) {
    var showBitGrid by remember { mutableStateOf(false) }

    CalcPinnedScaffold(
        topBar = {
            CalcSuitsHeader(
                title = stringResource(R.string.eng_tool_digital_logic_short),
                subtitle = stringResource(R.string.eng_digital_subtitle_format, state.wordSize, state.activeBase),
                onMenuClick = onBackClick,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.cd_back_to_engineering),
                navigationTestTag = "engineering_back_button"
            )
        },
        applyImePadding = false,
        enableContentScroll = false,
        pinnedPadding = PaddingValues(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 4.dp),
        pinnedSpacing = 4.dp,
        contentPadding = PaddingValues(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 8.dp),
        contentSpacing = 0.dp,
        compactHeightThreshold = 340.dp,
        pinnedContent = {
            ProgrammerPinnedControls(
                state = state,
                showBitGrid = showBitGrid,
                onShowBitGridChange = { showBitGrid = it },
                onWordSizeSelected = onWordSizeSelected,
                onBaseSelected = onBaseSelected
            )
        }
    ) {
        ProgrammerWorkspaceBody(
            state = state,
            showBitGrid = showBitGrid,
            onKeyPress = onKeyPress,
            onBackspace = onBackspace,
            onClear = onClear,
            onBitwise = onBitwise,
            onToggleBit = onToggleBit
        )
    }
}

@Composable
private fun ElectricalFormulaBody(
    state: EngineeringState,
    onInputChange: (String, String) -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    CalcCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.selectedFormula.equation.asString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = extColors.technicalAccent
            )
            Text(
                text = stringResource(state.selectedFormula.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.selectedFormula.variables.forEach { variable ->
                OutlinedTextField(
                    value = state.inputs[variable.key] ?: "",
                    onValueChange = { onInputChange(variable.key, it) },
                    label = { Text(stringResource(variable.labelRes)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eng_input_${variable.key}"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
        }
    }

    val out = state.output
    if (out.error != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        ) {
            Text(
                text = out.error.asString(),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium
            )
        }
    } else if (out.headline != null) {
        CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.eng_calculated_result),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = out.headline.asString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (out.details.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    out.details.forEach { detail ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = detail.label.asString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = detail.value.asString(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
