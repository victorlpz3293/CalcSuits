package com.calcsuits.data.remote.repository

import com.calcsuits.data.remote.api.ExchangeRateApi
import com.calcsuits.data.remote.dto.CurrencyResponse
import com.calcsuits.domain.repository.CurrencyRepository
import javax.inject.Inject

class CurrencyRepositoryImpl @Inject constructor(
    private val api: ExchangeRateApi
) : CurrencyRepository {

    private val apiKey = "YOUR_API_KEY"

    override suspend fun getExchangeRates(base: String): Result<CurrencyResponse> {
        return try {
            val response = api.getLatestRates(apiKey, base)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
