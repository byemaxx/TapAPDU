package com.byemaxx.tappapdu

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.byemaxx.tappapdu.constants.CommandPresets
import com.byemaxx.tappapdu.nfc.NfcHandler
import com.byemaxx.tappapdu.model.GpoConfig
import com.byemaxx.tappapdu.ui.ApduSender
import com.byemaxx.tappapdu.ui.theme.TapAPDUTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val nfcHandler = NfcHandler()
    
    private val _log = mutableStateOf("")
    private val _apduCommand = mutableStateOf(CommandPresets.PPSE) // Default PPSE
    private val _isAutoMode = mutableStateOf(true)
    private val _gpoConfig = mutableStateOf(GpoConfig())

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
                                                gpoConfig = _gpoConfig.value,
                        onCommandChange = { _apduCommand.value = it },
                        onAutoModeChange = { _isAutoMode.value = it },
                        onClearLog = { _log.value = "" },
                        onGpoConfigChange = { _gpoConfig.value = it }
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
        val result = nfcHandler.processTag(tag, _isAutoMode.value, _apduCommand.value, _gpoConfig.value)
        runOnUiThread {
            _log.value = result + "\n" + _log.value
        }
    }
}
