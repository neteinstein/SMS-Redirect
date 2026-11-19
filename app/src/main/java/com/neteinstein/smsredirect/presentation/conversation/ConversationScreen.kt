package com.neteinstein.smsredirect.presentation.conversation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.messaging.MessageLinks
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageAttachment
import com.neteinstein.smsredirect.domain.model.MessageReceipt
import com.neteinstein.smsredirect.domain.model.MessageStatus
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import com.neteinstein.smsredirect.presentation.components.WarningBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.IDN

/** One conversation: message bubbles, picture messages, and the composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (OutgoingAttachment) -> Unit,
    onOpenAttachment: (MessageAttachment) -> Unit,
    onRetry: (ConversationMessage) -> Unit,
    onDelete: (ConversationMessage) -> Unit,
    onErrorShown: () -> Unit,
    onDownload: (ConversationMessage) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onTrustSenders: () -> Unit = {},
    onStopTrusting: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText =
        state.error?.let { sendFailureText(it) }
            ?: if (state.downloadFailed) stringResource(R.string.conversation_mms_download_failed) else null
    LaunchedEffect(state.error, state.downloadFailed) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorShown()
        }
    }
    var selected by remember { mutableStateOf<ConversationMessage?>(null) }
    // Asking whether to trust the flagged senders; holds the tapped link, if a link started it.
    var trustPrompt by remember { mutableStateOf<TrustPrompt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Show the number under a contact name, so the user knows which one it is.
                        if (!state.isGroup) {
                            state.addresses.firstOrNull()?.takeIf { state.contactNames.containsKey(it) }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Only a trusted sender has something to undo here.
                    if (state.trustedSenders.isNotEmpty()) {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.conversation_cd_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.spam_stop_trusting)) },
                                onClick = {
                                    menuOpen = false
                                    onStopTrusting()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Composer(
                state = state,
                onDraftChanged = onDraftChanged,
                onSend = onSend,
                onPickImage = onPickImage,
                onPickFile = onPickFile,
                onRemoveAttachment = onRemoveAttachment,
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        val newestFirst = remember(state.messages) { state.messages.asReversed() }
        // Reverse layout keeps the newest message at the bottom and in view as messages arrive.
        LaunchedEffect(newestFirst.firstOrNull()?.key) { listState.animateScrollToItem(0) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (state.risk.isFlagged) {
                WarningBanner(
                    message = stringResource(R.string.spam_warning_banner),
                    actionLabel = stringResource(R.string.spam_trust_action),
                    onActionClick = { trustPrompt = TrustPrompt(url = null) },
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(newestFirst, key = { it.key }) { message ->
                    MessageBubble(
                        message = message,
                        senderLabel =
                            if (state.isGroup && message.isIncoming) state.contactNames[message.address] ?: message.address else null,
                        linksEnabled = message.key !in state.risk.flaggedMessageKeys,
                        onOpenAttachment = onOpenAttachment,
                        onLongClick = { selected = message },
                        onRetry = { onRetry(message) },
                        onDownload = { onDownload(message) },
                        onOpenLink = onOpenLink,
                        onBlockedLink = { url -> trustPrompt = TrustPrompt(url) },
                    )
                }
            }
        }
    }

    trustPrompt?.let { prompt ->
        TrustSenderDialog(
            senders = state.risk.flaggedSenders.joinToString(", ") { state.contactNames[it] ?: it },
            url = prompt.url,
            onDismiss = { trustPrompt = null },
            onOpenOnce = {
                trustPrompt = null
                prompt.url?.let(onOpenLink)
            },
            onConfirm = {
                trustPrompt = null
                onTrustSenders()
                prompt.url?.let(onOpenLink)
            },
        )
    }

    selected?.let { message ->
        MessageActionsDialog(
            message = message,
            onDismiss = { selected = null },
            onRetry = {
                onRetry(message)
                selected = null
            },
            onDelete = {
                onDelete(message)
                selected = null
            },
        )
    }
}

@Composable
private fun sendFailureText(failure: SendFailure): String =
    stringResource(
        when (failure) {
            SendFailure.NOT_DEFAULT_SMS_APP -> R.string.conversation_error_not_default
            SendFailure.NO_RECIPIENTS -> R.string.conversation_error_no_recipients
            SendFailure.EMPTY_MESSAGE -> R.string.conversation_error_empty
            SendFailure.ATTACHMENT_TOO_LARGE -> R.string.conversation_error_too_large
            SendFailure.ATTACHMENT_UNREADABLE -> R.string.conversation_error_unreadable
            SendFailure.NO_SERVICE -> R.string.conversation_error_no_service
        },
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ConversationMessage,
    senderLabel: String?,
    linksEnabled: Boolean,
    onOpenAttachment: (MessageAttachment) -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onOpenLink: (String) -> Unit,
    onBlockedLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val incoming = message.isIncoming
    val container = if (incoming) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer
    val content = if (incoming) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
    ) {
        senderLabel?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = container,
            contentColor = content,
            shape = RoundedCornerShape(18.dp),
            modifier =
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        onClick = {
                            when (message.status) {
                                MessageStatus.FAILED -> onRetry()
                                MessageStatus.NOT_DOWNLOADED -> onDownload()
                                else -> Unit
                            }
                        },
                        onLongClick = onLongClick,
                    ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                message.subject?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (message.isAwaitingDownload) {
                    NotDownloadedContent(downloading = message.status == MessageStatus.DOWNLOADING, onDownload = onDownload)
                }
                message.attachments.forEach { attachment ->
                    if (attachment.isImage) {
                        AttachmentImage(attachment, onClick = { onOpenAttachment(attachment) })
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenAttachment(attachment) },
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                attachment.name ?: attachment.contentType,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                if (message.body.isNotEmpty()) {
                    MessageBody(
                        body = message.body,
                        linksEnabled = linksEnabled,
                        onOpenLink = onOpenLink,
                        onBlockedLink = onBlockedLink,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            if (message.status == MessageStatus.FAILED) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            }
            Text(
                text =
                    when (message.status) {
                        MessageStatus.SENDING -> {
                            stringResource(R.string.conversation_status_sending)
                        }

                        MessageStatus.FAILED -> {
                            stringResource(R.string.conversation_status_failed)
                        }

                        else -> {
                            val time =
                                DateUtils.formatDateTime(
                                    context,
                                    message.dateEpochMs,
                                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
                                )
                            receiptLabel(message.receipt)?.let { "$it · $time" } ?: time
                        }
                    },
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (message.status == MessageStatus.FAILED || message.receipt == MessageReceipt.NOT_DELIVERED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * The message text with its web links tappable. In a message flagged as spam or fraud the links
 * are shown in the error colour and do not open: tapping one asks whether to trust the sender.
 */
