package com.calcsuits.domain.model.finance

/**
 * Currency definition independent of device/app [java.util.Locale].
 *
 * Adding a new currency only requires registering an entry in [FinanceCurrencies.supported]
 * or constructing a [FinanceCurrency] instance directly.
 */
data class FinanceCurrency(
    val code: String,
    val symbol: String,
    val fractionDigits: Int = 2
) {
    init {
        require(code.isNotBlank()) { "Currency code must not be blank" }
        require(symbol.isNotBlank()) { "Currency symbol must not be blank" }
        require(fractionDigits in 0..4) { "Fraction digits must be between 0 and 4" }
    }
}

object FinanceCurrencies {
    val USD = FinanceCurrency(code = "USD", symbol = "$", fractionDigits = 2)
    val NIO = FinanceCurrency(code = "NIO", symbol = "C$", fractionDigits = 2)
    val EUR = FinanceCurrency(code = "EUR", symbol = "€", fractionDigits = 2)
    val MXN = FinanceCurrency(code = "MXN", symbol = "MX$", fractionDigits = 2)
    val CRC = FinanceCurrency(code = "CRC", symbol = "₡", fractionDigits = 2)
    val GBP = FinanceCurrency(code = "GBP", symbol = "£", fractionDigits = 2)

    val supported: List<FinanceCurrency> = listOf(NIO, USD, EUR, MXN, CRC, GBP)

    fun byCode(code: String): FinanceCurrency? =
        supported.firstOrNull { it.code.equals(code, ignoreCase = true) }
}
