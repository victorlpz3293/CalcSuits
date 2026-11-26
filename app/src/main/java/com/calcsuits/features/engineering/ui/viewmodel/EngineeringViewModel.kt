package com.calcsuits.features.engineering.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.model.engineering.EngineeringSpecialty
import com.calcsuits.domain.model.engineering.NetworkingTool
import com.calcsuits.domain.usecase.CalculateEngineeringUseCase
import com.calcsuits.domain.usecase.EngineeringOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val DEFAULT_IPV4_INPUTS = mapOf("ip" to "192.168.1.10", "mask" to "/24")

data class EngineeringState(
    val selectedSpecialty: EngineeringSpecialty? = null,
    val selectedElectricalFormula: EngineeringFormula = EngineeringFormula.OhmsLaw,
    val selectedNetworkingTool: NetworkingTool = NetworkingTool.IPV4_ANALYSIS,
    val selectedFormula: EngineeringFormula = EngineeringFormula.OhmsLaw,
    val inputs: Map<String, String> = emptyMap(),
    val output: EngineeringOutput = EngineeringOutput()
)

@HiltViewModel
class EngineeringViewModel @Inject constructor(
    private val calculateUseCase: CalculateEngineeringUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(EngineeringState())
    val state = _state.asStateFlow()

    private var electricalInputs: Map<String, String> = emptyMap()
    private var networkingInputs: Map<String, String> = DEFAULT_IPV4_INPUTS

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
        }
    }

    fun onFormulaSelected(formula: EngineeringFormula) {
        if (formula == EngineeringFormula.Subnetting) {
            networkingInputs = DEFAULT_IPV4_INPUTS
            _state.update {
                it.copy(
                    selectedFormula = formula,
                    inputs = networkingInputs,
                    output = EngineeringOutput()
                )
            }
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
        val allowsCidr = _state.value.selectedFormula.allowsCidrText
        val cleanValue = if (allowsCidr) {
            value.filter { it.isDigit() || it == '.' || it == '/' }
        } else {
            var dotSeen = false
            buildString {
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
        }
        _state.update {
            val newInputs = it.inputs.toMutableMap()
            newInputs[variableKey] = cleanValue
            if (it.selectedFormula == EngineeringFormula.Subnetting) {
                networkingInputs = newInputs.toMap()
            } else {
                electricalInputs = newInputs.toMap()
            }
            it.copy(inputs = newInputs)
        }
        calculate()
    }

    fun clearInputs() {
        _state.update {
            if (it.selectedFormula == EngineeringFormula.Subnetting) {
                networkingInputs = emptyMap()
            } else {
                electricalInputs = emptyMap()
            }
            it.copy(inputs = emptyMap(), output = EngineeringOutput())
        }
    }

    fun recalculate() {
        calculate()
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
