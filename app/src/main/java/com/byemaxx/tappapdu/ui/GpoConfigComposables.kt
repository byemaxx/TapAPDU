package com.byemaxx.tappapdu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.byemaxx.tappapdu.model.CurrencyCodes
import com.byemaxx.tappapdu.model.GpoConfig
import com.byemaxx.tappapdu.model.TerminalCapabilitiesPresets
import com.byemaxx.tappapdu.model.TerminalTypePresets
import com.byemaxx.tappapdu.model.CountryCodePresets
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpoConfigSection(
    config: GpoConfig,
    onConfigChange: (GpoConfig) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val expandedScrollState = rememberScrollState()
    
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with expand button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GPO Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onConfigChange(GpoConfig()) },
                        enabled = enabled
                    ) {
                        Text("Reset")
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
            }
            
            // Expandable content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(expandedScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Amount
                    OutlinedTextField(
                        value = if (config.amount == 0L) "" else (config.amount / 100.0).toString(),
                        onValueChange = { 
                            val amount = it.toDoubleOrNull()?.times(100)?.toLong() ?: 0L
                            onConfigChange(config.copy(amount = amount))
                        },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = enabled,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp),
                        suffix = { Text(CurrencyCodes.currencies[config.currencyCode]?.split(" ")?.get(0) ?: "") }
                    )
                    
                    // Currency dropdown
                    var currencyExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = currencyExpanded,
                        onExpandedChange = { if (enabled) currencyExpanded = !currencyExpanded }
                    ) {
                        OutlinedTextField(
                            value = CurrencyCodes.currencies[config.currencyCode] ?: config.currencyCode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Currency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
                            enabled = enabled,
                            textStyle = TextStyle(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            CurrencyCodes.currencies.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onConfigChange(config.copy(
                                            currencyCode = code,
                                            countryCode = code // Keep country aligned with currency
                                        ))
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Transaction Type dropdown
                    var txTypeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = txTypeExpanded,
                        onExpandedChange = { if (enabled) txTypeExpanded = !txTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = CurrencyCodes.transactionTypes[config.transactionType] ?: config.transactionType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Transaction Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = txTypeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
                            enabled = enabled,
                            textStyle = TextStyle(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = txTypeExpanded,
                            onDismissRequest = { txTypeExpanded = false }
                        ) {
                            CurrencyCodes.transactionTypes.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onConfigChange(config.copy(transactionType = code))
                                        txTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Advanced options toggle
                    var showAdvanced by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        enabled = enabled
                    ) {
                        Text(if (showAdvanced) "Hide advanced options" else "Show advanced options")
                    }
                    
                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            
                            // Terminal Type with dropdown and manual input
                            var terminalTypeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = terminalTypeExpanded,
                                onExpandedChange = { if (enabled) terminalTypeExpanded = !terminalTypeExpanded }
                            ) {
                                OutlinedTextField(
                                    value = config.terminalType,
                                    onValueChange = { 
                                        if (it.length <= 2) onConfigChange(config.copy(terminalType = it))
                                    },
                                    label = { Text("Terminal Type (9F35)") },
                                    placeholder = { Text(TerminalTypePresets.types[config.terminalType] ?: "Manual") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = terminalTypeExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
                                    enabled = enabled,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = terminalTypeExpanded,
                                    onDismissRequest = { terminalTypeExpanded = false }
                                ) {
                                    TerminalTypePresets.types.forEach { (code, description) ->
                                        DropdownMenuItem(
                                            text = { Text("$code - $description") },
                                            onClick = {
                                                onConfigChange(config.copy(terminalType = code))
                                                terminalTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Terminal Capabilities with dropdown and manual input
                            var capabilitiesExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = capabilitiesExpanded,
                                onExpandedChange = { if (enabled) capabilitiesExpanded = !capabilitiesExpanded }
                            ) {
                                OutlinedTextField(
                                    value = config.terminalCapabilities,
                                    onValueChange = { 
                                        if (it.length <= 6) onConfigChange(config.copy(terminalCapabilities = it))
                                    },
                                    label = { Text("Terminal Capabilities (9F33)") },
                                    placeholder = { Text(TerminalCapabilitiesPresets.capabilities[config.terminalCapabilities] ?: "Manual") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capabilitiesExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
                                    enabled = enabled,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = capabilitiesExpanded,
                                    onDismissRequest = { capabilitiesExpanded = false }
                                ) {
                                    TerminalCapabilitiesPresets.capabilities.forEach { (code, description) ->
                                        DropdownMenuItem(
                                            text = { Text("$code - $description") },
                                            onClick = {
                                                onConfigChange(config.copy(terminalCapabilities = code))
                                                capabilitiesExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Country Code with dropdown and manual input
                            var countryExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = countryExpanded,
                                onExpandedChange = { if (enabled) countryExpanded = !countryExpanded }
                            ) {
                                OutlinedTextField(
                                    value = config.countryCode,
                                    onValueChange = { 
                                        if (it.length <= 4) onConfigChange(config.copy(countryCode = it))
                                    },
                                    label = { Text("Country Code (9F1A)") },
                                    placeholder = { Text(CountryCodePresets.countries[config.countryCode] ?: "Manual") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
                                    enabled = enabled,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = countryExpanded,
                                    onDismissRequest = { countryExpanded = false }
                                ) {
                                    CountryCodePresets.countries.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text("$code - $name") },
                                            onClick = {
                                                onConfigChange(config.copy(countryCode = code))
                                                countryExpanded = false
                                            }
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
