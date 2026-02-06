package com.byemaxx.tappapdu.utils

import com.byemaxx.tappapdu.model.CardData
import com.byemaxx.tappapdu.model.GpoConfig
import java.text.SimpleDateFormat
import java.util.*

object TlvUtils {
    // Basic TLV parser that looks for a specific tag at the top level or inside constructed tags (70, 77, A5, 61, etc.)
    // Returns the Value part of the TLV
    fun findTag(data: ByteArray, tagHex: String): ByteArray? {
        val targetTag = try {
            ApduUtils.hexStringToByteArray(tagHex)
        } catch (e: Exception) { return null }

        var i = 0
        while (i < data.size) {
            // Skip Padding (00 / FF) - Critical for Visa/EMV compliance
            val b = data[i].toInt() and 0xFF
            if (b == 0x00 || b == 0xFF) {
                i++
                continue
            }

            // 1. Tag
            val tStart = i
            val firstByte = data[i]
            i++
            
            // Handle multi-byte tags (e.g., 9F, 5F)
            if ((firstByte.toInt() and 0x1F) == 0x1F) {
                while (i < data.size) {
                    val nextB = data[i]
                    i++
                    if ((nextB.toInt() and 0x80) == 0) break
                }
            }
            val tEnd = i
            val currentTag = data.copyOfRange(tStart, tEnd)
            
            // 2. Length
            if (i >= data.size) break
            var len = data[i].toInt() and 0xFF
            i++
            if (len == 0x81) {
                if (i >= data.size) break
                len = data[i].toInt() and 0xFF
                i++
            } else if (len == 0x82) { // Just in case larger lengths
                 if (i + 1 >= data.size) break
                 len = ((data[i].toInt() and 0xFF) shl 8) or (data[i+1].toInt() and 0xFF)
                 i += 2
            } else if (len > 0x82) {
                 // Should not happen for standard EMV, but safe break
                 break
            }
            
            if (i + len > data.size) break // Invalid length or data lookup out of bounds
            
            val value = data.copyOfRange(i, i + len)
            
            // Check if this is the tag we want
            if (java.util.Arrays.equals(currentTag, targetTag)) {
                return value
            }
            
            // Check if this is a constructed tag (contains other tags)
            // Mask 0x20 in first byte indicates constructed
            if ((firstByte.toInt() and 0x20) != 0) {
                 val result = findTag(value, tagHex)
                 if (result != null) return result
            }
            
            i += len
        }
        return null
    }
    
    fun extractCardData(data: ByteArray): CardData? {
        var pan: String? = null
        var expiry: String? = null
        var serviceCode: String? = null
        
        // Priority 1: Tag 5A (PAN) - Must be strictly numeric
        val panBytes = findTag(data, "5A")
        if (panBytes != null) {
            val panRaw = ApduUtils.toHexString(panBytes).trimEnd('F')
            if (panRaw.matches(Regex("^[0-9]+$")) && panRaw.length in 12..19) {
                pan = panRaw
                val expBytes = findTag(data, "5F24") // Application Expiration Date
                if(expBytes != null) {
                    val rawDate = ApduUtils.toHexString(expBytes) // YYMMDD
                    if (rawDate.length >= 4) {
                        expiry = "${rawDate.substring(2,4)}/20${rawDate.substring(0,2)}" // MM/YYYY
                    }
                }
            }
        }
        
        // Priority 2: Tag 57 (Track 2 Equiv)
        // Format: PAN + 'D' + Expiry(YYMM) + ServiceCode + Discretional
        if (pan == null) {
            val track2Bytes = findTag(data, "57") ?: findTag(data, "9F6B")
            if (track2Bytes != null) {
                 val t2 = ApduUtils.toHexString(track2Bytes)
                 val separator = t2.indexOf('D')
                 if (separator != -1) {
                     val potentialPan = t2.substring(0, separator)
                     // Valid PAN: 12-19 digits, strictly numeric
                     if (potentialPan.length in 12..19 && potentialPan.matches(Regex("^[0-9]+$"))) {
                         pan = potentialPan
                         
                         val expStart = separator + 1
                         if (expStart + 4 <= t2.length) {
                             val t2Exp = t2.substring(expStart, expStart + 4) // YYMM
                             // Just update expiry if we haven't found a better one
                             if (expiry == null) {
                                 expiry = "${t2Exp.substring(2,4)}/20${t2Exp.substring(0,2)}"
                             }
                             
                             val svcStart = expStart + 4
                             if (svcStart + 3 <= t2.length) {
                                 serviceCode = t2.substring(svcStart, svcStart + 3)
                             }
                         }
                     }
                 }
            }
        }
        
        // Independent Extraction of Country and PSN
        val countryBytes = findTag(data, "5F28")
        val countryCode = if (countryBytes != null) ApduUtils.toHexString(countryBytes) else null
        
        val psnBytes = findTag(data, "5F34")
        val psn = if (psnBytes != null) ApduUtils.toHexString(psnBytes) else null

        val nameBytes = findTag(data, "5F20")
        val holderName = if (nameBytes != null) ApduUtils.toAsciiString(nameBytes).trim() else null

        val logEntryBytes = findTag(data, "9F4D")
        val logEntry = if (logEntryBytes != null) ApduUtils.toHexString(logEntryBytes) else null
        
        // Return object if ANY field is found (allows partial updates)
        if (pan != null || expiry != null || serviceCode != null || countryCode != null || psn != null || holderName != null || logEntry != null) {
            return CardData(pan, expiry, serviceCode, countryCode, psn, holderName, logEntry)
        }
        return null
    }

