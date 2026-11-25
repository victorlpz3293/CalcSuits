package com.calcsuits.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CurrencyResponse(
    @SerializedName("result")
    val result: String = "",
    @SerializedName("base_code")
    val baseCode: String = "",
    @SerializedName("time_last_update_unix")
    val timeLastUpdateUnix: Long? = null,
    @SerializedName("rates")
    val rates: Map<String, Double> = emptyMap()
)
