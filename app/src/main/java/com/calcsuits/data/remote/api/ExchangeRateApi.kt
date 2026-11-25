package com.calcsuits.data.remote.api

import com.calcsuits.data.remote.dto.CurrencyResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(
        @Path("base") base: String = DEFAULT_BASE
    ): CurrencyResponse

    companion object {
        const val BASE_URL = "https://open.er-api.com/"
        const val DEFAULT_BASE = "USD"
    }
}
