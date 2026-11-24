package com.calcsuits.features.engineering.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.calcsuits.domain.model.engineering.EngineeringFormula
import com.calcsuits.domain.usecase.CalculateEngineeringUseCase
import com.calcsuits.domain.usecase.EngineeringOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EngineeringState(
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

    fun onFormulaSelected(formula: EngineeringFormula) {
        val defaultInputs = if (formula == EngineeringFormula.Subnetting) {
            mapOf("Dirección IPv4" to "192.168.1.10", "Máscara o CIDR" to "/24")
        } else {
            emptyMap()
        }
        _state.update { it.copy(selectedFormula = formula, inputs = defaultInputs, output = EngineeringOutput()) }
        calculate()
    }

    fun onInputChange(variable: String, value: String) {
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
            newInputs[variable] = cleanValue
            it.copy(inputs = newInputs)
        }
        calculate()
    }

    fun clearInputs() {
        _state.update { it.copy(inputs = emptyMap(), output = EngineeringOutput()) }
    }

    private fun calculate() {
        val currentState = _state.value
        val inputs = currentState.inputs

        val res = when (currentState.selectedFormula) {
            is EngineeringFormula.OhmsLaw -> calculateUseCase.calculateOhmsLaw(
                v = inputs["Voltaje (V)"]?.toDoubleOrNull(),
                i = inputs["Corriente (A)"]?.toDoubleOrNull(),
                r = inputs["Resistencia (Ω)"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Power -> calculateUseCase.calculatePower(
                v = inputs["Voltaje (V)"]?.toDoubleOrNull(),
                i = inputs["Corriente (A)"]?.toDoubleOrNull(),
                p = inputs["Potencia (W)"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Resistors -> calculateUseCase.calculateResistors(
                r1 = inputs["R1 (Ω)"]?.toDoubleOrNull(),
                r2 = inputs["R2 (Ω)"]?.toDoubleOrNull()
            )
            is EngineeringFormula.VoltageDivider -> calculateUseCase.calculateVoltageDivider(
                vin = inputs["Vin (V)"]?.toDoubleOrNull(),
                r1 = inputs["R1 (Ω)"]?.toDoubleOrNull(),
                r2 = inputs["R2 (Ω)"]?.toDoubleOrNull()
            )
            is EngineeringFormula.Subnetting -> calculateUseCase.calculateSubnetting(
                ip = inputs["Dirección IPv4"] ?: "",
                mask = inputs["Máscara o CIDR"] ?: ""
            )
        }

        _state.update { it.copy(output = res) }
    }
}
