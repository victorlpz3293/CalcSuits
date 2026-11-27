package com.calcsuits.features.converter.ui.viewmodel

import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.model.converter.ConversionCategory
import com.calcsuits.domain.repository.CurrencyRatesResult
import com.calcsuits.domain.repository.CurrencyRatesSnapshot
import com.calcsuits.domain.repository.CurrencyRatesSource
import com.calcsuits.domain.repository.CurrencyRepository
import com.calcsuits.domain.usecase.ConvertUnitsUseCase
import com.calcsuits.features.converter.ui.screens.resolveConverterGridColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun converterStartsOnNineCategoryHubAndNavigatesToEachCategoryAndBack() = runTest {
        val repo = FakeCurrencyRepository(
            cached = null,
            refreshHandler = { _, _ -> Result.failure(IOException("Offline")) }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)

        assertNull(vm.state.value.activeCategory)
        assertEquals(9, ConversionCategory.all.size)
        assertEquals(
            listOf("length", "mass", "area", "volume", "temperature", "speed", "time", "data", "currency"),
            ConversionCategory.all.map { it.key }
        )

        vm.onValueChange("42")
        ConversionCategory.all.forEach { category ->
            vm.onCategorySelected(category)
            assertEquals(category, vm.state.value.activeCategory)
            assertEquals(category, vm.state.value.selectedCategory)
            assertEquals("42", vm.state.value.fromValue)

            vm.onBackToCategories()
            assertNull(vm.state.value.activeCategory)
            assertEquals("42", vm.state.value.fromValue)
        }
    }

    @Test
    fun converterGridUsesTwoColumnsOnPhonesAndThreeColumnsOnWideScreens() {
        assertEquals(2, resolveConverterGridColumns(360f))
        assertEquals(2, resolveConverterGridColumns(412f))
        assertEquals(2, resolveConverterGridColumns(599.9f))
        assertEquals(3, resolveConverterGridColumns(600f))
        assertEquals(3, resolveConverterGridColumns(840f))
        assertEquals(3, resolveConverterGridColumns(1024f))
    }

    @Test
    fun selectingCurrencyLoadsCacheThenRefreshesAndFormatsWithThousandsSeparator() = runTest {
        val cachedSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "EUR" to 0.90, "NIO" to 36.00),
            updatedAtMillis = 1_732_000_000_000L
        )
        val remoteSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "EUR" to 0.95, "NIO" to 36.80, "MXN" to 20.00, "CRC" to 510.0, "GBP" to 0.80),
            updatedAtMillis = 1_732_500_000_000L
        )
        val repo = FakeCurrencyRepository(
            cached = cachedSnapshot,
            refreshHandler = { _, _ ->
                Result.success(
                    CurrencyRatesResult(
                        snapshot = remoteSnapshot,
                        source = CurrencyRatesSource.REMOTE,
                        isStale = false
                    )
                )
            }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)
        vm.updateLocale(Locale.US)
        vm.onValueChange("1000")

        vm.onCategorySelected(ConversionCategory.Currency)
        advanceUntilIdle()

        val nioUnit = vm.state.value.availableUnits.first { it.symbol == "NIO" }
        vm.onUnitChange(from = false, unit = nioUnit)

        assertEquals("36,800.00", vm.state.value.toValue)
        assertFalse(vm.state.value.isRefreshingCurrency)
        assertFalse(vm.state.value.isCurrencyOffline)
        assertTrue(vm.state.value.referenceCurrencySymbols.isEmpty())
        val status = vm.state.value.currencyStatusText as UiText.StringResource
        assertEquals(R.string.converter_rates_updated, status.resId)
    }

    @Test
    fun networkFailurePreservesCachedRatesAndShowsOfflineCachedStatus() = runTest {
        val cachedSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "EUR" to 0.92, "NIO" to 36.50, "MXN" to 18.0, "CRC" to 512.0, "GBP" to 0.79),
            updatedAtMillis = 1_732_000_000_000L
        )
        val repo = FakeCurrencyRepository(
            cached = cachedSnapshot,
            refreshHandler = { _, _ ->
                Result.success(
                    CurrencyRatesResult(
                        snapshot = cachedSnapshot,
                        source = CurrencyRatesSource.CACHE,
                        isStale = true,
                        error = IOException("Offline")
                    )
                )
            }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)
        vm.updateLocale(Locale.US)
        vm.onValueChange("1000")
        vm.onCategorySelected(ConversionCategory.Currency)
        advanceUntilIdle()

        val nioUnit = vm.state.value.availableUnits.first { it.symbol == "NIO" }
        vm.onUnitChange(from = false, unit = nioUnit)

        assertEquals("36,500.00", vm.state.value.toValue)
        assertTrue(vm.state.value.isCurrencyOffline)
        assertFalse(vm.state.value.isRefreshingCurrency)
        val status = vm.state.value.currencyStatusText as UiText.StringResource
        assertEquals(R.string.converter_rates_offline_cached, status.resId)
    }

    @Test
    fun networkFailureWithoutCacheKeepsReferenceRatesAndSetsOfflineReferenceStatus() = runTest {
        val repo = FakeCurrencyRepository(
            cached = null,
            refreshHandler = { _, _ -> Result.failure(IOException("No network")) }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)
        vm.updateLocale(Locale.US)
        vm.onValueChange("1000")
        vm.onCategorySelected(ConversionCategory.Currency)
        advanceUntilIdle()

        assertTrue(vm.state.value.isCurrencyOffline)
        assertFalse(vm.state.value.isRefreshingCurrency)
        assertEquals("920.00", vm.state.value.toValue)
        val status = vm.state.value.currencyStatusText as UiText.StringResource
        assertEquals(R.string.converter_rates_offline_reference, status.resId)
    }

    @Test
    fun manualRefreshForcesRemoteCallAndMarksMissingNioAsReference() = runTest {
        var lastForceRefresh: Boolean? = null
        val snapshotWithoutNio = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "EUR" to 0.93, "MXN" to 19.0, "CRC" to 510.0, "GBP" to 0.78),
            updatedAtMillis = 1_732_600_000_000L
        )
        val repo = FakeCurrencyRepository(
            cached = null,
            refreshHandler = { _, force ->
                lastForceRefresh = force
                Result.success(
                    CurrencyRatesResult(
                        snapshot = snapshotWithoutNio,
                        source = CurrencyRatesSource.REMOTE,
                        isStale = false
                    )
                )
            }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)
        vm.onCategorySelected(ConversionCategory.Currency)
        advanceUntilIdle()
        assertEquals(false, lastForceRefresh)

        vm.refreshCurrencyRates()
        advanceUntilIdle()

        assertEquals(true, lastForceRefresh)
        assertTrue("NIO" in vm.state.value.referenceCurrencySymbols)
        assertFalse("EUR" in vm.state.value.referenceCurrencySymbols)
    }

    @Test
    fun selectingAllCategoriesAndSwappingUnitsProducesInverseConversions() = runTest {
        val remoteSnapshot = CurrencyRatesSnapshot(
            baseCode = "USD",
            rates = mapOf("USD" to 1.0, "EUR" to 0.90, "NIO" to 36.60, "MXN" to 18.0, "CRC" to 510.0, "GBP" to 0.80),
            updatedAtMillis = 1_732_700_000_000L
        )
        val repo = FakeCurrencyRepository(
            cached = remoteSnapshot,
            refreshHandler = { _, _ ->
                Result.success(
                    CurrencyRatesResult(
                        snapshot = remoteSnapshot,
                        source = CurrencyRatesSource.REMOTE,
                        isStale = false
                    )
                )
            }
        )
        val vm = ConverterViewModel(ConvertUnitsUseCase(), repo)
        vm.updateLocale(Locale.US)
        vm.onValueChange("100")

        ConversionCategory.all.forEach { category ->
            vm.onCategorySelected(category)
            advanceUntilIdle()
            val forward = vm.state.value.toValue.replace(",", "")
            assertTrue("Expected non-blank result for ${category.key}", forward.isNotBlank())

            vm.swapUnits()
            vm.onValueChange(forward)
            val roundTrip = vm.state.value.toValue.replace(",", "").toDouble()
            assertEquals("Round-trip failed for ${category.key}", 100.0, roundTrip, 0.05)

            vm.onValueChange("100")
        }
    }

    private class FakeCurrencyRepository(
        private val cached: CurrencyRatesSnapshot?,
        private val refreshHandler: suspend (String, Boolean) -> Result<CurrencyRatesResult>
    ) : CurrencyRepository {
        override suspend fun getCachedRates(base: String): CurrencyRatesSnapshot? = cached

        override suspend fun refreshExchangeRates(
            base: String,
            forceRefresh: Boolean
        ): Result<CurrencyRatesResult> = refreshHandler(base, forceRefresh)
    }
}
