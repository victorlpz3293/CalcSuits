package com.calcsuits.features.engineering.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuits.R
import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.FlsmDesignResult
import com.calcsuits.domain.model.engineering.FlsmOutcome
import com.calcsuits.domain.model.engineering.HistoricalClassfulClass
import com.calcsuits.domain.model.engineering.Ipv4AddressScope
import com.calcsuits.domain.model.engineering.Ipv4AnalysisOutcome
import com.calcsuits.domain.model.engineering.Ipv4AnalysisResult
import com.calcsuits.domain.model.engineering.Ipv4Error
import com.calcsuits.domain.model.engineering.SpecialPrefixKind
import com.calcsuits.domain.model.engineering.SubnetAllocation
import com.calcsuits.domain.model.engineering.SubnetDesignMode
import com.calcsuits.domain.model.engineering.VlsmDesignResult
import com.calcsuits.domain.model.engineering.VlsmOutcome
import com.calcsuits.features.engineering.ui.viewmodel.EngineeringState
import com.calcsuits.ui.components.CalcCard
import com.calcsuits.ui.components.CalcFilterChip
import com.calcsuits.ui.components.CalcResultCard
import com.calcsuits.ui.components.calcSegmentedButtonColors
import com.calcsuits.ui.theme.CalcSuitsTheme
import com.calcsuits.ui.theme.JetBrainsMonoFontFamily
import com.calcsuits.ui.util.rememberCurrentLocale
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val ANALYSIS_CIDR_PRESETS = listOf("/8", "/16", "/24", "/26", "/28", "/30", "/31", "/32")
private val DESIGN_CIDR_PRESETS = listOf("/8", "/16", "/20", "/24", "/26", "/28", "/30")

