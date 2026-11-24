package com.calcsuits.domain.usecase

import java.util.Locale
import javax.inject.Inject
import kotlin.math.pow

data class EngineeringOutput(
    val headline: String = "",
    val details: List<Pair<String, String>> = emptyList(),
    val error: String? = null
)

class CalculateEngineeringUseCase @Inject constructor() {

    fun calculateOhmsLaw(v: Double?, i: Double?, r: Double?): EngineeringOutput {
        val provided = listOf(v, i, r).count { it != null }
        if (provided < 2) return EngineeringOutput()
        if (provided == 3) return EngineeringOutput(error = "Deja vacío el valor que deseas calcular")

        return when {
            v == null && i != null && r != null -> {
                val resV = i * r
                val power = resV * i
                EngineeringOutput(
                    headline = "V = ${fmt(resV)} V",
                    details = listOf("Fórmula" to "V = I × R", "Potencia asociada" to "${fmt(power)} W")
                )
            }
            i == null && v != null && r != null -> {
                if (r == 0.0) return EngineeringOutput(error = "La resistencia no puede ser 0 Ω")
                val resI = v / r
                EngineeringOutput(
                    headline = "I = ${fmt(resI)} A",
                    details = listOf("Fórmula" to "I = V / R", "Potencia asociada" to "${fmt(v * resI)} W")
                )
            }
            r == null && v != null && i != null -> {
                if (i == 0.0) return EngineeringOutput(error = "La corriente no puede ser 0 A")
                val resR = v / i
                EngineeringOutput(
                    headline = "R = ${fmt(resR)} Ω",
                    details = listOf("Fórmula" to "R = V / I", "Potencia asociada" to "${fmt(v * i)} W")
                )
            }
            else -> EngineeringOutput(error = "Datos insuficientes")
        }
    }

    fun calculatePower(v: Double?, i: Double?, p: Double?): EngineeringOutput {
        val provided = listOf(v, i, p).count { it != null }
        if (provided < 2) return EngineeringOutput()
        if (provided == 3) return EngineeringOutput(error = "Deja vacío el valor que deseas calcular")

        return when {
            p == null && v != null && i != null -> EngineeringOutput(
                headline = "P = ${fmt(v * i)} W",
                details = listOf("Fórmula" to "P = V × I", "Resistencia equiv." to if (i != 0.0) "${fmt(v / i)} Ω" else "—")
            )
            v == null && p != null && i != null -> {
                if (i == 0.0) return EngineeringOutput(error = "La corriente no puede ser 0 A")
                EngineeringOutput(
                    headline = "V = ${fmt(p / i)} V",
                    details = listOf("Fórmula" to "V = P / I")
                )
            }
            i == null && v != null && p != null -> {
                if (v == 0.0) return EngineeringOutput(error = "El voltaje no puede ser 0 V")
                EngineeringOutput(
                    headline = "I = ${fmt(p / v)} A",
                    details = listOf("Fórmula" to "I = P / V")
                )
            }
            else -> EngineeringOutput(error = "Datos insuficientes")
        }
    }

    fun calculateResistors(r1: Double?, r2: Double?): EngineeringOutput {
        if (r1 == null || r2 == null) return EngineeringOutput()
        if (r1 < 0 || r2 < 0) return EngineeringOutput(error = "Las resistencias deben ser positivas")
        val series = r1 + r2
        val parallel = if (series == 0.0) 0.0 else (r1 * r2) / series
        return EngineeringOutput(
            headline = "Rp = ${fmt(parallel)} Ω",
            details = listOf(
                "Equivalente en Serie (Rs)" to "${fmt(series)} Ω",
                "Equivalente en Paralelo (Rp)" to "${fmt(parallel)} Ω"
            )
        )
    }

    fun calculateVoltageDivider(vin: Double?, r1: Double?, r2: Double?): EngineeringOutput {
        if (vin == null || r1 == null || r2 == null) return EngineeringOutput()
        if (r1 < 0 || r2 < 0 || (r1 + r2) == 0.0) {
            return EngineeringOutput(error = "R1 + R2 debe ser mayor que 0 Ω")
        }
        val vout = vin * (r2 / (r1 + r2))
        val current = vin / (r1 + r2)
        return EngineeringOutput(
            headline = "Vout = ${fmt(vout)} V",
            details = listOf(
                "Relación Vout / Vin" to "${fmt((r2 / (r1 + r2)) * 100)} %",
                "Corriente de rama" to "${fmt(current * 1000)} mA"
            )
        )
    }

    fun calculateSubnetting(ip: String, mask: String): EngineeringOutput {
        if (ip.isBlank() || mask.isBlank()) return EngineeringOutput()
        return try {
            val ipParts = ip.trim().split(".").map { it.toInt() }
            require(ipParts.size == 4 && ipParts.all { it in 0..255 })
            val trimmedMask = mask.trim()
            val cidr = when {
                trimmedMask.startsWith("/") -> trimmedMask.drop(1).toInt()
                trimmedMask.all { it.isDigit() } -> trimmedMask.toInt()
                else -> maskToCidr(trimmedMask)
            }
            require(cidr in 0..32)
            val ipInt = (ipParts[0] shl 24) or (ipParts[1] shl 16) or (ipParts[2] shl 8) or ipParts[3]
            val maskInt = if (cidr == 0) 0 else (-1 shl (32 - cidr))
            val network = ipInt and maskInt
            val broadcast = network or maskInt.inv()
            val addresses = 2.0.pow(32 - cidr).toLong()
            val usable = when (cidr) {
                32 -> 1L
                31 -> 2L
                else -> (addresses - 2).coerceAtLeast(0)
            }
            val wildcard = maskInt.inv()
            val firstHost = if (cidr <= 30) intToIp(network + 1) else intToIp(network)
            val lastHost = if (cidr <= 30) intToIp(broadcast - 1) else intToIp(broadcast)

            EngineeringOutput(
                headline = "${intToIp(network)}/$cidr",
                details = listOf(
                    "Dirección de Red" to intToIp(network),
                    "Máscara de Subred" to intToIp(maskInt),
                    "Wildcard" to intToIp(wildcard),
                    "Broadcast" to intToIp(broadcast),
                    "Primer Host" to firstHost,
                    "Último Host" to lastHost,
                    "Direcciones Totales" to String.format(Locale.US, "%,d", addresses),
                    "Hosts Útiles" to String.format(Locale.US, "%,d", usable)
                )
            )
        } catch (_: Exception) {
            EngineeringOutput(error = "IPv4 o máscara inválida (ej. 192.168.1.10 y /24)")
        }
    }

    private fun maskToCidr(mask: String): Int {
        val parts = mask.split(".").map { it.toInt() }
        require(parts.size == 4 && parts.all { it in 0..255 })
        val binary = parts.joinToString("") { it.toString(2).padStart(8, '0') }
        require("01" !in binary)
        return binary.count { it == '1' }
    }

    private fun intToIp(ip: Int): String =
        "${(ip ushr 24) and 255}.${(ip ushr 16) and 255}.${(ip ushr 8) and 255}.${ip and 255}"

    private fun fmt(value: Double): String = String.format(Locale.US, "%.6g", value)
}
