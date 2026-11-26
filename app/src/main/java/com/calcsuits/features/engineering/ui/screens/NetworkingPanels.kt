package com.calcsuits.features.engineering.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
            DetailKeyValueRow(stringResource(R.string.eng_detail_broadcast), result.broadcastAddress)
            DetailKeyValueRow(stringResource(R.string.eng_detail_first_host), result.firstUsableHost)
            DetailKeyValueRow(stringResource(R.string.eng_detail_last_host), result.lastUsableHost)
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
            DetailKeyValueRow(
                label = stringResource(R.string.eng_detail_iana_scope),
                value = stringResource(scopeLabelRes(result.scope))
            )
            DetailKeyValueRow(
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

    // Mode selector: FLSM | VLSM inside the same Subnet Design tool
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
                selected = state.subnetDesignMode == mode,
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

    QuickCidrChipStrip(
        presets = DESIGN_CIDR_PRESETS,
        currentCidr = state.designBaseMask,
        tagPrefix = "eng_design_cidr_",
        onSelect = onBaseMaskChange
    )

    // Base network card shared by FLSM and VLSM
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
                    value = state.designBaseIp,
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
                    value = state.designBaseMask,
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

    if (state.subnetDesignMode == SubnetDesignMode.FLSM) {
        FlsmSection(
            state = state,
            intFormat = intFormat,
            locale = locale,
            onCriteriaSelected = onFlsmCriteriaSelected,
            onTargetChange = onFlsmTargetChange,
            onCopyTable = { result ->
                clipboard.setText(AnnotatedString(formatFlsmSummary(context, result, intFormat)))
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.eng_flsm_copied))
                }
            }
        )
    } else {
        VlsmSection(
            state = state,
            intFormat = intFormat,
            locale = locale,
            onAddRequirement = {
                val nextIndex = state.vlsmRequirements.size + 1
                onAddVlsmRequirement(context.getString(R.string.eng_vlsm_default_prefix_name, nextIndex))
            },
            onUpdateName = onUpdateVlsmRequirementName,
            onUpdateHosts = onUpdateVlsmRequirementHosts,
            onRemove = onRemoveVlsmRequirement,
            onCopyTable = { result ->
                clipboard.setText(AnnotatedString(formatVlsmSummary(context, result, intFormat)))
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.eng_vlsm_copied))
                }
            }
        )
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FlsmSection(
    state: EngineeringState,
    intFormat: NumberFormat,
    locale: Locale,
    onCriteriaSelected: (FlsmCriteria) -> Unit,
    onTargetChange: (String) -> Unit,
    onCopyTable: (FlsmDesignResult) -> Unit
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
                    selected = state.flsmCriteria == FlsmCriteria.BY_SUBNET_COUNT,
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
                    selected = state.flsmCriteria == FlsmCriteria.BY_HOSTS_PER_SUBNET,
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

            val inputLabelRes = if (state.flsmCriteria == FlsmCriteria.BY_SUBNET_COUNT) {
                R.string.eng_flsm_subnets_input
            } else {
                R.string.eng_flsm_hosts_input
            }

            OutlinedTextField(
                value = state.flsmTargetInput,
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

    when (val outcome = state.flsmOutcome) {
        is FlsmOutcome.Failure -> {
            if (outcome.error != Ipv4Error.MissingInput) {
                Ipv4ErrorBanner(error = outcome.error, locale = locale)
            }
        }
        is FlsmOutcome.Success -> {
            val res = outcome.result
            CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.eng_flsm_summary_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${res.baseNetwork.cidrNotation} → /${res.newPrefix}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailKeyValueRow(stringResource(R.string.eng_flsm_new_prefix), "/${res.newPrefix} (${res.subnetMask})")
                    DetailKeyValueRow(stringResource(R.string.eng_flsm_borrowed_bits), "+${res.borrowedBits}")
                    DetailKeyValueRow(stringResource(R.string.eng_flsm_generated_subnets), intFormat.format(res.totalSubnets))
                    DetailKeyValueRow(stringResource(R.string.eng_flsm_capacity_per_subnet), intFormat.format(res.capacityPerSubnet))
                    DetailKeyValueRow(stringResource(R.string.eng_flsm_usable_per_subnet), intFormat.format(res.usableHostsPerSubnet))

                    if (res.isTruncated) {
                        Text(
                            text = stringResource(
                                R.string.eng_flsm_truncated_note,
                                intFormat.format(res.subnets.size),
                                intFormat.format(res.totalSubnets)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.technicalAccent
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onCopyTable(res) },
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

            Text(
                text = stringResource(R.string.eng_flsm_table_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            res.subnets.forEach { allocation ->
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
private fun VlsmSection(
    state: EngineeringState,
    intFormat: NumberFormat,
    locale: Locale,
    onAddRequirement: () -> Unit,
    onUpdateName: (Long, String) -> Unit,
    onUpdateHosts: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onCopyTable: (VlsmDesignResult) -> Unit
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
                        label = { Text(stringResource(R.string.eng_vlsm_req_name)) },
                        modifier = Modifier
                            .weight(0.54f)
                            .testTag("eng_vlsm_name_$idx"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = req.hostsInput,
                        onValueChange = { onUpdateHosts(req.id, it) },
                        label = { Text(stringResource(R.string.eng_vlsm_req_hosts)) },
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

    when (val outcome = state.vlsmOutcome) {
        is VlsmOutcome.Failure -> {
            if (outcome.error != Ipv4Error.MissingInput) {
                Ipv4ErrorBanner(error = outcome.error, locale = locale)
            }
        }
        is VlsmOutcome.Success -> {
            val res = outcome.result
            CalcResultCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.eng_vlsm_summary_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = res.baseNetwork.cidrNotation,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val progress = (res.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f)
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
                        "${intFormat.format(res.allocatedAddresses)} / ${intFormat.format(res.totalBaseAddresses)}"
                    )
                    DetailKeyValueRow(
                        stringResource(R.string.eng_vlsm_remaining_addresses),
                        intFormat.format(res.remainingAddresses)
                    )
                    DetailKeyValueRow(
                        stringResource(R.string.eng_vlsm_utilization),
                        String.format(locale, "%.1f%%", res.utilizationPercent)
                    )
                    if (res.remainingCidrBlocks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.eng_vlsm_remaining_blocks),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = res.remainingCidrBlocks.joinToString(" · "),
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
                        onClick = { onCopyTable(res) },
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
            DetailKeyValueRow(stringResource(R.string.eng_detail_broadcast), allocation.broadcastAddress)
            DetailKeyValueRow(stringResource(R.string.eng_detail_first_host), allocation.firstUsableHost)
            DetailKeyValueRow(stringResource(R.string.eng_detail_last_host), allocation.lastUsableHost)
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
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
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
    appendLine("${context.getString(R.string.eng_detail_broadcast)}: ${result.broadcastAddress}")
    appendLine("${context.getString(R.string.eng_detail_first_host)}: ${result.firstUsableHost}")
    appendLine("${context.getString(R.string.eng_detail_last_host)}: ${result.lastUsableHost}")
    appendLine("${context.getString(R.string.eng_detail_total_addresses)}: ${intFormat.format(result.totalAddresses)}")
    appendLine("${context.getString(R.string.eng_detail_usable_hosts)}: ${intFormat.format(result.usableHosts)}")
    appendLine("${context.getString(R.string.eng_detail_ip_binary)}: ${result.ipBinary}")
    appendLine("${context.getString(R.string.eng_detail_mask_binary)}: ${result.maskBinary}")
    appendLine("${context.getString(R.string.eng_detail_iana_scope)}: ${context.getString(scopeLabelRes(result.scope))}")
    appendLine("${context.getString(R.string.eng_detail_historical_class)}: ${context.getString(historicalClassLabelRes(result.historicalClass))}")
}.trim()

private fun formatFlsmSummary(
    context: Context,
    result: FlsmDesignResult,
    intFormat: NumberFormat
): String = buildString {
    appendLine("FLSM — ${result.baseNetwork.cidrNotation} → /${result.newPrefix} (${result.subnetMask})")
    appendLine("${context.getString(R.string.eng_flsm_generated_subnets)}: ${intFormat.format(result.totalSubnets)}")
    result.subnets.forEach { s ->
        appendLine("#${s.index} ${s.cidrNotation} | ${s.firstUsableHost}–${s.lastUsableHost} | Bcast: ${s.broadcastAddress} | Usable: ${intFormat.format(s.usableHosts)}")
    }
}.trim()

private fun formatVlsmSummary(
    context: Context,
    result: VlsmDesignResult,
    intFormat: NumberFormat
): String = buildString {
    appendLine("VLSM — ${result.baseNetwork.cidrNotation} (${intFormat.format(result.allocatedAddresses)}/${intFormat.format(result.totalBaseAddresses)})")
    result.allocations.forEach { s ->
        appendLine("#${s.index} ${s.name} (req ${s.requestedHosts}): ${s.cidrNotation} | ${s.firstUsableHost}–${s.lastUsableHost} | Bcast: ${s.broadcastAddress} | Usable: ${intFormat.format(s.usableHosts)}")
    }
    appendLine("${context.getString(R.string.eng_vlsm_remaining_addresses)}: ${intFormat.format(result.remainingAddresses)}")
}.trim()
