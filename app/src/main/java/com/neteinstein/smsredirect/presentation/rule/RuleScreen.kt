package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    uiState: RuleUiState,
    onNameChanged: (String) -> Unit,
    onSenderChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
    onPickSenderContact: () -> Unit,
    onPickTargetContact: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.id == 0L) {
                            stringResource(R.string.rule_title_create)
                        } else {
                            stringResource(R.string.rule_title_edit)
                        }
                    )
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_label_name)) },
                placeholder = { Text(stringResource(R.string.rule_placeholder_name_optional)) }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.senderText,
                    onValueChange = onSenderChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.rule_label_sender)) }
                )
                IconButton(onClick = onPickSenderContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = stringResource(R.string.rule_cd_pick_sender_contact)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.targetNumber,
                    onValueChange = onTargetChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.rule_label_target_number)) }
                )
                IconButton(onClick = onPickTargetContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = stringResource(R.string.rule_cd_pick_target_contact)
                    )
                }
            }
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rule_action_save))
            }
        }
    }
}
