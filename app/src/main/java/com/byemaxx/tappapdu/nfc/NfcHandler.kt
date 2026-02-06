package com.byemaxx.tappapdu.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.byemaxx.tappapdu.constants.CommandPresets
import com.byemaxx.tappapdu.utils.ApduUtils
import com.byemaxx.tappapdu.utils.MetaUtils
import com.byemaxx.tappapdu.utils.TlvUtils
import java.io.IOException

class NfcHandler {
    
    fun processTag(tag: Tag?, isAutoMode: Boolean, manualCommand: String): String {
        val isoDep = IsoDep.get(tag) ?: return "Error: No IsoDep tag found"
        
        return try {
            isoDep.connect()
            isoDep.timeout = 5000
            
            val result = if (isAutoMode) {
                processAutoScan(isoDep)
            } else {
                processManualCommand(isoDep, manualCommand)
            }
            
            isoDep.close()
            result
        } catch (e: IOException) {
            "Connection Error: ${e.message}"
        }
    }
    
    private fun processAutoScan(isoDep: IsoDep): String {
        val sb = StringBuilder()
        sb.append("--- New Session ---\n")
        sb.append("Starting Auto Scan...\n")
        
        var foundAny = false
        for ((name, cmdHex) in CommandPresets.knownCommands) {
            try {
                val result = transceive(isoDep, cmdHex)
                val sw = ApduUtils.getStatusWord(result)
                val swDesc = ApduUtils.getSWDescription(sw)
                
                if (sw == "9000") {
                    foundAny = true
                    sb.append("✅ $name: [Selected]\n")
                    
                    // 1. Try to get Label from FCI
                    val label = TlvUtils.findTag(result, "50") ?: TlvUtils.findTag(result, "9F12")
                    if (label != null) {
                        sb.append("   🏷️ Label: ${ApduUtils.toAsciiString(label)}\n")
                    }

                    // 2. Deep Scan
                    if (name != "PPSE") {
                        sb.append(performDeepScan(isoDep, result))
                    }

                } else {
                    if (sw != "6A82") {
                         sb.append("⚠️ $name: $sw ($swDesc)\n")
                    }
                }
            } catch (e: Exception) {
                sb.append("❌ $name: Error ${e.message}\n")
            }
        }
        
        if (!foundAny) sb.append("No known applications found.\n")
        sb.append("Scan Complete.\n")
        
        return sb.toString()
    }
    
