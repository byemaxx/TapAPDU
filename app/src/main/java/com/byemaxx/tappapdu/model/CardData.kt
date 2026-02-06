package com.byemaxx.tappapdu.model

data class CardData(
    val pan: String?,
    val expiry: String?,
    val serviceCode: String?,
    val countryCode: String?, // Tag 5F28
    val psn: String?,         // Tag 5F34
    val holderName: String?,  // Tag 5F20
    val logEntry: String?     // Tag 9F4D
)
