package com.neteinstein.smsredirect.presentation.restore

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.SmsRestoreFailure
import com.neteinstein.smsredirect.domain.model.SmsRestoreState

/** The Restore tab: copies the SMS backed up in the user's Gmail label back to this phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    state: RestoreUiState,
    defaultSmsRoleDenied: Boolean,
    onRestoreConfirmed: () -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreResultDismissed: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    /** Shown above the content, e.g. the "update available" banner. */
    topBanner: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.home_cd_settings))
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
            topBanner()
            if (!state.hasCredentials) {
                NoCredentialsState(onOpenSettings)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    RestoreCard(
                        state = state,
                        defaultSmsRoleDenied = defaultSmsRoleDenied,
                        onRestoreConfirmed = onRestoreConfirmed,
                        onCancelRestore = onCancelRestore,
                        onRestoreResultDismissed = onRestoreResultDismissed,
                    )
                }
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
            text = stringResource(R.string.restore_no_credentials_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.backup_no_credentials_restore_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.backup_open_settings))
        }
    }
}

@Composable
private fun RestoreCard(
    state: RestoreUiState,
    defaultSmsRoleDenied: Boolean,
    onRestoreConfirmed: () -> Unit,
    onCancelRestore: () -> Unit,
    onRestoreResultDismissed: () -> Unit,
) {
    // Local UI state only: whether the explanation dialog is open.
    var showConfirmation by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.restore_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            when (val restore = state.restoreState) {
                SmsRestoreState.Idle -> {
                    IdleContent(
                        label = state.settings.gmailLabel,
                        defaultSmsRoleDenied = defaultSmsRoleDenied,
                        onStart = { showConfirmation = true },
                    )
                }

                is SmsRestoreState.Running -> {
                    RunningContent(restore, onCancelRestore)
                }

                is SmsRestoreState.Finished -> {
                    FinishedContent(result = restore, onDone = onRestoreResultDismissed)
                }

                is SmsRestoreState.Failed -> {
                    FailedContent(
                        failure = restore.reason,
                        label = state.settings.gmailLabel,
                        onRetry = { showConfirmation = true },
                        onDismiss = onRestoreResultDismissed,
                    )
                }
            }
        }
    }

    if (showConfirmation) {
        RestoreConfirmationDialog(
            label = state.settings.gmailLabel,
            alreadyDefault = state.isDefaultSmsApp,
            onConfirm = {
                showConfirmation = false
                onRestoreConfirmed()
            },
            onDismiss = { showConfirmation = false },
        )
    }
}

@Composable
private fun IdleContent(
    label: String,
    defaultSmsRoleDenied: Boolean,
    onStart: () -> Unit,
) {
    Text(
        text = stringResource(R.string.restore_description, label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (defaultSmsRoleDenied) {
        Text(
            text = stringResource(R.string.restore_role_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.restore_start))
    }
}

@Composable
private fun RunningContent(
    running: SmsRestoreState.Running,
    onCancel: () -> Unit,
) {
    val progress = running.progress
    if (progress == null) {
        Text(text = stringResource(R.string.restore_preparing), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            text = stringResource(R.string.restore_progress, progress.processed, progress.total),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { if (progress.total <= 0) 0f else progress.processed.toFloat() / progress.total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = pluralStringResource(R.plurals.restore_progress_restored, progress.restored, progress.restored),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = stringResource(R.string.restore_running_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_stop)) }
}

@Composable
private fun FinishedContent(
    result: SmsRestoreState.Finished,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.restore_result_restored, result.restored, result.restored),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (result.alreadyOnDevice > 0) {
        Text(
            text = pluralStringResource(R.plurals.restore_result_already_on_device, result.alreadyOnDevice, result.alreadyOnDevice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (result.notBackups > 0) {
        Text(
            text = pluralStringResource(R.plurals.restore_result_not_backups, result.notBackups, result.notBackups),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (result.failed > 0) {
        Text(
            text = pluralStringResource(R.plurals.restore_result_failed, result.failed, result.failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = onDone) { Text(stringResource(R.string.restore_done)) }
}

@Composable
private fun FailedContent(
    failure: SmsRestoreFailure,
    label: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text =
                when (failure) {
                    SmsRestoreFailure.NOT_DEFAULT_SMS_APP -> stringResource(R.string.restore_failed_not_default)
                    SmsRestoreFailure.AUTH_FAILED -> stringResource(R.string.restore_failed_auth)
                    SmsRestoreFailure.LABEL_NOT_FOUND -> stringResource(R.string.restore_failed_label_not_found, label)
                    SmsRestoreFailure.NETWORK -> stringResource(R.string.restore_failed_network)
                    SmsRestoreFailure.NOT_CONFIGURED -> stringResource(R.string.restore_failed_not_configured)
                },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Text(
        text = stringResource(R.string.restore_failed_safe_to_retry),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.restore_try_again)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.restore_dismiss)) }
    }
}

@Composable
private fun RestoreConfirmationDialog(
    label: String,
    alreadyDefault: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Default.Restore, contentDescription = null) },
        title = { Text(stringResource(R.string.restore_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.restore_dialog_intro, label))
                if (!alreadyDefault) {
                    Step(1, stringResource(R.string.restore_dialog_step_role))
                    Step(2, stringResource(R.string.restore_dialog_step_restore))
                } else {
                    Text(stringResource(R.string.restore_dialog_step_restore))
                }
                Text(
                    text = stringResource(R.string.restore_dialog_mms_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (alreadyDefault) R.string.restore_dialog_confirm_start else R.string.restore_dialog_confirm,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.restore_dialog_cancel)) } },
    )
}

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 3.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
