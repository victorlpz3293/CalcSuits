package com.calcsuits.features.engineering.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.model.engineering.EngineeringSpecialty
import com.calcsuits.domain.model.engineering.FlsmCriteria
import com.calcsuits.domain.model.engineering.FlsmOutcome
import com.calcsuits.domain.model.engineering.Ipv4AnalysisOutcome
import com.calcsuits.domain.model.engineering.Ipv4Error
import com.calcsuits.domain.model.engineering.NetworkingTool
import com.calcsuits.domain.model.engineering.SubnetDesignMode
import com.calcsuits.domain.model.engineering.VlsmOutcome
import com.calcsuits.domain.model.engineering.VlsmRequirement
import com.calcsuits.domain.usecase.CalculateEngineeringUseCase
import com.calcsuits.domain.usecase.EngineeringOutput
import com.calcsuits.domain.usecase.engineering.Ipv4SubnettingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val DEFAULT_IPV4_INPUTS = mapOf("ip" to "192.168.1.10", "mask" to "/24")

enum class Ipv4MaskInputSource {
    CIDR,
    DECIMAL_MASK
}

data class VlsmRequirementInput(
    val id: Long,
    val name: String,
    val hostsInput: String
)

private fun defaultVlsmInputs(): List<VlsmRequirementInput> = listOf(
    VlsmRequirementInput(id = 1L, name = "LAN A", hostsInput = "100"),
    VlsmRequirementInput(id = 2L, name = "LAN B", hostsInput = "50"),
    VlsmRequirementInput(id = 3L, name = "WAN", hostsInput = "2")
)

data class EngineeringState(
    val selectedSpecialty: EngineeringSpecialty? = null,
    val selectedElectricalFormula: EngineeringFormula = EngineeringFormula.OhmsLaw,
    val selectedNetworkingTool: NetworkingTool = NetworkingTool.IPV4_ANALYSIS,
    val selectedFormula: EngineeringFormula = EngineeringFormula.OhmsLaw,
    val inputs: Map<String, String> = emptyMap(),
    val output: EngineeringOutput = EngineeringOutput(),
    // IPv4 Analysis (CIDR + Decimal mask synchronized)
    val ipv4IpInput: String = "192.168.1.10",
    val ipv4CidrInput: String = "/24",
    val ipv4MaskInput: String = "255.255.255.0",
    val ipv4LastEditedMaskField: Ipv4MaskInputSource = Ipv4MaskInputSource.CIDR,
    val ipv4Analysis: Ipv4AnalysisOutcome = Ipv4AnalysisOutcome.Failure(Ipv4Error.MissingInput),
    // Subnet Design (FLSM + VLSM inside the same tool)
    val subnetDesignMode: SubnetDesignMode = SubnetDesignMode.FLSM,
    val designBaseIp: String = "192.168.1.0",
    val designBaseMask: String = "/24",
    val flsmCriteria: FlsmCriteria = FlsmCriteria.BY_SUBNET_COUNT,
    val flsmTargetInput: String = "4",
    val flsmOutcome: FlsmOutcome = FlsmOutcome.Failure(Ipv4Error.MissingInput),
    val vlsmRequirements: List<VlsmRequirementInput> = defaultVlsmInputs(),
    val vlsmOutcome: VlsmOutcome = VlsmOutcome.Failure(Ipv4Error.MissingInput)
)

