package com.neteinstein.smsredirect.presentation.history

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    forwardHistory: List<ForwardHistory>,
    contactNames: Map<String, String>,
    keepHistory: Boolean,
    showDisableConfirmation: Boolean,
    onKeepHistoryChanged: (Boolean) -> Unit,
    onConfirmDisable: () -> Unit,
    onDismissDisable: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_keep_history))
                Switch(
                    checked = keepHistory,
                    onCheckedChange = onKeepHistoryChanged,
                )
            }

            if (forwardHistory.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sms_scheduler_illustration),
                        contentDescription = stringResource(R.string.history_no_scheduled_sms_illustration_cd),
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.settings_no_forwarded_sms))
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(forwardHistory, key = { it.id }) { item ->
                        ForwardHistoryCard(
                            item = item,
                            senderContactName = contactNames[item.sender],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardHistoryCard(
    item: ForwardHistory,
    senderContactName: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val senderDisplay =
                if (item.sender.isBlank()) {
                    stringResource(R.string.settings_history_unknown_sender)
                } else if (senderContactName != null) {
                    "$senderContactName (${item.sender})"
                } else {
                    item.sender
                }
            Text(
                text =
                    stringResource(
                        R.string.settings_history_sender_target_format,
                        senderDisplay,
                        item.targetNumber,
                    ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(item.forwardedAtEpochMs)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.ruleName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_history_rule_format, item.ruleName),
                    style = MaterialTheme.typography.bodySmall,
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
