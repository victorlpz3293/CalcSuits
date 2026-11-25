package com.calcsuits.domain.repository

data class CurrencyRatesSnapshot(
    val baseCode: String,
    val rates: Map<String, Double>,
    val updatedAtMillis: Long
)

enum class CurrencyRatesSource {
    REMOTE,
    CACHE
}

data class CurrencyRatesResult(
    val snapshot: CurrencyRatesSnapshot,
    val source: CurrencyRatesSource,
    val isStale: Boolean,
    val error: Throwable? = null
)

interface CurrencyRepository {
    suspend fun getCachedRates(base: String = DEFAULT_BASE): CurrencyRatesSnapshot?

    suspend fun refreshExchangeRates(
        base: String = DEFAULT_BASE,
        forceRefresh: Boolean = false
    ): Result<CurrencyRatesResult>

    companion object {
        const val DEFAULT_BASE = "USD"
        const val CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L
    }
}