@Composable
internal fun Ipv4AnalysisPanel(
    state: EngineeringState,
    snackbarHostState: SnackbarHostState,
    onIpChange: (String) -> Unit,
    onCidrChange: (String) -> Unit,
    onDecimalMaskChange: (String) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val locale = rememberCurrentLocale()
    val intFormat = NumberFormat.getIntegerInstance(locale)
    val scope = rememberCoroutineScope()

    CalcCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.eng_subnetting_equation),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = extColors.technicalAccent
            )
            Text(
                text = stringResource(R.string.eng_ipv4_analysis_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    QuickCidrChipStrip(
        presets = ANALYSIS_CIDR_PRESETS,
        currentCidr = state.ipv4CidrInput,
        tagPrefix = "eng_cidr_",
        onSelect = onCidrChange
    )

    CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.ipv4IpInput,
                onValueChange = onIpChange,
                label = { Text(stringResource(R.string.eng_var_ipv4)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_input_ip"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.ipv4CidrInput,
                    onValueChange = onCidrChange,
                    label = {
                        Text(
                            text = stringResource(R.string.eng_ipv4_cidr_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(0.38f)
                        .testTag("eng_input_cidr"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = state.ipv4MaskInput,
                    onValueChange = onDecimalMaskChange,
                    label = {
                        Text(
                            text = stringResource(R.string.eng_ipv4_decimal_mask_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(0.62f)
                        .testTag("eng_input_mask"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
        }
    }

    when (val outcome = state.ipv4Analysis) {
        is Ipv4AnalysisOutcome.Failure -> {
            if (outcome.error != Ipv4Error.MissingInput) {
                Ipv4ErrorBanner(error = outcome.error, locale = locale)
            }
        }
        is Ipv4AnalysisOutcome.Success -> {
            val res = outcome.result
            Ipv4AnalysisResultCards(
                result = res,
                intFormat = intFormat,
                onCopySummary = {
                    val summary = formatIpv4Summary(context, res, intFormat)
                    clipboard.setText(AnnotatedString(summary))
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.eng_subnet_copied))
                    }
                }
            )
        }
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Ipv4AnalysisResultCards(
    result: Ipv4AnalysisResult,
    intFormat: NumberFormat,
    onCopySummary: () -> Unit
) {
    val extColors = CalcSuitsTheme.colors

    CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.eng_calculated_result),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.cidrNotation,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            SpecialPrefixNotice(result.specialPrefixKind)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DetailKeyValueRow(stringResource(R.string.eng_detail_network_addr), result.networkAddress)
            DetailKeyValueRow(stringResource(R.string.eng_detail_cidr_prefix), "/${result.prefixLength}")
            DetailKeyValueRow(stringResource(R.string.eng_detail_subnet_mask), result.subnetMask)
            DetailKeyValueRow(stringResource(R.string.eng_detail_wildcard), result.wildcardMask)
            when (result.specialPrefixKind) {
                SpecialPrefixKind.POINT_TO_POINT_31 -> {
                    DetailKeyValueRow(
                        stringResource(R.string.eng_detail_broadcast),
                        stringResource(R.string.eng_broadcast_na_31)
                    )
                    DetailKeyValueRow(stringResource(R.string.eng_detail_p2p_endpoint_1), result.firstUsableHost)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_p2p_endpoint_2), result.lastUsableHost)
                }
                SpecialPrefixKind.HOST_ROUTE_32 -> {
                    DetailKeyValueRow(
                        stringResource(R.string.eng_detail_broadcast),
                        stringResource(R.string.eng_broadcast_na_32)
                    )
                    DetailKeyValueRow(stringResource(R.string.eng_detail_host_address), result.firstUsableHost)
                }
                else -> {
                    DetailKeyValueRow(stringResource(R.string.eng_detail_broadcast), result.broadcastAddress)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_first_host), result.firstUsableHost)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_last_host), result.lastUsableHost)
                }
            }
            DetailKeyValueRow(stringResource(R.string.eng_detail_total_addresses), intFormat.format(result.totalAddresses))
            DetailKeyValueRow(stringResource(R.string.eng_detail_usable_hosts), intFormat.format(result.usableHosts))

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCopySummary,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_copy_subnet_summary")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.eng_copy_summary),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Binary representation card
    CalcCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.eng_detail_binary_section),
                style = MaterialTheme.typography.labelSmall,
                color = extColors.technicalAccent,
                fontWeight = FontWeight.Bold
            )
            BinaryLine(label = stringResource(R.string.eng_detail_ip_binary), bits = result.ipBinary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BinaryLine(label = stringResource(R.string.eng_detail_mask_binary), bits = result.maskBinary)
        }
    }

    // Modern IANA/CIDR vs Historical Classful classification card
    CalcCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.eng_detail_classification_section),
                style = MaterialTheme.typography.labelSmall,
                color = extColors.technicalAccent,
                fontWeight = FontWeight.Bold
            )
            DetailStackedRow(
                label = stringResource(R.string.eng_detail_iana_scope),
                value = stringResource(scopeLabelRes(result.scope))
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailStackedRow(
                label = stringResource(R.string.eng_detail_historical_class),
                value = stringResource(historicalClassLabelRes(result.historicalClass))
            )
            if (result.isSpecialOrReservedRange) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = extColors.technicalContainer,
                    border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = extColors.technicalAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.eng_special_range_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.onTechnicalContainer
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.eng_historical_class_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecialPrefixNotice(kind: SpecialPrefixKind) {
    val noteRes = when (kind) {
        SpecialPrefixKind.NONE -> return
        SpecialPrefixKind.POINT_TO_POINT_31 -> R.string.eng_special_prefix_31
        SpecialPrefixKind.HOST_ROUTE_32 -> R.string.eng_special_prefix_32
        SpecialPrefixKind.DEFAULT_ROUTE_0 -> R.string.eng_special_prefix_0
    }
    val extColors = CalcSuitsTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = extColors.technicalContainer,
        border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = extColors.technicalAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = extColors.onTechnicalContainer
            )
        }
    }
}

