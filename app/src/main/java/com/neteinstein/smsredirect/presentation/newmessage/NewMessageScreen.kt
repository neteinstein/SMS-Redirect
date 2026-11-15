package com.neteinstein.smsredirect.presentation.newmessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

/** "New message": choose one recipient (SMS) or several (group MMS), then continue. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    state: NewMessageUiState,
    onInputChanged: (String) -> Unit,
    onCommitInput: () -> Unit,
    onRemoveRecipient: (Recipient) -> Unit,
    onPickContact: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.recipients.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recipients.forEach { recipient ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveRecipient(recipient) },
                            label = { Text(recipient.name ?: recipient.address) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.new_message_cd_remove_recipient))
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                label = { Text(stringResource(R.string.new_message_to)) },
                placeholder = { Text(stringResource(R.string.new_message_to_hint)) },
                isError = state.invalidInput,
                supportingText =
                    if (state.invalidInput) {
                        { Text(stringResource(R.string.new_message_invalid_recipient)) }
                    } else {
                        null
                    },
                singleLine = true,
                // Not KeyboardType.Phone: a dial pad has no letters or "@", and email recipients are valid.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitInput() }),
                trailingIcon = {
                    IconButton(onClick = onPickContact) {
                        Icon(Icons.Default.Contacts, contentDescription = stringResource(R.string.new_message_cd_pick_contact))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.recipients.size + (if (state.input.isNotBlank()) 1 else 0) > 1) {
                Text(
                    text = stringResource(R.string.new_message_group_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onNext, enabled = state.canProceed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.new_message_next))
            }
        }
    }
}