    private fun performDeepScan(isoDep: IsoDep, selectResponse: ByteArray): String {
        val sb = StringBuilder()
        
        // Dynamic GPO (PDOL Handling)
        val pdol = TlvUtils.findTag(selectResponse, "9F38")
        val gpoCmd = TlvUtils.constructGpoCommand(pdol)
        val gpoRes = transceive(isoDep, gpoCmd)
        
        val gpoSw = ApduUtils.getStatusWord(gpoRes)
        sb.append("   📡 GPO: $gpoSw\n")
        
        // Extract AFL for robust reading
        var afl = TlvUtils.findTag(gpoRes, "94")
        if (afl == null) {
            val val80 = TlvUtils.findTag(gpoRes, "80")
            if (val80 != null && val80.size > 2) {
                afl = val80.copyOfRange(2, val80.size)
            }
        }

        // Data Accumulators
        var extractedPan: String? = null
        var extractedExp: String? = null
        var extractedSvc: String? = null
        var extractedCtry: String? = null
        var extractedPsn: String? = null
        var extractedName: String? = null
        var extractedLogEntry: String? = null
        var extractedAtc: String? = null

        // Check GPO response first
        val gpoData = TlvUtils.extractCardData(gpoRes)
        if (gpoData != null) {
            if (gpoData.pan != null) extractedPan = gpoData.pan
            if (gpoData.expiry != null) extractedExp = gpoData.expiry
            if (gpoData.serviceCode != null) extractedSvc = gpoData.serviceCode
            if (gpoData.countryCode != null) extractedCtry = gpoData.countryCode
            if (gpoData.psn != null) extractedPsn = gpoData.psn
            if (gpoData.holderName != null) extractedName = gpoData.holderName
            if (gpoData.logEntry != null) extractedLogEntry = gpoData.logEntry
        }
        val gpoAtc = TlvUtils.findTag(gpoRes, "9F36")
        if (gpoAtc != null) extractedAtc = ApduUtils.toHexString(gpoAtc).toInt(16).toString()

        val recordsToRead = mutableListOf<Pair<Int, Int>>() // SFI, RecordNum

        if (afl != null) {
            sb.append("   📂 AFL Found: ${ApduUtils.toHexString(afl)}\n")
            var ptr = 0
            while (ptr + 4 <= afl.size) {
                val sfi = (afl[ptr].toInt() and 0xFF) ushr 3
                val startRec = afl[ptr+1].toInt() and 0xFF
                val endRec = afl[ptr+2].toInt() and 0xFF
                
                for (r in startRec..endRec) {
                    recordsToRead.add(sfi to r)
                }
                ptr += 4
            }
        } else {
            // Fallback Blind Scan
            listOf(1, 2).forEach { s ->
                listOf(1, 2, 3).forEach { r -> recordsToRead.add(s to r) }
            }
        }

        // Execute Read Record
        for ((sfi, rec) in recordsToRead) {
            val p2 = (sfi shl 3) or 4
            val readCmd = "00B2${String.format("%02X", rec)}${String.format("%02X", p2)}00"
            val readRes = transceive(isoDep, readCmd)
            
            if (ApduUtils.getStatusWord(readRes) == "9000") {
                val cd = TlvUtils.extractCardData(readRes)
                if (cd != null) {
                    if (extractedPan == null && cd.pan != null) extractedPan = cd.pan
                    if (extractedExp == null && cd.expiry != null) extractedExp = cd.expiry
                    if (extractedSvc == null && cd.serviceCode != null) extractedSvc = cd.serviceCode
                    if (extractedCtry == null && cd.countryCode != null) extractedCtry = cd.countryCode
                    if (extractedPsn == null && cd.psn != null) extractedPsn = cd.psn
                    if (extractedName == null && cd.holderName != null) extractedName = cd.holderName
                    if (extractedLogEntry == null && cd.logEntry != null) extractedLogEntry = cd.logEntry
                }
                
                // Also check for ATC in Record payload (Mastercard specifically)
                val recAtc = TlvUtils.findTag(readRes, "9F36")
                if (recAtc != null) {
                    extractedAtc = ApduUtils.toHexString(recAtc).toInt(16).toString()
                }
            }
        }

        // Final Print
        sb.append("   💳 PAN: ${extractedPan ?: "NOT FOUND"}\n")
        sb.append("   📅 Exp: ${extractedExp ?: "NOT FOUND"}\n")
        sb.append("   👤 Name: ${extractedName ?: "NOT FOUND"}\n")
        
        if (extractedSvc != null) {
            val desc = MetaUtils.getServiceCodeDescription(extractedSvc)
            sb.append("   🔒 Svc: $extractedSvc ($desc)\n")
        } else sb.append("   🔒 Svc: NOT FOUND\n")

        if (extractedCtry != null) {
            val cName = MetaUtils.getCountryName(extractedCtry)
            sb.append("   🌏 Ctry: $extractedCtry ($cName)\n")
        } else sb.append("   🌏 Ctry: NOT FOUND\n")

        sb.append("   🔢 PSN: ${extractedPsn ?: "NOT FOUND"}\n")
        sb.append("   💸 Log Entry: ${extractedLogEntry ?: "NOT FOUND"}\n")
        sb.append("   #️⃣ ATC: ${extractedAtc?.plus(" (Transactions)") ?: "NOT FOUND"}\n")
        
        return sb.toString()
    }
    
    private fun processManualCommand(isoDep: IsoDep, command: String): String {
        val sb = StringBuilder()
        val cmdHex = command.replace(" ", "")
        sb.append("Tx: $cmdHex\n")
        
        val result = transceive(isoDep, cmdHex)
        val sw = ApduUtils.getStatusWord(result)
        val swDesc = ApduUtils.getSWDescription(sw)
        val ascii = ApduUtils.toAsciiString(result)
        val label = TlvUtils.findTag(result, "50")

        sb.append("Rx: ${ApduUtils.toHexString(result)}\n")
        sb.append("SW: $sw ($swDesc)\n")
        if (label != null) sb.append("Label: ${ApduUtils.toAsciiString(label)}\n")
        else if (ascii.isNotEmpty()) sb.append("TXT: $ascii\n")
        
        return sb.toString()
    }
    
    private fun transceive(isoDep: IsoDep, hexCommand: String): ByteArray {
        val cmd = ApduUtils.hexStringToByteArray(hexCommand)
        var response = isoDep.transceive(cmd)
        
        // Handle 61xx (More Data Available)
        while (response.size >= 2 && response[response.size - 2] == 0x61.toByte()) {
            val len = response[response.size - 1]
            // 00 C0 00 00 <length>
            val getResponse = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, len)
            response = isoDep.transceive(getResponse)
        }
        return response
    }
}