@Composable
private fun BinaryLine(label: String, bits: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = bits,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun SubnetDesignPanel(
    state: EngineeringState,
    snackbarHostState: SnackbarHostState,
    onModeSelected: (SubnetDesignMode) -> Unit,
    onBaseIpChange: (String) -> Unit,
    onBaseMaskChange: (String) -> Unit,
    onFlsmCriteriaSelected: (FlsmCriteria) -> Unit,
    onFlsmTargetChange: (String) -> Unit,
    onAddVlsmRequirement: (String) -> Unit,
    onUpdateVlsmRequirementName: (Long, String) -> Unit,
    onUpdateVlsmRequirementHosts: (Long, String) -> Unit,
    onRemoveVlsmRequirement: (Long) -> Unit
) {
    val locale = rememberCurrentLocale()
    val intFormat = NumberFormat.getIntegerInstance(locale)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val onCopyFlsm: (FlsmDesignResult) -> Unit = { result ->
        clipboard.setText(AnnotatedString(formatFlsmSummary(context, result, intFormat)))
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.eng_flsm_copied))
        }
    }
    val onCopyVlsm: (VlsmDesignResult) -> Unit = { result ->
        clipboard.setText(AnnotatedString(formatVlsmSummary(context, result, intFormat, locale)))
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.eng_vlsm_copied))
        }
    }
    val onAddReq: () -> Unit = {
        val nextIndex = state.vlsmRequirements.size + 1
        onAddVlsmRequirement(context.getString(R.string.eng_vlsm_default_prefix_name, nextIndex))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (constraints.hasBoundedHeight) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "design_mode_selector") {
                    SubnetDesignModeSelector(
                        selectedMode = state.subnetDesignMode,
                        onModeSelected = onModeSelected
                    )
                }
                item(key = "design_quick_cidr") {
                    QuickCidrChipStrip(
                        presets = DESIGN_CIDR_PRESETS,
                        currentCidr = state.designBaseMask,
                        tagPrefix = "eng_design_cidr_",
                        onSelect = onBaseMaskChange
                    )
                }
                item(key = "design_base_network") {
                    BaseNetworkCard(
                        baseIp = state.designBaseIp,
                        baseMask = state.designBaseMask,
                        onBaseIpChange = onBaseIpChange,
                        onBaseMaskChange = onBaseMaskChange
                    )
                }

                if (state.subnetDesignMode == SubnetDesignMode.FLSM) {
                    flsmLazyItems(
                        state = state,
                        intFormat = intFormat,
                        locale = locale,
                        onCriteriaSelected = onFlsmCriteriaSelected,
                        onTargetChange = onFlsmTargetChange,
                        onCopyTable = onCopyFlsm
                    )
                } else {
                    vlsmLazyItems(
                        state = state,
                        intFormat = intFormat,
                        locale = locale,
                        onAddRequirement = onAddReq,
                        onUpdateName = onUpdateVlsmRequirementName,
                        onUpdateHosts = onUpdateVlsmRequirementHosts,
                        onRemove = onRemoveVlsmRequirement,
                        onCopyTable = onCopyVlsm
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SubnetDesignModeSelector(
                    selectedMode = state.subnetDesignMode,
                    onModeSelected = onModeSelected
                )
                QuickCidrChipStrip(
                    presets = DESIGN_CIDR_PRESETS,
                    currentCidr = state.designBaseMask,
                    tagPrefix = "eng_design_cidr_",
                    onSelect = onBaseMaskChange
                )
                BaseNetworkCard(
                    baseIp = state.designBaseIp,
                    baseMask = state.designBaseMask,
                    onBaseIpChange = onBaseIpChange,
                    onBaseMaskChange = onBaseMaskChange
                )
                if (state.subnetDesignMode == SubnetDesignMode.FLSM) {
                    FlsmControlsCard(
                        criteria = state.flsmCriteria,
                        targetInput = state.flsmTargetInput,
                        onCriteriaSelected = onFlsmCriteriaSelected,
                        onTargetChange = onFlsmTargetChange
                    )
                    when (val outcome = state.flsmOutcome) {
                        is FlsmOutcome.Failure -> {
                            if (outcome.error != Ipv4Error.MissingInput) {
                                Ipv4ErrorBanner(error = outcome.error, locale = locale)
                            }
                        }
                        is FlsmOutcome.Success -> {
                            val res = outcome.result
                            FlsmSummaryCard(result = res, intFormat = intFormat, onCopyTable = { onCopyFlsm(res) })
                            FlsmTableHeader(result = res, intFormat = intFormat)
                            res.subnets.forEach { allocation ->
                                SubnetAllocationCard(
                                    allocation = allocation,
                                    title = stringResource(R.string.eng_subnet_number_format, allocation.index),
                                    intFormat = intFormat
                                )
                            }
                        }
                    }
                } else {
                    VlsmRequirementsCard(
                        state = state,
                        onAddRequirement = onAddReq,
                        onUpdateName = onUpdateVlsmRequirementName,
                        onUpdateHosts = onUpdateVlsmRequirementHosts,
                        onRemove = onRemoveVlsmRequirement
                    )
                    when (val outcome = state.vlsmOutcome) {
                        is VlsmOutcome.Failure -> {
                            if (outcome.error != Ipv4Error.MissingInput) {
                                Ipv4ErrorBanner(error = outcome.error, locale = locale)
                            }
                        }
                        is VlsmOutcome.Success -> {
                            val res = outcome.result
                            VlsmSummaryCard(
                                result = res,
                                intFormat = intFormat,
                                locale = locale,
                                onCopyTable = { onCopyVlsm(res) }
                            )
                            Text(
                                text = stringResource(R.string.eng_vlsm_table_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                            res.allocations.forEach { allocation ->
                                SubnetAllocationCard(
                                    allocation = allocation,
                                    title = "#${allocation.index} · ${allocation.name}",
                                    intFormat = intFormat
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetDesignModeSelector(
    selectedMode: SubnetDesignMode,
    onModeSelected: (SubnetDesignMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SubnetDesignMode.entries.forEachIndexed { index, mode ->
            val labelRes = if (mode == SubnetDesignMode.FLSM) {
                R.string.eng_design_mode_flsm
            } else {
                R.string.eng_design_mode_vlsm
            }
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, SubnetDesignMode.entries.size),
                colors = calcSegmentedButtonColors(),
                modifier = Modifier.testTag("eng_design_mode_${mode.key}")
            ) {
                Text(
                    text = stringResource(labelRes),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BaseNetworkCard(
    baseIp: String,
    baseMask: String,
    onBaseIpChange: (String) -> Unit,
    onBaseMaskChange: (String) -> Unit
) {
    CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.eng_design_base_network_title),
                style = MaterialTheme.typography.labelSmall,
                color = CalcSuitsTheme.colors.technicalAccent,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = baseIp,
                    onValueChange = onBaseIpChange,
                    label = {
                        Text(
                            text = stringResource(R.string.eng_design_base_ip),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(0.60f)
                        .testTag("eng_design_base_ip"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                OutlinedTextField(
                    value = baseMask,
                    onValueChange = onBaseMaskChange,
                    label = {
                        Text(
                            text = stringResource(R.string.eng_design_base_prefix_mask),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(0.40f)
                        .testTag("eng_design_base_mask"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
            }
        }
    }
}

private fun LazyListScope.flsmLazyItems(
    state: EngineeringState,
    intFormat: NumberFormat,
    locale: Locale,
    onCriteriaSelected: (FlsmCriteria) -> Unit,
    onTargetChange: (String) -> Unit,
    onCopyTable: (FlsmDesignResult) -> Unit
) {
    item(key = "flsm_controls") {
        FlsmControlsCard(
            criteria = state.flsmCriteria,
            targetInput = state.flsmTargetInput,
            onCriteriaSelected = onCriteriaSelected,
            onTargetChange = onTargetChange
        )
    }

    when (val outcome = state.flsmOutcome) {
        is FlsmOutcome.Failure -> {
            if (outcome.error != Ipv4Error.MissingInput) {
                item(key = "flsm_error") {
                    Ipv4ErrorBanner(error = outcome.error, locale = locale)
                }
            }
        }
        is FlsmOutcome.Success -> {
            val res = outcome.result
            item(key = "flsm_summary") {
                FlsmSummaryCard(
                    result = res,
                    intFormat = intFormat,
                    onCopyTable = { onCopyTable(res) }
                )
            }
            item(key = "flsm_table_header") {
                FlsmTableHeader(result = res, intFormat = intFormat)
            }
            items(res.subnets, key = { "flsm_${it.index}" }) { allocation ->
                SubnetAllocationCard(
                    allocation = allocation,
                    title = stringResource(R.string.eng_subnet_number_format, allocation.index),
                    intFormat = intFormat
                )
            }
        }
    }
}

@Composable
private fun FlsmControlsCard(
    criteria: FlsmCriteria,
    targetInput: String,
    onCriteriaSelected: (FlsmCriteria) -> Unit,
    onTargetChange: (String) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.eng_flsm_criteria_label),
                style = MaterialTheme.typography.labelSmall,
                color = extColors.technicalAccent,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalcFilterChip(
                    selected = criteria == FlsmCriteria.BY_SUBNET_COUNT,
                    onClick = { onCriteriaSelected(FlsmCriteria.BY_SUBNET_COUNT) },
                    label = {
                        Text(
                            text = stringResource(R.string.eng_flsm_by_subnets),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("eng_flsm_by_subnets")
                )
                CalcFilterChip(
                    selected = criteria == FlsmCriteria.BY_HOSTS_PER_SUBNET,
                    onClick = { onCriteriaSelected(FlsmCriteria.BY_HOSTS_PER_SUBNET) },
                    label = {
                        Text(
                            text = stringResource(R.string.eng_flsm_by_hosts),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("eng_flsm_by_hosts")
                )
            }

            val inputLabelRes = if (criteria == FlsmCriteria.BY_SUBNET_COUNT) {
                R.string.eng_flsm_subnets_input
            } else {
                R.string.eng_flsm_hosts_input
            }

            OutlinedTextField(
                value = targetInput,
                onValueChange = onTargetChange,
                label = { Text(stringResource(inputLabelRes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_flsm_target_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            )
        }
    }
}

@Composable
private fun FlsmSummaryCard(
    result: FlsmDesignResult,
    intFormat: NumberFormat,
    onCopyTable: () -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.eng_flsm_summary_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${result.baseNetwork.cidrNotation} → /${result.newPrefix}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailKeyValueRow(stringResource(R.string.eng_flsm_new_prefix), "/${result.newPrefix} (${result.subnetMask})")
            DetailKeyValueRow(stringResource(R.string.eng_flsm_borrowed_bits), "+${result.borrowedBits}")
            DetailKeyValueRow(stringResource(R.string.eng_flsm_generated_subnets), intFormat.format(result.totalSubnets))
            DetailKeyValueRow(stringResource(R.string.eng_flsm_capacity_per_subnet), intFormat.format(result.capacityPerSubnet))
            DetailKeyValueRow(stringResource(R.string.eng_flsm_usable_per_subnet), intFormat.format(result.usableHostsPerSubnet))

            if (result.isTruncated) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = extColors.technicalContainer,
                    border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eng_flsm_truncated_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = extColors.technicalAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.eng_flsm_truncated_note,
                                intFormat.format(result.subnets.size),
                                intFormat.format(result.totalSubnets)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.onTechnicalContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCopyTable,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_flsm_copy_table")
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.eng_copy_summary), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FlsmTableHeader(result: FlsmDesignResult, intFormat: NumberFormat) {
    val headerText = if (result.isTruncated) {
        stringResource(
            R.string.eng_flsm_table_title_truncated,
            intFormat.format(result.subnets.size),
            intFormat.format(result.totalSubnets)
        )
    } else {
        stringResource(R.string.eng_flsm_table_title)
    }
    Text(
        text = headerText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
}

private fun LazyListScope.vlsmLazyItems(
    state: EngineeringState,
    intFormat: NumberFormat,
    locale: Locale,
    onAddRequirement: () -> Unit,
    onUpdateName: (Long, String) -> Unit,
    onUpdateHosts: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onCopyTable: (VlsmDesignResult) -> Unit
) {
    item(key = "vlsm_requirements") {
        VlsmRequirementsCard(
            state = state,
            onAddRequirement = onAddRequirement,
            onUpdateName = onUpdateName,
            onUpdateHosts = onUpdateHosts,
            onRemove = onRemove
        )
    }

    when (val outcome = state.vlsmOutcome) {
        is VlsmOutcome.Failure -> {
            if (outcome.error != Ipv4Error.MissingInput) {
                item(key = "vlsm_error") {
                    Ipv4ErrorBanner(error = outcome.error, locale = locale)
                }
            }
        }
        is VlsmOutcome.Success -> {
            val res = outcome.result
            item(key = "vlsm_summary") {
                VlsmSummaryCard(
                    result = res,
                    intFormat = intFormat,
                    locale = locale,
                    onCopyTable = { onCopyTable(res) }
                )
            }
            item(key = "vlsm_table_header") {
                Text(
                    text = stringResource(R.string.eng_vlsm_table_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
            items(res.allocations, key = { "vlsm_${it.index}_${it.networkLong}" }) { allocation ->
                SubnetAllocationCard(
                    allocation = allocation,
                    title = "#${allocation.index} · ${allocation.name}",
                    intFormat = intFormat
                )
            }
        }
    }
}

@Composable
private fun VlsmRequirementsCard(
    state: EngineeringState,
    onAddRequirement: () -> Unit,
    onUpdateName: (Long, String) -> Unit,
    onUpdateHosts: (Long, String) -> Unit,
    onRemove: (Long) -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    CalcCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.eng_vlsm_reqs_title),
                style = MaterialTheme.typography.labelSmall,
                color = extColors.technicalAccent,
                fontWeight = FontWeight.Bold
            )

            state.vlsmRequirements.forEachIndexed { idx, req ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = req.name,
                        onValueChange = { onUpdateName(req.id, it) },
                        label = {
                            Text(
                                text = stringResource(R.string.eng_vlsm_req_name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(0.54f)
                            .testTag("eng_vlsm_name_$idx"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = req.hostsInput,
                        onValueChange = { onUpdateHosts(req.id, it) },
                        label = {
                            Text(
                                text = stringResource(R.string.eng_vlsm_req_hosts),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(0.34f)
                            .testTag("eng_vlsm_hosts_$idx"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    IconButton(
                        onClick = { onRemove(req.id) },
                        modifier = Modifier.testTag("eng_vlsm_remove_$idx")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_vlsm_remove_req),
                            tint = extColors.destructiveText
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onAddRequirement,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_vlsm_add_req"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.eng_vlsm_add_req), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VlsmSummaryCard(
    result: VlsmDesignResult,
    intFormat: NumberFormat,
    locale: Locale,
    onCopyTable: () -> Unit
) {
    val extColors = CalcSuitsTheme.colors
    CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.eng_vlsm_summary_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = result.baseNetwork.cidrNotation,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            val progress = (result.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            DetailKeyValueRow(
                stringResource(R.string.eng_vlsm_allocated_addresses),
                "${intFormat.format(result.allocatedAddresses)} / ${intFormat.format(result.totalBaseAddresses)}"
            )
            DetailKeyValueRow(
                stringResource(R.string.eng_vlsm_remaining_addresses),
                intFormat.format(result.remainingAddresses)
            )
            DetailKeyValueRow(
                stringResource(R.string.eng_vlsm_utilization),
                String.format(locale, "%.1f%%", result.utilizationPercent)
            )
            if (result.remainingCidrBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.eng_vlsm_remaining_blocks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.remainingCidrBlocks.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = extColors.technicalAccent
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCopyTable,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eng_vlsm_copy_table")
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.eng_copy_summary), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SubnetAllocationCard(
    allocation: SubnetAllocation,
    title: String,
    intFormat: NumberFormat
) {
    val extColors = CalcSuitsTheme.colors
    CalcCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extColors.technicalContainer,
                    border = BorderStroke(1.dp, extColors.technicalAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = allocation.cidrNotation,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = extColors.onTechnicalContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (allocation.requestedHosts != null) {
                Text(
                    text = stringResource(
                        R.string.eng_vlsm_req_vs_usable,
                        intFormat.format(allocation.requestedHosts),
                        intFormat.format(allocation.usableHosts)
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DetailKeyValueRow(stringResource(R.string.eng_detail_network_addr), allocation.networkAddress)
            DetailKeyValueRow(stringResource(R.string.eng_detail_subnet_mask), "${allocation.subnetMask} (/${allocation.prefixLength})")
            when (allocation.prefixLength) {
                31 -> {
                    DetailKeyValueRow(
                        stringResource(R.string.eng_detail_broadcast),
                        stringResource(R.string.eng_broadcast_na_31)
                    )
                    DetailKeyValueRow(stringResource(R.string.eng_detail_p2p_endpoint_1), allocation.firstUsableHost)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_p2p_endpoint_2), allocation.lastUsableHost)
                }
                32 -> {
                    DetailKeyValueRow(
                        stringResource(R.string.eng_detail_broadcast),
                        stringResource(R.string.eng_broadcast_na_32)
                    )
                    DetailKeyValueRow(stringResource(R.string.eng_detail_host_address), allocation.firstUsableHost)
                }
                else -> {
                    DetailKeyValueRow(stringResource(R.string.eng_detail_broadcast), allocation.broadcastAddress)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_first_host), allocation.firstUsableHost)
                    DetailKeyValueRow(stringResource(R.string.eng_detail_last_host), allocation.lastUsableHost)
                }
            }
            DetailKeyValueRow(
                stringResource(R.string.eng_detail_total_addresses),
                "${intFormat.format(allocation.totalCapacity)} (${stringResource(R.string.eng_subnet_usable_short)}: ${intFormat.format(allocation.usableHosts)})"
            )
        }
    }
}

@Composable
private fun QuickCidrChipStrip(
    presets: List<String>,
    currentCidr: String,
    tagPrefix: String,
    onSelect: (String) -> Unit
) {
    val trimmed = currentCidr.trim()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.eng_quick_cidr),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets, key = { it }) { cidr ->
                val numeric = cidr.removePrefix("/")
                val selected = trimmed == cidr || trimmed == numeric
                CalcFilterChip(
                    selected = selected,
                    onClick = { onSelect(cidr) },
                    label = {
                        Text(
                            text = cidr,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("$tagPrefix$numeric")
                )
            }
        }
    }
}

@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.46f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.54f)
        )
    }
}

@Composable
private fun DetailStackedRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Ipv4ErrorBanner(error: Ipv4Error, locale: Locale) {
    val intFormat = NumberFormat.getIntegerInstance(locale)
    val message = when (error) {
        Ipv4Error.MissingInput -> return
        Ipv4Error.InvalidIpAddress -> stringResource(R.string.eng_error_invalid_ip_only)
        Ipv4Error.PrefixOutOfRange -> stringResource(R.string.eng_error_prefix_out_of_range)
        Ipv4Error.NonContiguousMask -> stringResource(R.string.eng_error_non_contiguous_mask)
        Ipv4Error.InvalidSubnetMask -> stringResource(R.string.eng_error_invalid_mask)
        Ipv4Error.InvalidSubnetCount -> stringResource(R.string.eng_error_invalid_subnet_count)
        Ipv4Error.InvalidHostCount -> stringResource(R.string.eng_error_invalid_host_count)
        is Ipv4Error.InsufficientCapacity -> stringResource(
            R.string.eng_error_insufficient_capacity,
            intFormat.format(error.requiredAddresses),
            intFormat.format(error.availableAddresses)
        )
        Ipv4Error.NoVlsmRequirements -> stringResource(R.string.eng_error_no_vlsm_reqs)
        is Ipv4Error.EmptyVlsmRequirementName -> stringResource(
            R.string.eng_error_empty_vlsm_name,
            error.position
        )
        is Ipv4Error.InvalidVlsmRequirement -> stringResource(
            R.string.eng_error_invalid_vlsm_req,
            error.requirementName
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("eng_ipv4_error_banner"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

@StringRes
private fun scopeLabelRes(scope: Ipv4AddressScope): Int = when (scope) {
    Ipv4AddressScope.PRIVATE_RFC1918 -> R.string.eng_scope_private
    Ipv4AddressScope.PUBLIC_GLOBAL -> R.string.eng_scope_public
    Ipv4AddressScope.LOOPBACK -> R.string.eng_scope_loopback
    Ipv4AddressScope.LINK_LOCAL -> R.string.eng_scope_link_local
    Ipv4AddressScope.CGNAT -> R.string.eng_scope_cgnat
    Ipv4AddressScope.MULTICAST -> R.string.eng_scope_multicast
    Ipv4AddressScope.LIMITED_BROADCAST -> R.string.eng_scope_limited_broadcast
    Ipv4AddressScope.THIS_NETWORK -> R.string.eng_scope_this_network
    Ipv4AddressScope.DOCUMENTATION -> R.string.eng_scope_documentation
    Ipv4AddressScope.BENCHMARKING -> R.string.eng_scope_benchmarking
    Ipv4AddressScope.IETF_PROTOCOL -> R.string.eng_scope_ietf_protocol
    Ipv4AddressScope.RESERVED_FUTURE -> R.string.eng_scope_reserved
}

@StringRes
private fun historicalClassLabelRes(cls: HistoricalClassfulClass): Int = when (cls) {
    HistoricalClassfulClass.CLASS_A -> R.string.eng_class_a
    HistoricalClassfulClass.CLASS_B -> R.string.eng_class_b
    HistoricalClassfulClass.CLASS_C -> R.string.eng_class_c
    HistoricalClassfulClass.CLASS_D -> R.string.eng_class_d
    HistoricalClassfulClass.CLASS_E -> R.string.eng_class_e
}

private fun formatIpv4Summary(
    context: Context,
    result: Ipv4AnalysisResult,
    intFormat: NumberFormat
): String = buildString {
    appendLine(context.getString(R.string.eng_subnet_summary_header, result.cidrNotation))
    appendLine("${context.getString(R.string.eng_detail_network_addr)}: ${result.networkAddress}")
    appendLine("${context.getString(R.string.eng_detail_cidr_prefix)}: /${result.prefixLength}")
    appendLine("${context.getString(R.string.eng_detail_subnet_mask)}: ${result.subnetMask}")
    appendLine("${context.getString(R.string.eng_detail_wildcard)}: ${result.wildcardMask}")
    when (result.specialPrefixKind) {
        SpecialPrefixKind.POINT_TO_POINT_31 -> {
            appendLine("${context.getString(R.string.eng_detail_broadcast)}: ${context.getString(R.string.eng_broadcast_na_31)}")
            appendLine("${context.getString(R.string.eng_detail_p2p_endpoint_1)}: ${result.firstUsableHost}")
            appendLine("${context.getString(R.string.eng_detail_p2p_endpoint_2)}: ${result.lastUsableHost}")
        }
        SpecialPrefixKind.HOST_ROUTE_32 -> {
            appendLine("${context.getString(R.string.eng_detail_broadcast)}: ${context.getString(R.string.eng_broadcast_na_32)}")
            appendLine("${context.getString(R.string.eng_detail_host_address)}: ${result.firstUsableHost}")
        }
        else -> {
            appendLine("${context.getString(R.string.eng_detail_broadcast)}: ${result.broadcastAddress}")
            appendLine("${context.getString(R.string.eng_detail_first_host)}: ${result.firstUsableHost}")
            appendLine("${context.getString(R.string.eng_detail_last_host)}: ${result.lastUsableHost}")
        }
    }
    appendLine("${context.getString(R.string.eng_detail_total_addresses)}: ${intFormat.format(result.totalAddresses)}")
    appendLine("${context.getString(R.string.eng_detail_usable_hosts)}: ${intFormat.format(result.usableHosts)}")
    appendLine("${context.getString(R.string.eng_detail_ip_binary)}: ${result.ipBinary}")
    appendLine("${context.getString(R.string.eng_detail_mask_binary)}: ${result.maskBinary}")
    appendLine("${context.getString(R.string.eng_detail_iana_scope)}: ${context.getString(scopeLabelRes(result.scope))}")
    appendLine("${context.getString(R.string.eng_detail_historical_class)}: ${context.getString(historicalClassLabelRes(result.historicalClass))}")
}.trim()

private fun formatBroadcastForSummary(context: Context, allocation: SubnetAllocation): String = when (allocation.prefixLength) {
    31 -> context.getString(R.string.eng_broadcast_na_31)
    32 -> context.getString(R.string.eng_broadcast_na_32)
    else -> allocation.broadcastAddress
}

private fun formatFlsmSummary(
    context: Context,
    result: FlsmDesignResult,
    intFormat: NumberFormat
): String = buildString {
    val bcastLabel = context.getString(R.string.eng_summary_bcast_short)
    val usableLabel = context.getString(R.string.eng_subnet_usable_short)
    appendLine(
        context.getString(
            R.string.eng_summary_flsm_header,
            result.baseNetwork.cidrNotation,
            result.newPrefix,
            result.subnetMask
        )
    )
    appendLine("${context.getString(R.string.eng_flsm_generated_subnets)}: ${intFormat.format(result.totalSubnets)}")
    if (result.isTruncated) {
        appendLine(
            context.getString(
                R.string.eng_flsm_truncated_note,
                intFormat.format(result.subnets.size),
                intFormat.format(result.totalSubnets)
            )
        )
    }
    result.subnets.forEach { s ->
        val bcastValue = formatBroadcastForSummary(context, s)
        appendLine("#${s.index} ${s.cidrNotation} | ${s.firstUsableHost}–${s.lastUsableHost} | $bcastLabel: $bcastValue | $usableLabel: ${intFormat.format(s.usableHosts)}")
    }
}.trim()

private fun formatVlsmSummary(
    context: Context,
    result: VlsmDesignResult,
    intFormat: NumberFormat,
    locale: Locale
): String = buildString {
    val bcastLabel = context.getString(R.string.eng_summary_bcast_short)
    val usableLabel = context.getString(R.string.eng_subnet_usable_short)
    appendLine(
        context.getString(
            R.string.eng_summary_vlsm_header,
            result.baseNetwork.cidrNotation,
            intFormat.format(result.allocatedAddresses),
            intFormat.format(result.totalBaseAddresses)
        )
    )
    appendLine(
        "${context.getString(R.string.eng_vlsm_utilization)}: ${String.format(locale, "%.1f%%", result.utilizationPercent)}"
    )
    result.allocations.forEach { s ->
        val reqText = context.getString(R.string.eng_summary_req_short, intFormat.format(s.requestedHosts ?: 0L))
        val bcastValue = formatBroadcastForSummary(context, s)
        appendLine("#${s.index} ${s.name} ($reqText): ${s.cidrNotation} | ${s.firstUsableHost}–${s.lastUsableHost} | $bcastLabel: $bcastValue | $usableLabel: ${intFormat.format(s.usableHosts)}")
    }
    appendLine("${context.getString(R.string.eng_vlsm_remaining_addresses)}: ${intFormat.format(result.remainingAddresses)}")
    if (result.remainingCidrBlocks.isNotEmpty()) {
        appendLine("${context.getString(R.string.eng_vlsm_remaining_blocks)}: ${result.remainingCidrBlocks.joinToString(", ")}")
    }
}.trim()
