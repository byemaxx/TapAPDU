package com.byemaxx.tappapdu.model

data class GpoConfig(
    // Amount, Authorized (Tag 9F02) - in cents
    val amount: Long = 0L,
    
    // Transaction Date (Tag 9A) - null uses current date
    val transactionDate: String? = null,
    
    // Country Code (Tag 9F1A) - ISO numeric
    val countryCode: String = "0840", // Default: USA
    
    // Currency Code (Tag 5F2A)
    val currencyCode: String = "0840", // Default: USD
    
    // Transaction Type (Tag 9C)
    val transactionType: String = "00", // 00 = goods/services
    
    // Terminal Type (Tag 9F35)
    val terminalType: String = "22", // 22 = contactless
    
    // Terminal Capabilities (Tag 9F33)
    val terminalCapabilities: String = "E0E1C8",
    
    // Amount, Other (Tag 9F03)
    val otherAmount: Long = 0L,
    
    // Custom tag hex values (advanced mode)
    val customTags: Map<String, String> = emptyMap()
)

// Preset configurations
object GpoPresets {
    val USD_PURCHASE = GpoConfig(
        countryCode = "0840",
        currencyCode = "0840",
        transactionType = "00"
    )
    
    val CNY_PURCHASE = GpoConfig(
        countryCode = "0156",
        currencyCode = "0156",
        transactionType = "00"
    )
    
    val EUR_PURCHASE = GpoConfig(
        countryCode = "0276", // Germany
        currencyCode = "0978", // EUR
        transactionType = "00"
    )
    
    val CASH_WITHDRAWAL = GpoConfig(
        transactionType = "09" // 09 = cash withdrawal
    )
}

// Currency and transaction labels
object CurrencyCodes {
    val currencies = mapOf(
        "0840" to "USD",
        "0124" to "CAD",
        "0156" to "CNY",
        "0978" to "EUR",
        "0392" to "JPY",
        "0826" to "GBP",
        "0344" to "HKD"
    )
    
    val transactionTypes = mapOf(
        "00" to "Goods/Services",
        "01" to "Cash",
        "09" to "Cash Withdrawal",
        "20" to "Refund",
        "30" to "Balance Inquiry"
    )
}
