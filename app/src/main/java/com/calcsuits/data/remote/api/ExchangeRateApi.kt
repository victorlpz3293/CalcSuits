package com.calcsuits.data.remote.api

import com.calcsuits.data.remote.dto.CurrencyResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("v6/{apiKey}/latest/{base}")
    suspend fun getLatestRates(
        @Path("apiKey") apiKey: String,
        @Path("base") base: String
    ): CurrencyResponse

    companion object {
        const val BASE_URL = "https://v6.exchangerate-api.com/"
    }
}
