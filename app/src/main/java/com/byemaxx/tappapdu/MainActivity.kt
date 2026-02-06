package com.byemaxx.tappapdu

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byemaxx.tappapdu.ui.theme.TapAPDUTheme
import java.io.IOException

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val _log = mutableStateOf("")
    private val _apduCommand = mutableStateOf("00A404000E325041592E5359532E444446303100") // Default PPSE
    private val _isAutoMode = mutableStateOf(true)

    // Knowledge Base for Command Presets
    private val knownCommands = listOf(
        "PPSE" to "00A404000E325041592E5359532E444446303100",
        "Mastercard" to "00A4040007A000000004101000",
        "Visa" to "00A4040007A000000003101000",
        "Amex" to "00A4040005A00000002500",
        "UnionPay" to "00A4040007A000000333010100",
        "Discover" to "00A4040007A000000152301000",
        "JCB" to "00A4040007A000000065101000"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            TapAPDUTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ApduSender(
                        log = _log.value,
                        apduCommand = _apduCommand.value,
                        isAutoMode = _isAutoMode.value,
                        onCommandChange = { _apduCommand.value = it },
                        onAutoModeChange = { _isAutoMode.value = it },
                        onClearLog = { _log.value = "" }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag)
        isoDep?.let {
            try {
                it.connect()
                it.timeout = 5000 
                
                val sb = StringBuilder()
                sb.append("--- New Session ---\n")

                if (_isAutoMode.value) {
                    sb.append("Starting Auto Scan...\n")
                    var foundAny = false
                    for ((name, cmdHex) in knownCommands) {
                        try {
                            val result = transceive(it, cmdHex)
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
                                    // Dynamic GPO (PDOL Handling)
                                    val pdol = TlvUtils.findTag(result, "9F38")
                                    val gpoCmd = TlvUtils.constructGpoCommand(pdol)
                                    val gpoRes = transceive(it, gpoCmd)
                                    
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
                                        val readRes = transceive(it, readCmd)
                                        
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

                } else {
                    // Manual Mode
                    val cmdHex = _apduCommand.value.replace(" ", "")
                    sb.append("Tx: $cmdHex\n")
                    val result = transceive(it, cmdHex)
                    val sw = ApduUtils.getStatusWord(result)
                    val swDesc = ApduUtils.getSWDescription(sw)
                    val ascii = ApduUtils.toAsciiString(result)
                    val label = TlvUtils.findTag(result, "50")

                    sb.append("Rx: ${ApduUtils.toHexString(result)}\n")
                    sb.append("SW: $sw ($swDesc)\n")
                    if (label != null) sb.append("Label: ${ApduUtils.toAsciiString(label)}\n")
                    else if (ascii.isNotEmpty()) sb.append("TXT: $ascii\n")
                }

                runOnUiThread {
                    _log.value = sb.toString() + "\n" + _log.value
                }
                it.close()
            } catch (e: IOException) {
                runOnUiThread {
                    _log.value = "Connection Error: ${e.message}\n${_log.value}"
                }
            }
        }
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

data class CardData(
    val pan: String?,
    val expiry: String?,
    val serviceCode: String?,
    val countryCode: String?, // Tag 5F28
    val psn: String?,         // Tag 5F34
    val holderName: String?,  // Tag 5F20
    val logEntry: String?     // Tag 9F4D
)

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
        if (pdolRaw == null) return "80A8000002830000" // Default generic GPO

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
                val valHex = when(tagHex) {
                    "9F66" -> { // Terminal Transaction Qualifiers (TTQ)
                        // 28 00 00 00 = qVSDC supported, MSD supported, Contactless
                         if (len == 4) "28000000" else "00".repeat(len) 
                    }
                    "9A" -> { // Transaction Date YYMMDD
                         val date = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date())
                         if (len == 3) date else "00".repeat(len)
                    }
                    "9F37" -> { // Unpredictable Number
                         val rnd = java.util.Random()
                         val b = ByteArray(len)
                         rnd.nextBytes(b)
                         ApduUtils.toHexString(b)
                    }
                    "9F1A", "5F2A" -> { // Country Code / Currency Code
                         if (len == 2) "0840" else "00".repeat(len) // USA / USD
                    }
                    else -> "00".repeat(len)
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

object ApduUtils {
    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun toHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    fun getStatusWord(bytes: ByteArray): String {
        if (bytes.size < 2) return ""
        return toHexString(byteArrayOf(bytes[bytes.size - 2], bytes[bytes.size - 1]))
    }

    fun getSWDescription(sw: String): String {
        return when (sw) {
            "9000" -> "Success (OK)"
            "6A82" -> "File Not Found"
            "6700" -> "Wrong Length"
            "6E00" -> "CLA Not Supported"
            "6D00" -> "INS Not Supported"
            "6985" -> "Conditions Not Satisfied"
            "6A81" -> "Function not supported"
            "6A83" -> "Record not found"
            else -> {
                if (sw.startsWith("61")) "More Data Available (${sw.substring(2)} bytes)"
                else if (sw.startsWith("6C")) "Wrong Length (Try Le=${sw.substring(2)})"
                else "Unknown"
            }
        }
    }

    fun toAsciiString(bytes: ByteArray): String {
        val sb = StringBuilder()
        val limit = if(bytes.size >= 2) bytes.size - 2 else bytes.size
        
        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                sb.append('.')
            }
        }
        return sb.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApduSender(
    log: String,
    apduCommand: String,
    isAutoMode: Boolean,
    onCommandChange: (String) -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onClearLog: () -> Unit
) {
    // Presets
    val ppseCommand = "00A404000E325041592E5359532E444446303100"
    val mastercardCommand = "00A4040007A000000004101000"
    val visaCommand = "00A4040007A000000003101000"
    val amexCommand = "00A4040005A00000002500"
    val unionPayCommand = "00A4040007A000000333010100"
    val discoverCommand = "00A4040007A000000152301000"
    val jcbCommand = "00A4040007A000000065101000"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("APDU Tester", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Section
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header + Auto Switch loop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAutoMode) "Auto Scan" else "Manual",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(checked = isAutoMode, onCheckedChange = onAutoModeChange)
                        }
                    }

                    // Input Field (Disabled in Auto Mode)
                    OutlinedTextField(
                        value = apduCommand,
                        onValueChange = onCommandChange,
                        label = { Text("APDU Hex Command") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        enabled = !isAutoMode,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)
                        )
                    )

                    // Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Presets (Manual Mode):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                "PPSE" to ppseCommand,
                                "Mastercard" to mastercardCommand,
                                "Visa" to visaCommand,
                                "Amex" to amexCommand,
                                "UnionPay" to unionPayCommand,
                                "Discover" to discoverCommand,
                                "JCB" to jcbCommand
                            )
                            
                            presets.forEach { (name, cmd) ->
                                OutlinedButton(
                                    onClick = { 
                                        onCommandChange(cmd)
                                        if (isAutoMode) onAutoModeChange(false) // Switch to manual if user clicks a preset
                                    },
                                    enabled = !isAutoMode || isAutoMode 
                                ) { Text(name) }
                            }
                        }
                    }
                }
            }

            // Log Section
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Transaction Log",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onClearLog) {
                            Text("Clear")
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    if (log.isEmpty()) {
                                        Text(
                                            text = "Ready to scan...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    } else {
                                        Text(
                                            text = log,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
