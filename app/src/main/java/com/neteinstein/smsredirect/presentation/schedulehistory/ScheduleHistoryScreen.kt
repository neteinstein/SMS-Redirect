package com.neteinstein.smsredirect.presentation.schedulehistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleHistoryScreen(
    scheduledSmsHistory: List<ScheduledSmsHistory>,
    contactNames: Map<String, String>,
    keepScheduleHistory: Boolean,
    showDisableConfirmation: Boolean,
    showClearConfirmation: Boolean,
    onKeepScheduleHistoryChanged: (Boolean) -> Unit,
    onConfirmDisable: () -> Unit,
    onDismissDisable: () -> Unit,
    onClearHistoryClicked: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
    onBack: () -> Unit,
) {
    if (showDisableConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDisable,
            title = { Text(stringResource(R.string.history_disable_confirm_title)) },
            text = { Text(stringResource(R.string.history_disable_confirm_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDisable) {
                    Text(stringResource(R.string.history_disable_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisable) {
                    Text(stringResource(R.string.history_disable_confirm_no))
                }
            },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissClear,
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text(stringResource(R.string.history_clear_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear) {
                    Text(stringResource(R.string.history_clear_confirm_no))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClearHistoryClicked,
                        enabled = scheduledSmsHistory.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.action_clear_history),
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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.schedule_history_keep_history))
                Switch(
                    checked = keepScheduleHistory,
                    onCheckedChange = onKeepScheduleHistoryChanged,
                )
            }

            if (scheduledSmsHistory.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.schedule_history_no_items),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(scheduledSmsHistory, key = { it.id }) { item ->
                        ScheduledSmsHistoryCard(
                            item = item,
                            contactName = contactNames[item.contactNumber],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledSmsHistoryCard(
    item: ScheduledSmsHistory,
    contactName: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                val contactDisplay =
                    if (item.contactNumber.isBlank()) {
                        stringResource(R.string.schedule_history_unknown_contact)
                    } else if (contactName != null) {
                        "$contactName (${item.contactNumber})"
                    } else {
                        item.contactNumber
                    }
                Text(
                    text = stringResource(R.string.schedule_history_contact_format, contactDisplay),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DateFormat.getDateTimeInstance().format(Date(item.sentAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.scheduledSmsName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.schedule_history_name_format, item.scheduledSmsName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.messageBody,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
