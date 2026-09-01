package com.neteinstein.smsredirect.presentation.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.presentation.components.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    needsCredentials: Boolean,
    onSubjectTemplateSaved: (String) -> Unit,
    onGmailLabelSaved: (String) -> Unit,
    onBackupPreviousChanged: (Boolean) -> Unit,
    onToggleBackupEnabled: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    showBackgroundWarning: Boolean = false,
    onFixBackgroundWarning: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_cd_settings),
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (showBackgroundWarning) {
                WarningBanner(
                    message = stringResource(R.string.battery_banner_message),
                    actionLabel = stringResource(R.string.battery_banner_action),
                    onActionClick = onFixBackgroundWarning,
                )
            }

            if (!state.hasCredentials) {
                NoCredentialsState(onOpenSettings = onOpenSettings)
            } else {
                BackupConfiguration(
                    state = state,
                    needsCredentials = needsCredentials,
                    onSubjectTemplateSaved = onSubjectTemplateSaved,
                    onGmailLabelSaved = onGmailLabelSaved,
                    onBackupPreviousChanged = onBackupPreviousChanged,
                    onToggleBackupEnabled = onToggleBackupEnabled,
                )
            }
        }
    }
}

@Composable
private fun NoCredentialsState(onOpenSettings: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.backup_no_credentials_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.backup_no_credentials_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.backup_no_credentials_context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.backup_open_settings))
        }
    }
}

@Composable
private fun BackupConfiguration(
    state: BackupUiState,
    needsCredentials: Boolean,
    onSubjectTemplateSaved: (String) -> Unit,
    onGmailLabelSaved: (String) -> Unit,
    onBackupPreviousChanged: (Boolean) -> Unit,
    onToggleBackupEnabled: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        EditableSettingRow(
            current = state.settings.subjectTemplate,
            label = stringResource(R.string.backup_subject_label),
            hint = stringResource(R.string.backup_subject_hint),
            saveContentDescription = stringResource(R.string.backup_subject_save),
            icon = Icons.Default.Subject,
            onSave = onSubjectTemplateSaved,
        )

        EditableSettingRow(
            current = state.settings.gmailLabel,
            label = stringResource(R.string.backup_label_label),
            hint = stringResource(R.string.backup_label_hint),
            saveContentDescription = stringResource(R.string.backup_label_save),
            icon = Icons.Default.Label,
            onSave = onGmailLabelSaved,
        )

        HorizontalDivider()

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
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.backup_previous_label),
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            Switch(
                checked = state.settings.backupPreviousEnabled,
                onCheckedChange = onBackupPreviousChanged,
            )
        }

        HorizontalDivider()

        EnableBackupButton(
            enabled = state.settings.backupEnabled,
            onClick = onToggleBackupEnabled,
        )

        Text(
            text = stringResource(R.string.backup_enable_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (needsCredentials) {
            Text(
                text = stringResource(R.string.backup_enable_needs_credentials),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        state.progress?.let { progress ->
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.backup_progress, progress.done, progress.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = {
                        if (progress.total <= 0) 0f else progress.done.toFloat() / progress.total.toFloat()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EnableBackupButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        colors =
            if (enabled) {
                // colorScheme.secondary is the brand green (0xFF188038) and adapts to dark mode,
                // unlike a hardcoded colour.
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
    ) {
        if (enabled) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.backup_cd_enabled),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text =
                if (enabled) {
                    stringResource(R.string.backup_enabled)
                } else {
                    stringResource(R.string.backup_enable)
                },
        )
    }
}

/** Matches the ForwardPrefixRow pattern in SettingsScreen: edit freely, commit with the check. */
@Composable
private fun EditableSettingRow(
    current: String,
    label: String,
    hint: String,
    saveContentDescription: String,
    icon: ImageVector,
    onSave: (String) -> Unit,
) {
    var text by remember(current) { mutableStateOf(current) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { onSave(text) },
                enabled = text != current && text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = saveContentDescription,
                )
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
