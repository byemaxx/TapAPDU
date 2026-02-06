package com.byemaxx.tappapdu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byemaxx.tappapdu.constants.CommandPresets
import com.byemaxx.tappapdu.model.GpoConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApduSender(
    log: String,
    apduCommand: String,
    isAutoMode: Boolean,
        gpoConfig: GpoConfig,
    onCommandChange: (String) -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onClearLog: () -> Unit,
    onGpoConfigChange: (GpoConfig) -> Unit
) {
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
            ConfigurationCard(
                apduCommand = apduCommand,
                isAutoMode = isAutoMode,
                                gpoConfig = gpoConfig,
                onCommandChange = onCommandChange,
                onAutoModeChange = onAutoModeChange,
                onGpoConfigChange = onGpoConfigChange
            )

            // Log Section
            LogCard(
                log = log,
                onClearLog = onClearLog,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun ConfigurationCard(
        gpoConfig: GpoConfig,
    apduCommand: String,
    isAutoMode: Boolean,
    onCommandChange: (String) -> Unit,
    onAutoModeChange: (Boolean) -> Unit,
    onGpoConfigChange: (GpoConfig) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header + Auto Switch
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

            // Input Field (Manual Mode only)
            if (!isAutoMode) {
                OutlinedTextField(
                    value = apduCommand,
                    onValueChange = onCommandChange,
                    label = { Text("APDU Hex Command") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Presets (Manual Mode)
            if (!isAutoMode) {
                PresetsSection(
                    isAutoMode = isAutoMode,
                    onCommandChange = onCommandChange,
                    onAutoModeChange = onAutoModeChange
                )
            }

            // GPO Configuration (only visible in Auto Mode)
            if (isAutoMode) {
                GpoConfigSection(
                    config = gpoConfig,
                    onConfigChange = onGpoConfigChange,
                    enabled = true
                )
            }
        }
    }
}

@Composable
fun PresetsSection(
    isAutoMode: Boolean,
    onCommandChange: (String) -> Unit,
    onAutoModeChange: (Boolean) -> Unit
) {
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
            val presets = CommandPresets.knownCommands
            
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

@Composable
fun LogCard(
    log: String,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
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
