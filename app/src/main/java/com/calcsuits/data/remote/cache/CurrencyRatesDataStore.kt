package com.calcsuits.data.remote.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calcsuits.domain.repository.CurrencyRatesSnapshot
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.currencyCacheDataStore by preferencesDataStore(name = "calcsuits_currency_cache")

private val BASE_CODE_KEY = stringPreferencesKey("base_code")
private val RATES_JSON_KEY = stringPreferencesKey("rates_json")
private val UPDATED_AT_KEY = longPreferencesKey("updated_at_millis")

@Singleton
class CurrencyRatesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : CurrencyRatesCache {

    private val ratesMapType = object : TypeToken<Map<String, Double>>() {}.type

    override suspend fun read(baseCode: String): CurrencyRatesSnapshot? {
        val prefs = context.currencyCacheDataStore.data.first()
        val storedBase = prefs[BASE_CODE_KEY] ?: return null
        if (!storedBase.equals(baseCode, ignoreCase = true)) return null
        val json = prefs[RATES_JSON_KEY] ?: return null
        val updatedAt = prefs[UPDATED_AT_KEY] ?: return null
        val parsed = runCatching {
            gson.fromJson<Map<String, Double>>(json, ratesMapType)
        }.getOrNull()
            ?.entries
            ?.mapNotNull { (code, rate) ->
                if (rate.isFinite() && rate > 0.0) code.uppercase(Locale.ROOT) to rate else null
            }
            ?.toMap()
            ?: return null

        if (parsed.isEmpty()) return null
        return CurrencyRatesSnapshot(
            baseCode = storedBase.uppercase(Locale.ROOT),
            rates = parsed,
            updatedAtMillis = updatedAt
        )
    }

    override suspend fun write(snapshot: CurrencyRatesSnapshot) {
        val json = gson.toJson(snapshot.rates)
        context.currencyCacheDataStore.edit { prefs ->
            prefs[BASE_CODE_KEY] = snapshot.baseCode.uppercase(Locale.ROOT)
            prefs[RATES_JSON_KEY] = json
            prefs[UPDATED_AT_KEY] = snapshot.updatedAtMillis
        }
    }
}
