package com.calcsuits.data.remote.cache

import com.calcsuits.domain.repository.CurrencyRatesSnapshot

interface CurrencyRatesCache {
    suspend fun read(baseCode: String): CurrencyRatesSnapshot?
    suspend fun write(snapshot: CurrencyRatesSnapshot)
}
