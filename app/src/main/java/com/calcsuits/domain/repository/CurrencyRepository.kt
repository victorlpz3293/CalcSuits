package com.calcsuits.domain.repository

import com.calcsuits.data.remote.dto.CurrencyResponse

interface CurrencyRepository {
    suspend fun getExchangeRates(base: String): Result<CurrencyResponse>
}
