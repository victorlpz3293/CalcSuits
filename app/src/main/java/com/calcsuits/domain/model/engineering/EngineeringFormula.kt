package com.calcsuits.domain.model.engineering

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.calcsuits.R
import com.calcsuits.domain.model.UiText

data class EngineeringVariable(
    val key: String,
    @StringRes val labelRes: Int
)

sealed class EngineeringFormula(
    val key: String,
    @StringRes val titleRes: Int,
    val equation: UiText,
    @StringRes val hintRes: Int,
    val icon: ImageVector,
    val variables: List<EngineeringVariable>,
    val allowsCidrText: Boolean = false
) {
    object OhmsLaw : EngineeringFormula(
        key = "ohms_law",
        titleRes = R.string.eng_ohms_law_title,
        equation = UiText.DynamicString("V = I × R"),
        hintRes = R.string.eng_two_values_hint,
        icon = Icons.Default.ElectricBolt,
        variables = listOf(
            EngineeringVariable("v", R.string.eng_var_voltage),
            EngineeringVariable("i", R.string.eng_var_current),
            EngineeringVariable("r", R.string.eng_var_resistance)
        )
    )

    object Power : EngineeringFormula(
        key = "power",
        titleRes = R.string.eng_power_title,
        equation = UiText.DynamicString("P = V × I"),
        hintRes = R.string.eng_two_values_hint,
        icon = Icons.Default.Bolt,
        variables = listOf(
            EngineeringVariable("v", R.string.eng_var_voltage),
            EngineeringVariable("i", R.string.eng_var_current),
            EngineeringVariable("p", R.string.eng_var_power)
        )
    )

    object Resistors : EngineeringFormula(
        key = "resistors",
        titleRes = R.string.eng_resistors_title,
        equation = UiText.DynamicString("Rs = R1 + R2  |  Rp = (R1·R2)/(R1+R2)"),
        hintRes = R.string.eng_resistors_hint,
        icon = Icons.Default.LinearScale,
        variables = listOf(
            EngineeringVariable("r1", R.string.eng_var_r1),
            EngineeringVariable("r2", R.string.eng_var_r2)
        )
    )

    object VoltageDivider : EngineeringFormula(
        key = "voltage_divider",
        titleRes = R.string.eng_voltage_divider_title,
        equation = UiText.DynamicString("Vout = Vin × R2 / (R1 + R2)"),
        hintRes = R.string.eng_voltage_divider_hint,
        icon = Icons.Default.Tune,
        variables = listOf(
            EngineeringVariable("vin", R.string.eng_var_vin),
            EngineeringVariable("r1", R.string.eng_var_r1),
            EngineeringVariable("r2", R.string.eng_var_r2)
        )
    )

    object Subnetting : EngineeringFormula(
        key = "subnetting",
        titleRes = R.string.eng_subnetting_title,
        equation = UiText.StringResource(R.string.eng_subnetting_equation),
        hintRes = R.string.eng_subnetting_hint,
        icon = Icons.Default.Lan,
        variables = listOf(
            EngineeringVariable("ip", R.string.eng_var_ipv4),
            EngineeringVariable("mask", R.string.eng_var_mask_cidr)
        ),
        allowsCidrText = true
    )

    companion object {
        val all: List<EngineeringFormula> by lazy {
            listOf(OhmsLaw, Power, Resistors, VoltageDivider, Subnetting)
        }
    }
}
