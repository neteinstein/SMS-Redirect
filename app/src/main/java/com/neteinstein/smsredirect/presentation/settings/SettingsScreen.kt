package com.neteinstein.smsredirect.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    missingPermissions: List<String>,
    sendWhileRoaming: Boolean,
    keepHistory: Boolean,
    defaultPrefix: String,
    forwardHistory: List<ForwardHistory>,
    onSendWhileRoamingChanged: (Boolean) -> Unit,
    onKeepHistoryChanged: (Boolean) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (missingPermissions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.missing_permissions_banner_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        missingPermissions.forEach { permission ->
                            Text(
                                text = stringResource(R.string.permission_item_format, permission),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            SettingSwitchRow(
                title = stringResource(R.string.settings_send_while_roaming),
                checked = sendWhileRoaming,
                onCheckedChange = onSendWhileRoamingChanged
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_keep_history),
                checked = keepHistory,
                onCheckedChange = onKeepHistoryChanged
            )

            DefaultPrefixDropdown(
                selectedPrefix = defaultPrefix,
                onPrefixSelected = onDefaultPrefixChanged
            )

            if (keepHistory) {
                Text(
                    text = stringResource(R.string.settings_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (forwardHistory.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_forwarded_sms),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(forwardHistory, key = { it.id }) { item ->
                            ForwardHistoryCard(item)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultPrefixDropdown(
    selectedPrefix: String,
    onPrefixSelected: (String) -> Unit
) {
    val options = listOf(
        "" to stringResource(R.string.settings_prefix_none),
        "+351" to stringResource(R.string.settings_prefix_portugal)
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedPrefix }?.second
        ?: stringResource(R.string.settings_prefix_portugal)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_default_prefix_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPrefixSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ForwardHistoryCard(item: ForwardHistory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    R.string.settings_history_sender_target_format,
                    item.sender.ifBlank { stringResource(R.string.settings_history_unknown_sender) },
                    item.targetNumber
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(item.forwardedAtEpochMs)),
                style = MaterialTheme.typography.bodySmall
            )
            if (item.ruleName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_history_rule_format, item.ruleName),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = item.messageBody,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
