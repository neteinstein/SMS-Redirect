package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    uiState: RuleUiState,
    onSenderChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
    onPickSenderContact: () -> Unit,
    onPickTargetContact: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (uiState.id == 0L) "Create Rule" else "Edit Rule") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.senderText,
                    onValueChange = onSenderChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Sender") }
                )
                IconButton(onClick = onPickSenderContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = "Pick sender from contacts"
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
                    label = { Text("Target Number") }
                )
                IconButton(onClick = onPickTargetContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = "Pick target from contacts"
                    )
                }
            }
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save Rule")
            }
        }
    }
}
