package com.neteinstein.smsredirect.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

/** The "Updates" section at the top of Settings in the GitHub build. */
@Composable
fun UpdatesSection(
    state: AppUpdateUiState,
    onCheckNow: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleaseNotes: (String) -> Unit,
    onOpenInstallGuide: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_updates_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_updates_current_version, state.currentVersionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val update = state.availableUpdate
        if (update != null) {
            Text(
                text = stringResource(R.string.settings_updates_available, update.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when (val install = state.installStatus) {
                is UpdateInstallStatus.Downloading -> {
                    Text(
                        text =
                            install.percent?.let { stringResource(R.string.settings_updates_downloading_percent, it) }
                                ?: stringResource(R.string.settings_updates_downloading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val percent = install.percent
                    if (percent == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    StatusText(install)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall) {
                            Text(stringResource(R.string.settings_updates_install))
                        }
                        if (update.releaseUrl.isNotBlank()) {
                            OutlinedButton(onClick = { onOpenReleaseNotes(update.releaseUrl) }) {
                                Text(stringResource(R.string.settings_updates_release_notes))
                            }
                        }
                    }
                }
            }
        } else {
            val message =
                when (state.checkStatus) {
                    UpdateCheckStatus.Idle -> null
                    UpdateCheckStatus.Checking -> stringResource(R.string.settings_updates_checking)
                    UpdateCheckStatus.UpToDate -> stringResource(R.string.settings_updates_up_to_date)
                    UpdateCheckStatus.Failed -> stringResource(R.string.settings_updates_check_failed)
                }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (state.checkStatus == UpdateCheckStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            OutlinedButton(
                onClick = onCheckNow,
                enabled = state.checkStatus != UpdateCheckStatus.Checking,
            ) {
                if (state.checkStatus == UpdateCheckStatus.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_updates_check_now))
                }
            }
        }

        TextButton(onClick = onOpenInstallGuide) {
            Text(stringResource(R.string.settings_updates_install_guide))
        }
    }
}

@Composable
private fun StatusText(install: UpdateInstallStatus) {
    val (text, color) =
        when (install) {
            UpdateInstallStatus.InstallerOpened -> {
                stringResource(R.string.settings_updates_installer_opened) to MaterialTheme.colorScheme.onSurfaceVariant
            }

            UpdateInstallStatus.NeedsPermission -> {
                stringResource(R.string.settings_updates_needs_permission) to MaterialTheme.colorScheme.error
            }

            UpdateInstallStatus.Failed -> {
                stringResource(R.string.settings_updates_failed) to MaterialTheme.colorScheme.error
            }

            else -> {
                null to Color.Unspecified
            }
        }
    if (text != null) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