@Composable
private fun MessageBody(
    body: String,
    linksEnabled: Boolean,
    onOpenLink: (String) -> Unit,
    onBlockedLink: (String) -> Unit,
) {
    val open by rememberUpdatedState(onOpenLink)
    val blocked by rememberUpdatedState(onBlockedLink)
    val linkColor = if (linksEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val text =
        remember(body, linksEnabled, linkColor) {
            buildAnnotatedString {
                append(body)
                // Never fails on a bounded input, but a message must always render.
                runCatching { MessageLinks.find(body) }.getOrDefault(emptyList()).forEach { link ->
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = link.url,
                            styles =
                                TextLinkStyles(
                                    SpanStyle(
                                        color = linkColor,
                                        textDecoration = if (linksEnabled) TextDecoration.Underline else TextDecoration.None,
                                    ),
                                ),
                            linkInteractionListener = { if (linksEnabled) open(link.url) else blocked(link.url) },
                        ),
                        link.start,
                        link.end,
                    )
                }
            }
        }
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

private data class TrustPrompt(
    val url: String?,
)

/** The override: the user confirms they know the sender, so the warning lifts and links open. */
@Composable
private fun TrustSenderDialog(
    senders: String,
    url: String?,
    onDismiss: () -> Unit,
    onOpenOnce: () -> Unit,
    onConfirm: () -> Unit,
) {
    // The host the browser will really use, in ASCII so a look-alike (`pаypal.com`) shows as `xn--...`.
    val site =
        url?.let { link ->
            // If the host cannot be converted, never show its look-alike Unicode form.
            MessageLinks.hostOf(link)?.let { host ->
                runCatching { IDN.toASCII(host) }.getOrElse { if (host.all { it.code < 0x80 }) host else "?" }
            } ?: "?"
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.spam_trust_title, senders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.spam_trust_message))
                url?.let {
                    Text(
                        stringResource(R.string.spam_trust_link, site.orEmpty(), it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = {
            Row {
                if (url != null) {
                    TextButton(onClick = onOpenOnce) { Text(stringResource(R.string.spam_open_once)) }
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(if (url != null) R.string.spam_trust_and_open else R.string.spam_trust_confirm))
                }
            }
        },
    )
}

