package com.calcsuits.features.converter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuits.R
import com.calcsuits.domain.model.UiText
import com.calcsuits.domain.model.converter.ConversionCategory
import com.calcsuits.domain.model.converter.ConversionUnit
import com.calcsuits.domain.repository.CurrencyRepository
import com.calcsuits.domain.usecase.ConvertUnitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class UnitConversionPreview(
    val unit: ConversionUnit,
    val formattedValue: String
)

data class ConverterState(
    val activeCategory: ConversionCategory? = null,
    val selectedCategory: ConversionCategory = ConversionCategory.Length,
    val availableUnits: List<ConversionUnit> = ConversionCategory.Length.units,
    val fromUnit: ConversionUnit = ConversionCategory.Length.units[0],
    val toUnit: ConversionUnit = ConversionCategory.Length.units[1],
    val fromValue: String = "1",
    val toValue: String = "",
    val allUnitPreviews: List<UnitConversionPreview> = emptyList(),
    val isRefreshingCurrency: Boolean = false,
    val isCurrencyOffline: Boolean = false,
    val currencyStatusText: UiText? = null,
    val referenceCurrencySymbols: Set<String> = emptySet()
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val convertUseCase: ConvertUnitsUseCase,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterState())
    val state = _state.asStateFlow()

    private var currentLocale: Locale = Locale.getDefault()
    private var currencyUnits: List<ConversionUnit> = ConversionCategory.Currency.units
    private var currencyUpdatedAtMillis: Long? = null
    private var referenceSymbols: Set<String> = emptySet()
    private var currencyRefreshJob: Job? = null

    init {
        calculateConversion()
    }

    fun updateLocale(locale: Locale) {
        if (currentLocale != locale) {
            currentLocale = locale
            updateCurrencyStatusText()
            calculateConversion()
        }
    }

    fun onCategorySelected(category: ConversionCategory) {
        val units = unitsFor(category)
        _state.update {
            it.copy(
                activeCategory = category,
                selectedCategory = category,
                availableUnits = units,
                fromUnit = units[0],
                toUnit = units.getOrElse(1) { units[0] },
                currencyStatusText = if (category == ConversionCategory.Currency) {
                    buildCurrencyStatusText(it.isRefreshingCurrency, it.isCurrencyOffline)
                } else {
                    it.currencyStatusText
                },
                referenceCurrencySymbols = if (category == ConversionCategory.Currency) {
                    referenceSymbols
                } else {
                    emptySet()
                }
            )
        }
        calculateConversion()
        if (category == ConversionCategory.Currency) {
            loadCurrencyRates(forceRefresh = false)
        }
    }

    fun onBackToCategories() {
        _state.update { it.copy(activeCategory = null) }
    }

    fun refreshCurrencyRates() {
        loadCurrencyRates(forceRefresh = true)
    }

    fun onValueChange(value: String) {
        var dotSeen = false
        val cleanValue = buildString {
            value.forEachIndexed { idx, c ->
                when {
                    c.isDigit() -> append(c)
                    (c == '.' || c == ',') && !dotSeen -> {
                        dotSeen = true
                        append('.')
                    }
                    c == '-' && idx == 0 -> append(c)
                }
            }
        }
        _state.update { it.copy(fromValue = cleanValue) }
        calculateConversion()
    }

    fun onUnitChange(from: Boolean, unit: ConversionUnit) {
        _state.update { current ->
            val resolvedUnit = current.availableUnits.firstOrNull { it.symbol == unit.symbol } ?: unit
            if (from) current.copy(fromUnit = resolvedUnit) else current.copy(toUnit = resolvedUnit)
        }
        calculateConversion()
    }

    fun swapUnits() {
        _state.update { it.copy(fromUnit = it.toUnit, toUnit = it.fromUnit) }
        calculateConversion()
    }

    private fun loadCurrencyRates(forceRefresh: Boolean) {
        currencyRefreshJob?.cancel()
        currencyRefreshJob = viewModelScope.launch {
            // 1. Show cached rates immediately if available
            val cached = currencyRepository.getCachedRates(CurrencyRepository.DEFAULT_BASE)
            if (cached != null) {
                applyRatesSnapshot(
                    rates = cached.rates,
                    updatedAtMillis = cached.updatedAtMillis,
                    isOffline = _state.value.isCurrencyOffline,
                    isRefreshing = true
                )
            } else {
                _state.update {
                    it.copy(
                        isRefreshingCurrency = true,
                        currencyStatusText = buildCurrencyStatusText(
                            isRefreshing = true,
                            isOffline = it.isCurrencyOffline
                        )
                    )
                }
            }

            // 2. Attempt refresh in background; preserve cache on failure
            val refreshResult = currencyRepository.refreshExchangeRates(
                base = CurrencyRepository.DEFAULT_BASE,
                forceRefresh = forceRefresh
            )
            refreshResult
                .onSuccess { result ->
                    applyRatesSnapshot(
                        rates = result.snapshot.rates,
                        updatedAtMillis = result.snapshot.updatedAtMillis,
                        isOffline = result.error != null,
                        isRefreshing = false
                    )
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isRefreshingCurrency = false,
                            isCurrencyOffline = true,
                            currencyStatusText = buildCurrencyStatusText(
                                isRefreshing = false,
                                isOffline = true
                            )
                        )
                    }
                }
        }
    }

    private fun applyRatesSnapshot(
        rates: Map<String, Double>,
        updatedAtMillis: Long,
        isOffline: Boolean,
        isRefreshing: Boolean
    ) {
        val (updatedUnits, fallbackSymbols) = buildCurrencyUnits(rates)
        currencyUnits = updatedUnits
        currencyUpdatedAtMillis = updatedAtMillis
        referenceSymbols = fallbackSymbols

        _state.update { current ->
            val statusText = buildCurrencyStatusText(isRefreshing = isRefreshing, isOffline = isOffline)
            if (current.selectedCategory == ConversionCategory.Currency) {
                val syncedFrom = updatedUnits.firstOrNull { it.symbol == current.fromUnit.symbol } ?: updatedUnits[0]
                val syncedTo = updatedUnits.firstOrNull { it.symbol == current.toUnit.symbol }
                    ?: updatedUnits.getOrElse(1) { updatedUnits[0] }
                current.copy(
                    availableUnits = updatedUnits,
                    fromUnit = syncedFrom,
                    toUnit = syncedTo,
                    isRefreshingCurrency = isRefreshing,
                    isCurrencyOffline = isOffline,
                    currencyStatusText = statusText,
                    referenceCurrencySymbols = fallbackSymbols
                )
            } else {
                current.copy(
                    isRefreshingCurrency = isRefreshing,
                    isCurrencyOffline = isOffline,
                    currencyStatusText = statusText
                )
            }
        }
        if (_state.value.selectedCategory == ConversionCategory.Currency) {
            calculateConversion()
        }
    }

    private fun buildCurrencyUnits(rates: Map<String, Double>): Pair<List<ConversionUnit>, Set<String>> {
        val fallbackSymbols = mutableSetOf<String>()
        val units = ConversionCategory.Currency.units.map { defaultUnit ->
            val liveRate = rates[defaultUnit.symbol]
            if (liveRate != null && liveRate.isFinite() && liveRate > 0.0) {
                defaultUnit.copy(factorToBase = 1.0 / liveRate)
            } else {
                fallbackSymbols += defaultUnit.symbol
                defaultUnit
            }
        }
        return units to fallbackSymbols
    }

    private fun updateCurrencyStatusText() {
        _state.update { current ->
            current.copy(
                currencyStatusText = buildCurrencyStatusText(
                    isRefreshing = current.isRefreshingCurrency,
                    isOffline = current.isCurrencyOffline
                )
            )
        }
    }

    private fun buildCurrencyStatusText(isRefreshing: Boolean, isOffline: Boolean): UiText {
        val timestamp = currencyUpdatedAtMillis
        if (timestamp == null) {
            return when {
                isRefreshing -> UiText.StringResource(R.string.converter_rates_updating)
                isOffline -> UiText.StringResource(R.string.converter_rates_offline_reference)
                else -> UiText.StringResource(R.string.cat_currency_note)
            }
        }
        val formattedDate = formatTimestamp(timestamp, currentLocale)
        return if (isOffline) {
            UiText.StringResource(R.string.converter_rates_offline_cached, formattedDate)
        } else {
            UiText.StringResource(R.string.converter_rates_updated, formattedDate)
        }
    }

    private fun formatTimestamp(epochMillis: Long, locale: Locale): String {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        return formatter.format(Date(epochMillis))
    }

    private fun unitsFor(category: ConversionCategory): List<ConversionUnit> =
        if (category == ConversionCategory.Currency) currencyUnits else category.units

    private fun calculateConversion() {
        val current = _state.value
        val value = current.fromValue.toDoubleOrNull()
        if (value == null) {
            _state.update { it.copy(toValue = "", allUnitPreviews = emptyList()) }
            return
        }
        val fixedDecimals = if (current.selectedCategory == ConversionCategory.Currency) 2 else null
        val result = convertUseCase(value, current.fromUnit, current.toUnit, fixedDecimals, currentLocale)
        val previews = current.availableUnits
            .filter { it.symbol != current.fromUnit.symbol }
            .map { target ->
                UnitConversionPreview(
                    unit = target,
                    formattedValue = convertUseCase(value, current.fromUnit, target, fixedDecimals, currentLocale)
                )
            }
        _state.update { it.copy(toValue = result, allUnitPreviews = previews) }
    }
}
