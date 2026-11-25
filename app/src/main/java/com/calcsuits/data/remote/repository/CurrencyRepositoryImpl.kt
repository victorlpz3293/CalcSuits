package com.calcsuits.data.remote.repository

import com.calcsuits.data.remote.api.ExchangeRateApi
import com.calcsuits.data.remote.cache.CurrencyRatesCache
import com.calcsuits.domain.repository.CurrencyRatesResult
import com.calcsuits.domain.repository.CurrencyRatesSnapshot
import com.calcsuits.domain.repository.CurrencyRatesSource
import com.calcsuits.domain.repository.CurrencyRepository
import kotlinx.coroutines.CancellationException
import java.util.Locale
import javax.inject.Inject

class CurrencyRepositoryImpl(
    private val api: ExchangeRateApi,
    private val cache: CurrencyRatesCache,
    private val cacheTtlMillis: Long = CurrencyRepository.CACHE_TTL_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : CurrencyRepository {

    @Inject
    constructor(
        api: ExchangeRateApi,
        cache: CurrencyRatesCache
    ) : this(
        api = api,
        cache = cache,
        cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
        clock = { System.currentTimeMillis() }
    )

    override suspend fun getCachedRates(base: String): CurrencyRatesSnapshot? {
        val normalizedBase = base.uppercase(Locale.ROOT)
        return cache.read(normalizedBase)
    }

    override suspend fun refreshExchangeRates(
        base: String,
        forceRefresh: Boolean
    ): Result<CurrencyRatesResult> {
        val normalizedBase = base.uppercase(Locale.ROOT)
        val cached = cache.read(normalizedBase)
        val now = clock()

        if (cached != null && !forceRefresh && isFresh(cached, now)) {
            return Result.success(
                CurrencyRatesResult(
                    snapshot = cached,
                    source = CurrencyRatesSource.CACHE,
                    isStale = false
                )
            )
        }

        return try {
            val response = api.getLatestRates(normalizedBase)
            if (!response.result.equals("success", ignoreCase = true)) {
                throw IllegalStateException("Exchange rate API returned result=${response.result}")
            }

            val validRates = buildMap {
                response.rates.forEach { (code, rate) ->
                    if (rate.isFinite() && rate > 0.0) {
                        put(code.uppercase(Locale.ROOT), rate)
                    }
                }
                if (!containsKey(normalizedBase)) {
                    put(normalizedBase, 1.0)
                }
            }

            if (validRates.size <= 1 && response.rates.isEmpty()) {
                throw IllegalStateException("Exchange rate API returned empty rates")
            }

            val snapshot = CurrencyRatesSnapshot(
                baseCode = response.baseCode.ifBlank { normalizedBase }.uppercase(Locale.ROOT),
                rates = validRates,
                updatedAtMillis = clock()
            )
            cache.write(snapshot)
            Result.success(
                CurrencyRatesResult(
                    snapshot = snapshot,
                    source = CurrencyRatesSource.REMOTE,
                    isStale = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) {
                Result.success(
                    CurrencyRatesResult(
                        snapshot = cached,
                        source = CurrencyRatesSource.CACHE,
                        isStale = true,
                        error = e
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    private fun isFresh(snapshot: CurrencyRatesSnapshot, nowMillis: Long): Boolean {
        val age = nowMillis - snapshot.updatedAtMillis
        return age in 0 until cacheTtlMillis
    }
}
