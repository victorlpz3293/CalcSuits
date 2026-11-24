package com.calcsuits.domain.model

data class History(
    val id: Long = 0,
    val expression: String,
    val result: String,
    val timestamp: Long
)
