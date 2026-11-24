package com.calcsuits.features.converter.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.calcsuits.domain.model.converter.ConversionCategory
import com.calcsuits.domain.model.converter.ConversionUnit
import com.calcsuits.domain.usecase.ConvertUnitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class UnitConversionPreview(
    val unit: ConversionUnit,
    val formattedValue: String
)

data class ConverterState(
    val selectedCategory: ConversionCategory = ConversionCategory.Length,
    val fromUnit: ConversionUnit = ConversionCategory.Length.units[0],
    val toUnit: ConversionUnit = ConversionCategory.Length.units[1],
    val fromValue: String = "1",
    val toValue: String = "",
    val allUnitPreviews: List<UnitConversionPreview> = emptyList()
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val convertUseCase: ConvertUnitsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterState())
    val state = _state.asStateFlow()

    init {
        calculateConversion()
    }

    fun onCategorySelected(category: ConversionCategory) {
        _state.update {
            it.copy(
                selectedCategory = category,
                fromUnit = category.units[0],
                toUnit = category.units.getOrElse(1) { category.units[0] }
            )
        }
        calculateConversion()
    }

    fun onValueChange(value: String) {
        var dotSeen = false
        val cleanValue = buildString {
            value.forEachIndexed { idx, c ->
                when {
                    c.isDigit() -> append(c)
                    c == '.' && !dotSeen -> {
                        dotSeen = true
                        append(c)
                    }
                    c == '-' && idx == 0 -> append(c)
                }
            }
        }
        _state.update { it.copy(fromValue = cleanValue) }
        calculateConversion()
    }

    fun onUnitChange(from: Boolean, unit: ConversionUnit) {
        _state.update { if (from) it.copy(fromUnit = unit) else it.copy(toUnit = unit) }
        calculateConversion()
    }

    fun swapUnits() {
        _state.update { it.copy(fromUnit = it.toUnit, toUnit = it.fromUnit) }
        calculateConversion()
    }

    private fun calculateConversion() {
        val current = _state.value
        val value = current.fromValue.toDoubleOrNull()
        if (value == null) {
            _state.update { it.copy(toValue = "", allUnitPreviews = emptyList()) }
            return
        }
        val fixedDecimals = if (current.selectedCategory == ConversionCategory.Currency) 2 else null
        val result = convertUseCase(value, current.fromUnit, current.toUnit, fixedDecimals)
        val previews = current.selectedCategory.units
            .filter { it.symbol != current.fromUnit.symbol }
            .map { target ->
                UnitConversionPreview(
                    unit = target,
                    formattedValue = convertUseCase(value, current.fromUnit, target, fixedDecimals)
                )
            }
        _state.update { it.copy(toValue = result, allUnitPreviews = previews) }
    }
}
