package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    uiState: RuleUiState,
    onSenderChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPickContact: () -> Unit,
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
            OutlinedTextField(
                value = uiState.senderText,
                onValueChange = onSenderChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sender (text)") }
            )
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = onPhoneChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone Number") }
            )
            Button(onClick = onPickContact, modifier = Modifier.fillMaxWidth()) {
                Text("Pick from Contacts")
            }
            uiState.error?.let {
                Text(text = it)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save Rule")
            }
        }
    }
}
