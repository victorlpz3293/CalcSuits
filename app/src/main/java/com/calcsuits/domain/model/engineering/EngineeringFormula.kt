package com.calcsuits.domain.model.engineering

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class EngineeringFormula(
    val title: String,
    val equation: String,
    val hint: String,
    val icon: ImageVector,
    val variables: List<String>,
    val allowsCidrText: Boolean = false
) {
    object OhmsLaw : EngineeringFormula(
        title = "Ley de Ohm",
        equation = "V = I × R",
        hint = "Ingresa exactamente 2 valores para despejar el tercero",
        icon = Icons.Default.ElectricBolt,
        variables = listOf("Voltaje (V)", "Corriente (A)", "Resistencia (Ω)")
    )

    object Power : EngineeringFormula(
        title = "Potencia Eléctrica",
        equation = "P = V × I",
        hint = "Ingresa exactamente 2 valores para despejar el tercero",
        icon = Icons.Default.Bolt,
        variables = listOf("Voltaje (V)", "Corriente (A)", "Potencia (W)")
    )

    object Resistors : EngineeringFormula(
        title = "Resistencias",
        equation = "Rs = R1 + R2  |  Rp = (R1·R2)/(R1+R2)",
        hint = "Calcula equivalente en serie y paralelo",
        icon = Icons.Default.LinearScale,
        variables = listOf("R1 (Ω)", "R2 (Ω)")
    )

    object VoltageDivider : EngineeringFormula(
        title = "Divisor de Voltaje",
        equation = "Vout = Vin × R2 / (R1 + R2)",
        hint = "Calcula el voltaje de salida en R2",
        icon = Icons.Default.Tune,
        variables = listOf("Vin (V)", "R1 (Ω)", "R2 (Ω)")
    )

    object Subnetting : EngineeringFormula(
        title = "Subnetting IPv4",
        equation = "Red, Broadcast, Wildcard y Hosts útiles",
        hint = "Ej. IP 192.168.1.10 y máscara /24 o 255.255.255.0",
        icon = Icons.Default.Lan,
        variables = listOf("Dirección IPv4", "Máscara o CIDR"),
        allowsCidrText = true
    )

    companion object {
        val all: List<EngineeringFormula> by lazy {
            listOf(OhmsLaw, Power, Resistors, VoltageDivider, Subnetting)
        }
    }
}
