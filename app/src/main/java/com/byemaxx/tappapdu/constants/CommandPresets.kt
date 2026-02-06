package com.byemaxx.tappapdu.constants

object CommandPresets {
    const val PPSE = "00A404000E325041592E5359532E444446303100"
    const val MASTERCARD = "00A4040007A000000004101000"
    const val VISA = "00A4040007A000000003101000"
    const val AMEX = "00A4040005A00000002500"
    const val UNION_PAY = "00A4040007A000000333010100"
    const val DISCOVER = "00A4040007A000000152301000"
    const val JCB = "00A4040007A000000065101000"
    
    val knownCommands = listOf(
        "PPSE" to PPSE,
        "Mastercard" to MASTERCARD,
        "Visa" to VISA,
        "Amex" to AMEX,
        "UnionPay" to UNION_PAY,
        "Discover" to DISCOVER,
        "JCB" to JCB
    )
}