/** "Delivered", "Read", ... for a sent message whose receipt has come in; null when there is none. */
@Composable
private fun receiptLabel(receipt: MessageReceipt): String? =
    when (receipt) {
        MessageReceipt.NONE, MessageReceipt.PENDING -> null
        MessageReceipt.DELIVERED -> stringResource(R.string.conversation_receipt_delivered)
        MessageReceipt.NOT_DELIVERED -> stringResource(R.string.conversation_receipt_not_delivered)
        MessageReceipt.READ -> stringResource(R.string.conversation_receipt_read)
    }

/** A picture message still on the carrier's server: say so, and offer to fetch it. */
@Composable
private fun NotDownloadedContent(
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text =
                stringResource(
                    if (downloading) R.string.conversation_mms_downloading else R.string.conversation_mms_not_downloaded,
                ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (downloading) {
        LinearProgressIndicator(modifier = Modifier.widthIn(min = 160.dp))
    } else {
        FilledTonalButton(onClick = onDownload) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
            Text(stringResource(R.string.conversation_mms_download))
        }
    }
}

/** Decodes a stored MMS image off the main thread, downsampled to bubble size. */
@Composable
private fun AttachmentImage(
    attachment: MessageAttachment,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.uri) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, attachment.uri.toUri()),
                    ) { decoder, info, _ ->
                        val largest = maxOf(info.size.width, info.size.height)
                        if (largest > MAX_IMAGE_PX) {
                            val scale = MAX_IMAGE_PX.toFloat() / largest
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1),
                            )
                        }
                    }
                }.getOrNull()
            }
    }
    Box(
        modifier =
            Modifier
                .widthIn(max = 260.dp)
                .heightIn(min = 80.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = stringResource(R.string.conversation_cd_picture),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.conversation_cd_picture))
        }
    }
}

@Composable
private fun Composer(
    state: ConversationUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (OutgoingAttachment) -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
        ) {
            if (state.attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(state.attachments) { attachment ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveAttachment(attachment) },
                            label = {
                                Text(
                                    attachment.name ?: stringResource(R.string.conversation_attachment),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.conversation_cd_remove_attachment))
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPickImage) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.conversation_cd_add_picture))
                }
                IconButton(onClick = onPickFile) {
                    Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.conversation_cd_add_file))
                }
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChanged,
                    placeholder = {
                        Text(
                            stringResource(
                                if (state.sendsAsMms) R.string.conversation_hint_mms else R.string.conversation_hint_sms,
                            ),
                        )
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = state.canSend,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.conversation_cd_send))
                }
            }
        }
    }
}

@Composable
private fun MessageActionsDialog(
    message: ConversationMessage,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_message_actions)) },
        text = {
            Column {
                if (message.body.isNotEmpty()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(message.body))
                        onDismiss()
                    }) { Text(stringResource(R.string.conversation_action_copy)) }
                }
                if (message.status == MessageStatus.FAILED) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.conversation_action_retry)) }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_IMAGE_PX = 1080