@HiltViewModel
class EngineeringViewModel @Inject constructor(
    private val calculateUseCase: CalculateEngineeringUseCase,
    private val ipv4Engine: Ipv4SubnettingEngine
) : ViewModel() {

    private val _state = MutableStateFlow(EngineeringState())
    val state = _state.asStateFlow()

    private var electricalInputs: Map<String, String> = emptyMap()
    private var networkingInputs: Map<String, String> = DEFAULT_IPV4_INPUTS
    private var nextVlsmId: Long = 4L

    init {
        recomputeIpv4Analysis()
        recomputeSubnetDesign()
    }

    fun onSpecialtySelected(specialty: EngineeringSpecialty) {
        when (specialty) {
            EngineeringSpecialty.ELECTRICAL -> {
                _state.update {
                    it.copy(
                        selectedSpecialty = specialty,
                        selectedFormula = it.selectedElectricalFormula,
                        inputs = electricalInputs,
                        output = EngineeringOutput()
                    )
                }
                calculate()
            }
            EngineeringSpecialty.NETWORKING -> {
                _state.update {
                    it.copy(
                        selectedSpecialty = specialty,
                        selectedFormula = EngineeringFormula.Subnetting,
                        inputs = networkingInputs,
                        output = EngineeringOutput()
                    )
                }
                calculate()
                recomputeIpv4Analysis()
                recomputeSubnetDesign()
            }
            EngineeringSpecialty.DIGITAL_ELECTRONICS -> {
                _state.update { it.copy(selectedSpecialty = specialty) }
            }
        }
    }

    fun onBackToSpecialties() {
        _state.update { it.copy(selectedSpecialty = null) }
    }

    fun onNetworkingToolSelected(tool: NetworkingTool) {
        _state.update {
            it.copy(
                selectedNetworkingTool = tool,
                selectedFormula = EngineeringFormula.Subnetting,
                inputs = networkingInputs
            )
        }
        if (tool == NetworkingTool.IPV4_ANALYSIS) {
            calculate()
            recomputeIpv4Analysis()
        } else {
            recomputeSubnetDesign()
        }
    }

    fun onFormulaSelected(formula: EngineeringFormula) {
        if (formula == EngineeringFormula.Subnetting) {
            networkingInputs = DEFAULT_IPV4_INPUTS
            _state.update {
                it.copy(
                    selectedFormula = formula,
                    inputs = networkingInputs,
                    ipv4IpInput = "192.168.1.10",
                    ipv4CidrInput = "/24",
                    ipv4MaskInput = "255.255.255.0",
                    ipv4LastEditedMaskField = Ipv4MaskInputSource.CIDR,
                    output = EngineeringOutput()
                )
            }
            recomputeIpv4Analysis()
        } else {
            electricalInputs = emptyMap()
            _state.update {
                it.copy(
                    selectedElectricalFormula = formula,
                    selectedFormula = formula,
                    inputs = electricalInputs,
                    output = EngineeringOutput()
                )
            }
        }
        calculate()
    }

    fun onInputChange(variableKey: String, value: String) {
        if (_state.value.selectedFormula == EngineeringFormula.Subnetting) {
            when (variableKey) {
                "ip" -> onIpv4IpChange(value)
                "mask" -> {
                    if (value.contains(".")) {
                        onIpv4DecimalMaskChange(value)
                    } else {
                        onIpv4CidrChange(value)
                    }
                }
            }
            return
        }

        var dotSeen = false
        val cleanValue = buildString {
            value.forEach { c ->
                when {
                    c.isDigit() -> append(c)
                    c == '.' && !dotSeen -> {
                        dotSeen = true
                        append(c)
                    }
                }
            }
        }
        _state.update {
            val newInputs = it.inputs.toMutableMap()
            newInputs[variableKey] = cleanValue
            electricalInputs = newInputs.toMap()
            it.copy(inputs = newInputs)
        }
        calculate()
    }

    // --- IPv4 Analysis actions ---

    fun onIpv4IpChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' || it == '/' }
        if ("/" in sanitized) {
            val parts = sanitized.split("/", limit = 2)
            val ipPart = parts[0]
            val cidrPart = if (parts[1].isNotEmpty()) "/${parts[1]}" else "/"
            val resolvedPrefix = ipv4Engine.resolvePrefixOrNull(cidrPart)
            val syncedMask = resolvedPrefix?.let { ipv4Engine.prefixToMaskString(it) }
            updateNetworkingLegacyInputs(ipPart, cidrPart)
            _state.update {
                it.copy(
                    ipv4IpInput = ipPart,
                    ipv4CidrInput = cidrPart,
                    ipv4MaskInput = syncedMask ?: it.ipv4MaskInput,
                    ipv4LastEditedMaskField = Ipv4MaskInputSource.CIDR,
                    inputs = networkingInputs
                )
            }
        } else {
            val activeMask = if (_state.value.ipv4LastEditedMaskField == Ipv4MaskInputSource.CIDR) {
                _state.value.ipv4CidrInput
            } else {
                _state.value.ipv4MaskInput
            }
            updateNetworkingLegacyInputs(sanitized, activeMask)
            _state.update {
                it.copy(
                    ipv4IpInput = sanitized,
                    inputs = networkingInputs
                )
            }
        }
        recomputeIpv4Analysis()
        calculate()
    }

    fun onIpv4CidrChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '/' || it == '-' }
        val resolvedPrefix = ipv4Engine.resolvePrefixOrNull(sanitized)
        val syncedMask = resolvedPrefix?.let { ipv4Engine.prefixToMaskString(it) }
        updateNetworkingLegacyInputs(_state.value.ipv4IpInput, sanitized)
        _state.update {
            it.copy(
                ipv4CidrInput = sanitized,
                ipv4MaskInput = syncedMask ?: it.ipv4MaskInput,
                ipv4LastEditedMaskField = Ipv4MaskInputSource.CIDR,
                inputs = networkingInputs
            )
        }
        recomputeIpv4Analysis()
        calculate()
    }

    fun onIpv4DecimalMaskChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val resolvedPrefix = ipv4Engine.resolvePrefixOrNull(sanitized)
        val syncedCidr = resolvedPrefix?.let { "/$it" }
        updateNetworkingLegacyInputs(_state.value.ipv4IpInput, sanitized)
        _state.update {
            it.copy(
                ipv4MaskInput = sanitized,
                ipv4CidrInput = syncedCidr ?: it.ipv4CidrInput,
                ipv4LastEditedMaskField = Ipv4MaskInputSource.DECIMAL_MASK,
                inputs = networkingInputs
            )
        }
        recomputeIpv4Analysis()
        calculate()
    }

    // --- Subnet Design (FLSM / VLSM) actions ---

    fun onSubnetDesignModeSelected(mode: SubnetDesignMode) {
        _state.update { it.copy(subnetDesignMode = mode) }
        recomputeSubnetDesign()
    }

    fun onDesignBaseIpChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' || it == '/' }
        if ("/" in sanitized) {
            val parts = sanitized.split("/", limit = 2)
            val ipPart = parts[0]
            val maskPart = if (parts[1].isNotEmpty()) "/${parts[1]}" else "/"
            _state.update { it.copy(designBaseIp = ipPart, designBaseMask = maskPart) }
        } else {
            _state.update { it.copy(designBaseIp = sanitized) }
        }
        recomputeSubnetDesign()
    }

    fun onDesignBaseMaskChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' || it == '/' || it == '-' }
        _state.update { it.copy(designBaseMask = sanitized) }
        recomputeSubnetDesign()
    }

    fun onFlsmCriteriaSelected(criteria: FlsmCriteria) {
        val suggestedDefault = when (criteria) {
            FlsmCriteria.BY_SUBNET_COUNT -> "4"
            FlsmCriteria.BY_HOSTS_PER_SUBNET -> "30"
        }
        _state.update {
            it.copy(
                flsmCriteria = criteria,
                flsmTargetInput = suggestedDefault
            )
        }
        recomputeSubnetDesign()
    }

    fun onFlsmTargetChange(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _state.update { it.copy(flsmTargetInput = digitsOnly) }
        recomputeSubnetDesign()
    }

    fun onAddVlsmRequirement(defaultName: String) {
        val id = nextVlsmId++
        _state.update {
            it.copy(
                vlsmRequirements = it.vlsmRequirements + VlsmRequirementInput(
                    id = id,
                    name = defaultName,
                    hostsInput = "10"
                )
            )
        }
        recomputeSubnetDesign()
    }

    fun onUpdateVlsmRequirementName(id: Long, name: String) {
        _state.update {
            it.copy(
                vlsmRequirements = it.vlsmRequirements.map { req ->
                    if (req.id == id) req.copy(name = name) else req
                }
            )
        }
        recomputeSubnetDesign()
    }

    fun onUpdateVlsmRequirementHosts(id: Long, hostsInput: String) {
        val digitsOnly = hostsInput.filter { it.isDigit() }
        _state.update {
            it.copy(
                vlsmRequirements = it.vlsmRequirements.map { req ->
                    if (req.id == id) req.copy(hostsInput = digitsOnly) else req
                }
            )
        }
        recomputeSubnetDesign()
    }

    fun onRemoveVlsmRequirement(id: Long) {
        _state.update {
            it.copy(
                vlsmRequirements = it.vlsmRequirements.filterNot { req -> req.id == id }
            )
        }
        recomputeSubnetDesign()
    }

    fun clearInputs() {
        val current = _state.value
        when {
            current.selectedSpecialty == EngineeringSpecialty.NETWORKING &&
                current.selectedNetworkingTool == NetworkingTool.SUBNET_DESIGN -> {
                if (current.subnetDesignMode == SubnetDesignMode.FLSM) {
                    _state.update { it.copy(flsmTargetInput = "") }
                } else {
                    _state.update { it.copy(vlsmRequirements = emptyList()) }
                }
                recomputeSubnetDesign()
            }
            current.selectedFormula == EngineeringFormula.Subnetting -> {
                networkingInputs = emptyMap()
                _state.update {
                    it.copy(
                        inputs = emptyMap(),
                        output = EngineeringOutput(),
                        ipv4IpInput = "",
                        ipv4CidrInput = "",
                        ipv4MaskInput = "",
                        ipv4Analysis = Ipv4AnalysisOutcome.Failure(Ipv4Error.MissingInput)
                    )
                }
            }
            else -> {
                electricalInputs = emptyMap()
                _state.update { it.copy(inputs = emptyMap(), output = EngineeringOutput()) }
            }
        }
    }

    fun recalculate() {
        calculate()
        recomputeIpv4Analysis()
        recomputeSubnetDesign()
    }

    private fun updateNetworkingLegacyInputs(ip: String, mask: String) {
        networkingInputs = mapOf("ip" to ip, "mask" to mask)
    }

    private fun recomputeIpv4Analysis() {
        val current = _state.value
        val outcome = when (current.ipv4LastEditedMaskField) {
            Ipv4MaskInputSource.CIDR -> ipv4Engine.analyze(current.ipv4IpInput, current.ipv4CidrInput)
            Ipv4MaskInputSource.DECIMAL_MASK -> ipv4Engine.analyzeWithDecimalMask(current.ipv4IpInput, current.ipv4MaskInput)
        }
        _state.update { it.copy(ipv4Analysis = outcome) }
    }

    private fun recomputeSubnetDesign() {
        val current = _state.value
        val flsmOutcome = run {
            val target = current.flsmTargetInput.toLongOrNull()
            if (current.flsmTargetInput.isBlank()) {
                FlsmOutcome.Failure(Ipv4Error.MissingInput)
            } else if (target == null || target <= 0L) {
                val err = if (current.flsmCriteria == FlsmCriteria.BY_SUBNET_COUNT) {
                    Ipv4Error.InvalidSubnetCount
                } else {
                    Ipv4Error.InvalidHostCount
                }
                FlsmOutcome.Failure(err)
            } else {
                ipv4Engine.designFlsm(
                    baseIp = current.designBaseIp,
                    basePrefixOrMask = current.designBaseMask,
                    criteria = current.flsmCriteria,
                    targetValue = target
                )
            }
        }

        val vlsmOutcome = run {
            if (current.vlsmRequirements.isEmpty()) {
                VlsmOutcome.Failure(Ipv4Error.NoVlsmRequirements)
            } else {
                val invalid = current.vlsmRequirements.firstOrNull {
                    (it.hostsInput.toLongOrNull() ?: 0L) <= 0L
                }
                if (invalid != null) {
                    VlsmOutcome.Failure(Ipv4Error.InvalidVlsmRequirement(invalid.name.ifBlank { "#${invalid.id}" }))
                } else {
                    val domainReqs = current.vlsmRequirements.map {
                        VlsmRequirement(
                            id = it.id,
                            name = it.name,
                            requiredHosts = it.hostsInput.toLong()
                        )
                    }
                    ipv4Engine.designVlsm(
                        baseIp = current.designBaseIp,
                        basePrefixOrMask = current.designBaseMask,
                        requirements = domainReqs
                    )
                }
            }
        }

        _state.update {
            it.copy(
                flsmOutcome = flsmOutcome,
                vlsmOutcome = vlsmOutcome
            )
        }
    }

    private fun calculate() {
        val currentState = _state.value
        val inputs = currentState.inputs

        val res = when (currentState.selectedFormula) {
            is EngineeringFormula.OhmsLaw -> calculateUseCase.calculateOhmsLaw(
                v = inputs["v"]?.toDoubleOrNull(),
                i = inputs["i"]?.toDoubleOrNull(),
                r = inputs["r"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Power -> calculateUseCase.calculatePower(
                v = inputs["v"]?.toDoubleOrNull(),
                i = inputs["i"]?.toDoubleOrNull(),
                p = inputs["p"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Resistors -> calculateUseCase.calculateResistors(
                r1 = inputs["r1"]?.toDoubleOrNull(),
                r2 = inputs["r2"]?.toDoubleOrNull()
            )
            is EngineeringFormula.VoltageDivider -> calculateUseCase.calculateVoltageDivider(
                vin = inputs["vin"]?.toDoubleOrNull(),
                r1 = inputs["r1"]?.toDoubleOrNull(),
                r2 = inputs["r2"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Subnetting -> calculateUseCase.calculateSubnetting(
                ip = inputs["ip"] ?: "",
                mask = inputs["mask"] ?: ""
            )
        }

        _state.update { it.copy(output = res) }
    }
}
