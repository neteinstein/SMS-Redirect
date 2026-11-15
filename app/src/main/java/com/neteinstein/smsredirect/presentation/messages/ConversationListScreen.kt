package com.neteinstein.smsredirect.presentation.messages

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.Conversation
import com.neteinstein.smsredirect.presentation.components.WarningBanner

/** The Messages tab: every SMS/MMS conversation on the phone, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    isDefaultSmsApp: Boolean,
    onSetDefaultSmsApp: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    showBackgroundWarning: Boolean = false,
    onFixBackgroundWarning: () -> Unit = {},
    /** Shown above the content, e.g. the "update available" banner. */
    topBanner: @Composable () -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
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
            if (showBackgroundWarning && isDefaultSmsApp) {
                WarningBanner(
                    message = stringResource(R.string.battery_banner_message),
                    actionLabel = stringResource(R.string.battery_banner_action),
                    onActionClick = onFixBackgroundWarning,
                )
            }
            topBanner()
            if (!isDefaultSmsApp) {
                // Disabled, not hidden: the user sees what the tab is for and how to turn it on.
                WarningBanner(
                    message = stringResource(R.string.messages_disabled_banner),
                    actionLabel = stringResource(R.string.default_sms_banner_action),
                    onActionClick = onSetDefaultSmsApp,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(DISABLED_ALPHA),
                ) {
                    if (state.loaded && state.conversations.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.conversations, key = { it.threadId }) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    contactNames = state.contactNames,
                                    enabled = false,
                                    onClick = {},
                                    onLongClick = {},
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                            }
                        }
                    } else {
                        DisabledState()
                    }
                }
                return@Column
            }
            when {
                !state.loaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                state.conversations.isEmpty() -> {
                    EmptyState()
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.conversations, key = { it.threadId }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                contactNames = state.contactNames,
                                onClick = { onOpenConversation(conversation.threadId) },
                                onLongClick = { pendingDelete = conversation },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.messages_delete_conversation_title)) },
            text = { Text(stringResource(R.string.messages_delete_conversation_message)) },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(conversation.threadId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
        )
    }
}

/**
 * Shown instead of the inbox until SMS Toolbox is the default SMS app. Play requires the role to
 * be requested before any SMS permission, and the inbox cannot be read without it anyway.
 */
@Composable
fun DefaultSmsAppPrompt(
    onSetDefaultSmsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.default_sms_prompt_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.default_sms_prompt_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSetDefaultSmsApp, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.default_sms_prompt_action))
        }
    }
}

@Composable
private fun DisabledState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.messages_disabled_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Material's opacity for disabled content. */
const val DISABLED_ALPHA = 0.38f

@Composable
private fun EmptyState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.messages_empty),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.messages_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    contactNames: Map<String, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
) {
    val title =
        conversation.addresses
            .joinToString(", ") { contactNames[it] ?: it }
            .ifBlank { stringResource(R.string.messages_unknown_sender) }
    val snippet =
        conversation.snippet.ifBlank {
            if (conversation.hasAttachment) stringResource(R.string.messages_picture_message) else ""
        }
    val weight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title = title, isGroup = conversation.addresses.size > 1)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        DateUtils
                            .getRelativeTimeSpanString(
                                conversation.dateEpochMs,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.hasAttachment) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = weight,
                    color = if (conversation.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unread) {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
fun Avatar(
    title: String,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initial = title.firstOrNull { it.isLetter() }?.uppercaseChar()
        if (isGroup || initial == null) {
            Icon(
                imageVector = if (isGroup) Icons.Default.Group else Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
