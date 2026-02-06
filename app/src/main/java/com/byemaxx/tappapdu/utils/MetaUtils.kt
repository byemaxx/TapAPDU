package com.byemaxx.tappapdu.utils

object MetaUtils {
    private val countryCodes = mapOf(
        "0036" to "Australia",
        "0056" to "Belgium",
        "0076" to "Brazil",
        "0124" to "Canada",
        "0156" to "China",
        "0208" to "Denmark",
        "0250" to "France",
        "0276" to "Germany",
        "0344" to "Hong Kong",
        "0356" to "India",
        "0360" to "Indonesia",
        "0372" to "Ireland",
        "0380" to "Italy",
        "0392" to "Japan",
        "0410" to "South Korea",
        "0446" to "Macau",
        "0458" to "Malaysia",
        "0484" to "Mexico",
        "0528" to "Netherlands",
        "0554" to "New Zealand",
        "0578" to "Norway",
        "0608" to "Philippines",
        "0643" to "Russia",
        "0702" to "Singapore",
        "0710" to "South Africa",
        "0724" to "Spain",
        "0752" to "Sweden",
        "0756" to "Switzerland",
        "0158" to "Taiwan",
        "0764" to "Thailand",
        "0826" to "United Kingdom",
        "0840" to "United States",
        "0704" to "Vietnam"
    )

    fun getCountryName(code: String): String {
        return countryCodes[code] ?: "Unknown"
    }

    fun getServiceCodeDescription(code: String): String {
        if (code.length != 3) return ""
        
        // 1st Digit: Interchange
        val d1 = code[0]
        val technology = when(d1) {
            '2', '6' -> "Chip"
            '5' -> "National"
            else -> "Magstripe"
        }
        val interchange = if (d1 in listOf('1','2','5','6')) "Int'l" else "Nat'l"
        
        // 2nd Digit: Authorization
        val authorization = when(code[1]) {
            '0', '2', '4' -> "Normal"
            else -> "Online Only"
        }
        
        // 3rd Digit: PIN
        val pin = when(code[2]) {
            '0', '3', '5', '6', '7' -> "Pin"
            else -> "No Pin"
        }
        
        return "$technology, $interchange, $authorization, $pin"
    }
}
