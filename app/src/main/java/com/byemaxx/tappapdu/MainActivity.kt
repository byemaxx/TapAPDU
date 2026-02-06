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
    private val _isAutoMode = mutableStateOf(false)

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
                                    // Generic GPO 
                                    val gpoCmd = "80A8000002830000" 
                                    val gpoRes = transceive(it, gpoCmd)
                                    
                                    // Just try reading anyway, some cards respond even without GPO or if GPO fails
                                    var dataFound = false
                                    val sfiList = listOf(1, 2)
                                    val recordList = listOf(1, 2, 3) // Read a bit more
                                    
                                    for (sfi in sfiList) {
                                        for (rec in recordList) {
                                            if (dataFound) break
                                            val p1 = rec 
                                            val p2 = (sfi shl 3) or 4 
                                            val readCmd = "00B2${String.format("%02X", p1)}${String.format("%02X", p2)}00"
                                            
                                            val readRes = transceive(it, readCmd)
                                            if (ApduUtils.getStatusWord(readRes) == "9000") {
                                                val cardData = TlvUtils.extractCardData(readRes)
                                                if (cardData != null) {
                                                    sb.append("   💳 PAN: ${cardData.pan}\n")
                                                    sb.append("   📅 Exp: ${cardData.expiry}\n")
                                                    
                                                    if (cardData.serviceCode != null) {
                                                        val desc = MetaUtils.getServiceCodeDescription(cardData.serviceCode)
                                                        sb.append("   🔒 Svc: ${cardData.serviceCode} ($desc)\n")
                                                    }
                                                    
                                                    if (cardData.countryCode != null) {
                                                        val cName = MetaUtils.getCountryName(cardData.countryCode)
                                                        sb.append("   🌏 Ctry: ${cardData.countryCode} ($cName)\n")
                                                    }
                                                    
                                                    if (cardData.psn != null) sb.append("   🔢 PSN: ${cardData.psn}\n")
                                                    dataFound = true
                                                }
                                                // Check for ATC in records too if needed
                                            }
                                        }
                                    }
                                    
                                    // Also check GPO response itself for ATC
                                    val atc = TlvUtils.findTag(gpoRes, "9F36")
                                    if (atc != null) {
                                         val atcVal = ApduUtils.toHexString(atc).toInt(16)
                                         sb.append("   📊 ATC: $atcVal (Transactions)\n")
                                    }
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
         return isoDep.transceive(cmd)
    }
}

data class CardData(
    val pan: String,
    val expiry: String,
    val serviceCode: String?,
    val countryCode: String?, // Tag 5F28
    val psn: String?          // Tag 5F34
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
    fun findTag(data: ByteArray, tagHex: String): ByteArray? {
        val hexStr = ApduUtils.toHexString(data)
        
        val tagIdx = hexStr.indexOf(tagHex)
        if (tagIdx == -1) return null
        
        var i = 0
        while (i < data.size) {
             if (data[i] == ApduUtils.hexStringToByteArray(tagHex)[0]) {
                 val tagLenInBytes = tagHex.length / 2
                 if (i + tagLenInBytes >= data.size) return null
                 
                 var match = true
                 for(j in 0 until tagLenInBytes) {
                     if (data[i+j] != ApduUtils.hexStringToByteArray(tagHex)[j]) match = false
                 }
                 
                 if (match) {
                     var lenIdx = i + tagLenInBytes
                     if (lenIdx >= data.size) return null
                     var length = data[lenIdx].toInt() and 0xFF
                     
                     if (length == 0x81) {
                         lenIdx++
                         length = data[lenIdx].toInt() and 0xFF
                     }
                     
                     val startVal = lenIdx + 1
                     if (startVal + length <= data.size) {
                         return data.copyOfRange(startVal, startVal + length)
                     }
                 }
             }
             i++
        }
        return null
    }
    
    fun extractCardData(data: ByteArray): CardData? {
        var pan: String? = null
        var expiry: String = "Unknown"
        var serviceCode: String? = null
        
        // Priority 1: Tag 5A (PAN)
        val panBytes = findTag(data, "5A")
        if (panBytes != null) {
            val panRaw = ApduUtils.toHexString(panBytes)
            pan = panRaw.trimEnd('F')
            val expBytes = findTag(data, "5F24") // Application Expiration Date
            if(expBytes != null) expiry = ApduUtils.toHexString(expBytes)
        }
        
        // Priority 2: Tag 57 (Track 2 Equiv)
        val track2Bytes = findTag(data, "57") 
        if (track2Bytes != null) {
             val t2 = ApduUtils.toHexString(track2Bytes)
             val separator = t2.indexOf('D')
             if (separator != -1) {
                 if (pan == null) pan = t2.substring(0, separator)
                 val expStart = separator + 1
                 if (expStart + 4 <= t2.length) {
                     val t2Exp = t2.substring(expStart, expStart + 4)
                     if (expiry == "Unknown") expiry = t2Exp
                     
                     val svcStart = expStart + 4
                     if (svcStart + 3 <= t2.length) {
                         serviceCode = t2.substring(svcStart, svcStart + 3)
                     }
                 }
             }
        }
        
        if (pan != null) {
            val countryBytes = findTag(data, "5F28")
            val countryCode = if (countryBytes != null) ApduUtils.toHexString(countryBytes) else null
            
            val psnBytes = findTag(data, "5F34")
            val psn = if (psnBytes != null) ApduUtils.toHexString(psnBytes) else null
            
            return CardData(pan, expiry, serviceCode, countryCode, psn)
        }
        return null
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
