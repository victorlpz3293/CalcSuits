package com.calcsuits.domain.usecase

import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.pow

data class EngineeringDetail(
    val label: UiText,
    val value: UiText
)

data class EngineeringOutput(
    val headline: UiText? = null,
    val details: List<EngineeringDetail> = emptyList(),
    val error: UiText? = null
)

class CalculateEngineeringUseCase @Inject constructor() {

    fun calculateOhmsLaw(v: Double?, i: Double?, r: Double?): EngineeringOutput {
        val provided = listOf(v, i, r).count { it != null }
        if (provided < 2) return EngineeringOutput()
        if (provided == 3) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_leave_one_empty))

        return when {
            v == null && i != null && r != null -> {
                val resV = i * r
                val power = resV * i
                EngineeringOutput(
                    headline = UiText.DynamicString("V = ${fmt(resV)} V"),
                    details = listOf(
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("V = I × R")),
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_power), UiText.DynamicString("${fmt(power)} W"))
                    )
                )
            }
            i == null && v != null && r != null -> {
                if (r == 0.0) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_resistance_zero))
                val resI = v / r
                EngineeringOutput(
                    headline = UiText.DynamicString("I = ${fmt(resI)} A"),
                    details = listOf(
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("I = V / R")),
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_power), UiText.DynamicString("${fmt(v * resI)} W"))
                    )
                )
            }
            r == null && v != null && i != null -> {
                if (i == 0.0) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_current_zero))
                val resR = v / i
                EngineeringOutput(
                    headline = UiText.DynamicString("R = ${fmt(resR)} Ω"),
                    details = listOf(
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("R = V / I")),
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_power), UiText.DynamicString("${fmt(v * i)} W"))
                    )
                )
            }
            else -> EngineeringOutput(error = UiText.StringResource(R.string.eng_error_insufficient_data))
        }
    }

    fun calculatePower(v: Double?, i: Double?, p: Double?): EngineeringOutput {
        val provided = listOf(v, i, p).count { it != null }
        if (provided < 2) return EngineeringOutput()
        if (provided == 3) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_leave_one_empty))

        return when {
            p == null && v != null && i != null -> EngineeringOutput(
                headline = UiText.DynamicString("P = ${fmt(v * i)} W"),
                details = listOf(
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("P = V × I")),
                    EngineeringDetail(
                        UiText.StringResource(R.string.eng_detail_equiv_resistance),
                        UiText.DynamicString(if (i != 0.0) "${fmt(v / i)} Ω" else "—")
                    )
                )
            )
            v == null && p != null && i != null -> {
                if (i == 0.0) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_current_zero))
                EngineeringOutput(
                    headline = UiText.DynamicString("V = ${fmt(p / i)} V"),
                    details = listOf(
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("V = P / I"))
                    )
                )
            }
            i == null && v != null && p != null -> {
                if (v == 0.0) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_voltage_zero))
                EngineeringOutput(
                    headline = UiText.DynamicString("I = ${fmt(p / v)} A"),
                    details = listOf(
                        EngineeringDetail(UiText.StringResource(R.string.eng_detail_formula), UiText.DynamicString("I = P / V"))
                    )
                )
            }
            else -> EngineeringOutput(error = UiText.StringResource(R.string.eng_error_insufficient_data))
        }
    }

    fun calculateResistors(r1: Double?, r2: Double?): EngineeringOutput {
        if (r1 == null || r2 == null) return EngineeringOutput()
        if (r1 < 0 || r2 < 0) return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_resistors_positive))
        val series = r1 + r2
        val parallel = if (series == 0.0) 0.0 else (r1 * r2) / series
        return EngineeringOutput(
            headline = UiText.DynamicString("Rp = ${fmt(parallel)} Ω"),
            details = listOf(
                EngineeringDetail(UiText.StringResource(R.string.eng_detail_series_equiv), UiText.DynamicString("${fmt(series)} Ω")),
                EngineeringDetail(UiText.StringResource(R.string.eng_detail_parallel_equiv), UiText.DynamicString("${fmt(parallel)} Ω"))
            )
        )
    }

    fun calculateVoltageDivider(vin: Double?, r1: Double?, r2: Double?): EngineeringOutput {
        if (vin == null || r1 == null || r2 == null) return EngineeringOutput()
        if (r1 < 0 || r2 < 0 || (r1 + r2) == 0.0) {
            return EngineeringOutput(error = UiText.StringResource(R.string.eng_error_resistor_sum_zero))
        }
        val vout = vin * (r2 / (r1 + r2))
        val current = vin / (r1 + r2)
        return EngineeringOutput(
            headline = UiText.DynamicString("Vout = ${fmt(vout)} V"),
            details = listOf(
                EngineeringDetail(UiText.StringResource(R.string.eng_detail_vout_vin_ratio), UiText.DynamicString("${fmt((r2 / (r1 + r2)) * 100)} %")),
                EngineeringDetail(UiText.StringResource(R.string.eng_detail_branch_current), UiText.DynamicString("${fmt(current * 1000)} mA"))
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
            val intFormatter = NumberFormat.getIntegerInstance(Locale.getDefault())

            EngineeringOutput(
                headline = UiText.DynamicString("${intToIp(network)}/$cidr"),
                details = listOf(
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_network_addr), UiText.DynamicString(intToIp(network))),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_subnet_mask), UiText.DynamicString(intToIp(maskInt))),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_wildcard), UiText.DynamicString(intToIp(wildcard))),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_broadcast), UiText.DynamicString(intToIp(broadcast))),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_first_host), UiText.DynamicString(firstHost)),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_last_host), UiText.DynamicString(lastHost)),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_total_addresses), UiText.DynamicString(intFormatter.format(addresses))),
                    EngineeringDetail(UiText.StringResource(R.string.eng_detail_usable_hosts), UiText.DynamicString(intFormatter.format(usable)))
                )
            )
        } catch (_: Exception) {
            EngineeringOutput(error = UiText.StringResource(R.string.eng_error_invalid_ipv4))
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

    private fun fmt(value: Double): String = String.format(Locale.getDefault(), "%.6g", value)
}
