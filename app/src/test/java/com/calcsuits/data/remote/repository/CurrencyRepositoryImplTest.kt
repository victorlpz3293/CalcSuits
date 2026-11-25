package com.calcsuits.data.remote.repository

import com.calcsuits.data.remote.api.ExchangeRateApi
import com.calcsuits.data.remote.cache.CurrencyRatesCache
import com.calcsuits.data.remote.dto.CurrencyResponse
import com.calcsuits.domain.repository.CurrencyRatesSnapshot
import com.calcsuits.domain.repository.CurrencyRatesSource
import com.calcsuits.domain.repository.CurrencyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CurrencyRepositoryImplTest {

    @Test
    fun refreshSuccessSavesSnapshotIncludingNioAndReturnsRemote() = runTest {
        val fakeApi = FakeExchangeRateApi {
            CurrencyResponse(
                result = "success",
                baseCode = "USD",
                timeLastUpdateUnix = 1_732_500_000L,
                rates = mapOf(
                    "USD" to 1.0,
                    "EUR" to 0.94,
                    "NIO" to 36.68,
                    "MXN" to 20.15,
                    "CRC" to 510.50,
                    "GBP" to 0.79
                )
            )
        }
        val fakeCache = FakeCurrencyRatesCache()
        var now = 1_732_510_000_000L
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
            clock = { now }
        )

        val result = repository.refreshExchangeRates("USD").getOrThrow()

        assertEquals(1, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.REMOTE, result.source)
        assertFalse(result.isStale)
        assertNull(result.error)
        assertEquals(36.68, result.snapshot.rates["NIO"]!!, 1e-6)
        assertEquals(now, result.snapshot.updatedAtMillis)
        assertEquals(result.snapshot, repository.getCachedRates("USD"))
    }

    @Test
    fun networkErrorWithExistingCachePreservesCacheAndReturnsStaleResult() = runTest {
        val oldSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "NIO" to 36.50, "EUR" to 0.91),
            updatedAtMillis = 1_732_000_000_000L
        )
        val fakeCache = FakeCurrencyRatesCache(initial = oldSnapshot)
        val fakeApi = FakeExchangeRateApi { throw IOException("No internet connection") }
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
            clock = { oldSnapshot.updatedAtMillis + CurrencyRepository.CACHE_TTL_MILLIS + 60_000L }
        )

        val result = repository.refreshExchangeRates("USD", forceRefresh = true).getOrThrow()

        assertEquals(1, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.CACHE, result.source)
        assertTrue(result.isStale)
        assertTrue(result.error is IOException)
        assertEquals(oldSnapshot, result.snapshot)
        assertEquals(oldSnapshot, fakeCache.read("USD"))
    }

    @Test
    fun networkErrorWithoutCacheReturnsFailure() = runTest {
        val fakeCache = FakeCurrencyRatesCache()
        val fakeApi = FakeExchangeRateApi { throw IOException("Timeout") }
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            clock = { 1_732_510_000_000L }
        )

        val result = repository.refreshExchangeRates("USD")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertNull(fakeCache.read("USD"))
    }

    @Test
    fun staleCacheTriggersRemoteRefreshAndReplacesCachedSnapshot() = runTest {
        val t0 = 1_732_000_000_000L
        val staleSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "NIO" to 36.10, "EUR" to 0.90),
            updatedAtMillis = t0
        )
        val fakeCache = FakeCurrencyRatesCache(initial = staleSnapshot)
        val fakeApi = FakeExchangeRateApi {
            CurrencyResponse(
                result = "success",
                baseCode = "USD",
                rates = mapOf("USD" to 1.0, "NIO" to 36.72, "EUR" to 0.95)
            )
        }
        val tExpired = t0 + CurrencyRepository.CACHE_TTL_MILLIS + 1_000L
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
            clock = { tExpired }
        )

        val result = repository.refreshExchangeRates("USD", forceRefresh = false).getOrThrow()

        assertEquals(1, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.REMOTE, result.source)
        assertFalse(result.isStale)
        assertEquals(36.72, result.snapshot.rates["NIO"]!!, 1e-6)
        assertEquals(tExpired, result.snapshot.updatedAtMillis)
        assertEquals(tExpired, fakeCache.read("USD")?.updatedAtMillis)
    }

    @Test
    fun staleCachePreservesOldSnapshotWhenRemoteRefreshFails() = runTest {
        val t0 = 1_732_000_000_000L
        val staleSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "NIO" to 36.10),
            updatedAtMillis = t0
        )
        val fakeCache = FakeCurrencyRatesCache(initial = staleSnapshot)
        val fakeApi = FakeExchangeRateApi { throw IOException("Host unreachable") }
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
            clock = { t0 + CurrencyRepository.CACHE_TTL_MILLIS * 3 }
        )

        val result = repository.refreshExchangeRates("USD", forceRefresh = false).getOrThrow()

        assertEquals(1, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.CACHE, result.source)
        assertTrue(result.isStale)
        assertNotNull(result.error)
        assertEquals(staleSnapshot, result.snapshot)
        assertEquals(staleSnapshot, fakeCache.read("USD"))
    }

    @Test
    fun freshCacheAvoidsNetworkCallUnlessForceRefreshIsTrue() = runTest {
        val t0 = 1_732_000_000_000L
        val freshSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "NIO" to 36.60),
            updatedAtMillis = t0
        )
        val fakeCache = FakeCurrencyRatesCache(initial = freshSnapshot)
        val fakeApi = FakeExchangeRateApi {
            CurrencyResponse(
                result = "success",
                baseCode = "USD",
                rates = mapOf("USD" to 1.0, "NIO" to 36.80)
            )
        }
        val tShortlyAfter = t0 + 10 * 60 * 1000L
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            cacheTtlMillis = CurrencyRepository.CACHE_TTL_MILLIS,
            clock = { tShortlyAfter }
        )

        val cachedResult = repository.refreshExchangeRates("USD", forceRefresh = false).getOrThrow()
        assertEquals(0, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.CACHE, cachedResult.source)
        assertFalse(cachedResult.isStale)
        assertEquals(36.60, cachedResult.snapshot.rates["NIO"]!!, 1e-6)

        val forcedResult = repository.refreshExchangeRates("USD", forceRefresh = true).getOrThrow()
        assertEquals(1, fakeApi.callCount)
        assertEquals(CurrencyRatesSource.REMOTE, forcedResult.source)
        assertEquals(36.80, forcedResult.snapshot.rates["NIO"]!!, 1e-6)
    }

    @Test
    fun invalidApiStatusPreservesCacheAndFiltersNonPositiveRates() = runTest {
        val initial = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "NIO" to 36.55),
            updatedAtMillis = 1_732_000_000_000L
        )
        val fakeCache = FakeCurrencyRatesCache(initial = initial)
        var failWithErrorStatus = true
        val fakeApi = FakeExchangeRateApi {
            if (failWithErrorStatus) {
                CurrencyResponse(result = "error", baseCode = "USD", rates = emptyMap())
            } else {
                CurrencyResponse(
                    result = "success",
                    baseCode = "USD",
                    rates = mapOf(
                        "USD" to 1.0,
                        "NIO" to 36.65,
                        "BAD_ZERO" to 0.0,
                        "BAD_NEG" to -5.0,
                        "BAD_NAN" to Double.NaN
                    )
                )
            }
        }
        val repository = CurrencyRepositoryImpl(
            api = fakeApi,
            cache = fakeCache,
            clock = { 1_732_500_000_000L }
        )

        val errorFallback = repository.refreshExchangeRates("USD", forceRefresh = true).getOrThrow()
        assertEquals(CurrencyRatesSource.CACHE, errorFallback.source)
        assertTrue(errorFallback.isStale)
        assertEquals(initial, errorFallback.snapshot)

        failWithErrorStatus = false
        val cleaned = repository.refreshExchangeRates("USD", forceRefresh = true).getOrThrow()
        assertEquals(CurrencyRatesSource.REMOTE, cleaned.source)
        assertEquals(mapOf("USD" to 1.0, "NIO" to 36.65), cleaned.snapshot.rates)
    }

    private class FakeExchangeRateApi(
        private val responder: suspend (String) -> CurrencyResponse
    ) : ExchangeRateApi {
        var callCount: Int = 0
            private set

        override suspend fun getLatestRates(base: String): CurrencyResponse {
            callCount++
            return responder(base)
        }
    }

    private class FakeCurrencyRatesCache(
        initial: CurrencyRatesSnapshot? = null
    ) : CurrencyRatesCache {
        private var stored: CurrencyRatesSnapshot? = initial

        override suspend fun read(baseCode: String): CurrencyRatesSnapshot? =
            stored?.takeIf { it.baseCode.equals(baseCode, ignoreCase = true) }

        override suspend fun write(snapshot: CurrencyRatesSnapshot) {
            stored = snapshot
        }
    }
}
