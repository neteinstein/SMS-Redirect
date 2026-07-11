package com.neteinstein.smsredirect.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sendWhileRoaming: Boolean,
    showNotifications: Boolean,
    forwardPrefix: String,
    defaultPrefix: String,
    currentLanguageLabel: String,
    onSendWhileRoamingChanged: (Boolean) -> Unit,
    onShowNotificationsChanged: (Boolean) -> Unit,
    onForwardPrefixSaved: (String) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LanguageRow(
                currentLanguageLabel = currentLanguageLabel,
                onClick = onOpenLanguageSettings,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingSwitchRow(
                icon = Icons.Default.SignalCellularAlt,
                title = stringResource(R.string.settings_send_while_roaming),
                checked = sendWhileRoaming,
                onCheckedChange = onSendWhileRoamingChanged,
            )

            SettingSwitchRow(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.settings_show_notifications),
                checked = showNotifications,
                onCheckedChange = onShowNotificationsChanged,
            )

            ForwardPrefixRow(
                currentPrefix = forwardPrefix,
                onSave = onForwardPrefixSaved,
            )

            DefaultPrefixDropdown(
                selectedPrefix = defaultPrefix,
                onPrefixSelected = onDefaultPrefixChanged,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onExportRules,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_export_rules),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(
                    onClick = onImportRules,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_import_rules),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardPrefixRow(
    currentPrefix: String,
    onSave: (String) -> Unit,
) {
    var text by remember(currentPrefix) { mutableStateOf(currentPrefix) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.settings_forward_prefix_label)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Tag, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = { onSave(text) },
            enabled = text != currentPrefix,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.settings_forward_prefix_save),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultPrefixDropdown(
    selectedPrefix: String,
    onPrefixSelected: (String) -> Unit,
) {
    val options =
        listOf(
            "" to stringResource(R.string.settings_prefix_none),
            "+351" to stringResource(R.string.settings_prefix_portugal),
        )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        options.firstOrNull { it.first == selectedPrefix }?.second
            ?: stringResource(R.string.settings_prefix_portugal)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_default_prefix_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPrefixSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    currentLanguageLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = currentLanguageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.action_open_screen),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