    fun constructGpoCommand(pdolRaw: ByteArray?): String {
        return constructGpoCommand(pdolRaw, null)
    }

    fun constructGpoCommand(pdolRaw: ByteArray?, config: GpoConfig?): String {
        if (pdolRaw == null) return "80A8000002830000" // Default generic GPO
        
        val gpoConfig = config ?: GpoConfig() // Use default config if null

        val sb = StringBuilder()
        var i = 0
        while (i < pdolRaw.size) {
            // 1. Tag parsing
            val tagStart = i
            val tag1 = pdolRaw[i]
            i++
            // If b1-b5 are 1, it's a multi-byte tag
            if ((tag1.toInt() and 0x1F) == 0x1F) {
                // Read subsequent bytes
                while (i < pdolRaw.size) {
                    val nextByte = pdolRaw[i]
                    i++
                    // If MSB is 0, it's the last byte of tag
                    if ((nextByte.toInt() and 0x80) == 0) break 
                }
            }
            val tagHex = ApduUtils.toHexString(pdolRaw.copyOfRange(tagStart, i))

            // 2. Length parsing
            if (i < pdolRaw.size) {
                val len = pdolRaw[i].toInt() and 0xFF
                i++
                
                // 3. Smart Value Generation
                val valHex = when (tagHex) {
                    "9F02" -> { // Amount, Authorized (授权金额)
                        val amountHex = String.format("%012d", gpoConfig.amount)
                        if (len == 6) amountHex else "00".repeat(len)
                    }
                    "9F03" -> { // Amount, Other (其他金额)
                        val otherHex = String.format("%012d", gpoConfig.otherAmount)
                        if (len == 6) otherHex else "00".repeat(len)
                    }
                    "9C" -> { // Transaction Type
                        if (len == 1) gpoConfig.transactionType else "00".repeat(len)
                    }
                    "9F35" -> { // Terminal Type
                        if (len == 1) gpoConfig.terminalType else "22"
                    }
                    "9F33" -> { // Terminal Capabilities
                        if (len == 3) gpoConfig.terminalCapabilities else "E0E1C8"
                    }
                    "9F66" -> { // Terminal Transaction Qualifiers (TTQ)
                        // 28 00 00 00 = qVSDC supported, MSD supported, Contactless
                        if (len == 4) "28000000" else "00".repeat(len)
                    }
                    "9A" -> { // Transaction Date YYMMDD
                        val date = gpoConfig.transactionDate ?: SimpleDateFormat("yyMMdd", Locale.US).format(Date())
                        if (len == 3) date else "00".repeat(len)
                    }
                    "9F37" -> { // Unpredictable Number
                        val rnd = Random()
                        val b = ByteArray(len)
                        rnd.nextBytes(b)
                        ApduUtils.toHexString(b)
                    }
                    "9F1A", "5F2A" -> { // Country Code / Currency Code
                        val code = if (tagHex == "9F1A") gpoConfig.countryCode else gpoConfig.currencyCode
                        if (len == 2) code else "00".repeat(len)
                    }
                    else -> {
                        // Check if there's a custom value for this tag
                        gpoConfig.customTags[tagHex] ?: "00".repeat(len)
                    }
                }
                sb.append(valHex)
            }
        }
        
        val valueBody = sb.toString()
        val lenVal = valueBody.length / 2
        
        // Command Data: 83 + L + Value
        // Note: simplified length encoding (works for length < 128)
        val dataField = "83${String.format("%02X", lenVal)}$valueBody"
        val lc = dataField.length / 2
        
        return "80A80000${String.format("%02X", lc)}${dataField}00" 
    }
}
