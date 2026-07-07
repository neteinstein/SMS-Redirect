package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    uiState: RuleUiState,
    onNameChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onSenderChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
    onPickSenderContact: () -> Unit,
    onPickTargetContact: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
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
                        },
                    )
                },
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
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_label_name)) },
                placeholder = { Text(stringResource(R.string.rule_placeholder_name_optional)) },
            )
            CategoryField(
                category = uiState.category,
                existingCategories = uiState.existingCategories,
                onCategoryChanged = onCategoryChanged,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.senderText,
                    onValueChange = onSenderChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.rule_label_sender)) },
                    supportingText =
                        uiState.senderContactName?.let { name ->
                            { Text(name) }
                        },
                )
                IconButton(onClick = onPickSenderContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = stringResource(R.string.rule_cd_pick_sender_contact),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.targetNumber,
                    onValueChange = onTargetChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.rule_label_target_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText =
                        uiState.targetContactName?.let { name ->
                            { Text(name) }
                        },
                )
                IconButton(onClick = onPickTargetContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = stringResource(R.string.rule_cd_pick_target_contact),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    category: String,
    existingCategories: List<String>,
    onCategoryChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && existingCategories.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = category,
            onValueChange = { value ->
                onCategoryChanged(value)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(stringResource(R.string.rule_label_category)) },
            placeholder = { Text(stringResource(R.string.rule_placeholder_category)) },
            trailingIcon = {
                if (existingCategories.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
        )
        if (existingCategories.isNotEmpty()) {
            val filtered =
                if (category.isBlank()) {
                    existingCategories
                } else {
                    existingCategories.filter { it.contains(category, ignoreCase = true) }
                }
            if (filtered.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    filtered.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                onCategoryChanged(cat)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
