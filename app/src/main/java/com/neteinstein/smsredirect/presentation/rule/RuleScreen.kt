package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import com.neteinstein.smsredirect.presentation.components.WarningBanner

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
    onTypeChanged: (RuleType) -> Unit = {},
    onMessageTextChanged: (String) -> Unit = {},
    onActionKindChanged: (String) -> Unit = {},
    onPickSystemSound: () -> Unit = {},
    onPickSong: () -> Unit = {},
    onUseDefaultSound: () -> Unit = {},
    onVibrationPatternChanged: (VibrationPattern) -> Unit = {},
    onSoundModeChanged: (SoundMode) -> Unit = {},
    onGrantSoundModeAccess: () -> Unit = {},
    onTryPhoneAction: () -> Unit = {},
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuleTypeSelector(type = uiState.type, onTypeChanged = onTypeChanged)
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
                    label = {
                        Text(
                            stringResource(
                                if (uiState.type == RuleType.REDIRECT) {
                                    R.string.rule_label_sender
                                } else {
                                    R.string.rule_label_sender_optional
                                },
                            ),
                        )
                    },
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
            if (uiState.type == RuleType.REDIRECT) {
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
            } else {
                PhoneActionSection(
                    uiState = uiState,
                    onMessageTextChanged = onMessageTextChanged,
                    onActionKindChanged = onActionKindChanged,
                    onPickSystemSound = onPickSystemSound,
                    onPickSong = onPickSong,
                    onUseDefaultSound = onUseDefaultSound,
                    onVibrationPatternChanged = onVibrationPatternChanged,
                    onSoundModeChanged = onSoundModeChanged,
                    onGrantSoundModeAccess = onGrantSoundModeAccess,
                    onTryPhoneAction = onTryPhoneAction,
                )
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

@Composable
private fun RuleTypeSelector(
    type: RuleType,
    onTypeChanged: (RuleType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.rule_type_title),
            style = MaterialTheme.typography.titleSmall,
        )
        val options =
            listOf(
                RuleType.REDIRECT to stringResource(R.string.rule_type_redirect),
                RuleType.PHONE_ACTION to stringResource(R.string.rule_type_phone_action),
            )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = type == option,
                    onClick = { onTypeChanged(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

/** The "when" (message text) and "then" (which action, and its settings) of a phone-action rule. */
@Composable
private fun PhoneActionSection(
    uiState: RuleUiState,
    onMessageTextChanged: (String) -> Unit,
    onActionKindChanged: (String) -> Unit,
    onPickSystemSound: () -> Unit,
    onPickSong: () -> Unit,
    onUseDefaultSound: () -> Unit,
    onVibrationPatternChanged: (VibrationPattern) -> Unit,
    onSoundModeChanged: (SoundMode) -> Unit,
    onGrantSoundModeAccess: () -> Unit,
    onTryPhoneAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.messageText,
            onValueChange = onMessageTextChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.rule_label_message_text)) },
            supportingText = { Text(stringResource(R.string.rule_message_text_supporting)) },
        )
        ChoiceField(
            label = stringResource(R.string.rule_label_action),
            selected = uiState.actionKind,
            options = PHONE_ACTION_KINDS,
            optionLabel = { phoneActionKindLabel(it) },
            onSelected = onActionKindChanged,
        )
        when (uiState.actionKind) {
            PhoneAction.KIND_VIBRATE -> {
                ChoiceField(
                    label = stringResource(R.string.rule_label_vibration_pattern),
                    selected = uiState.vibrationPattern,
                    options = VibrationPattern.entries,
                    optionLabel = { vibrationPatternLabel(it) },
                    onSelected = onVibrationPatternChanged,
                )
            }

            PhoneAction.KIND_SOUND_MODE -> {
                ChoiceField(
                    label = stringResource(R.string.rule_label_sound_mode),
                    selected = uiState.soundMode,
                    options = SoundMode.entries,
                    optionLabel = { soundModeLabel(it) },
                    onSelected = onSoundModeChanged,
                )
                if (uiState.showSoundModeAccessWarning) {
                    WarningBanner(
                        message = stringResource(R.string.rule_sound_mode_access_message),
                        actionLabel = stringResource(R.string.rule_sound_mode_access_action),
                        onActionClick = onGrantSoundModeAccess,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            else -> {
                RingSettings(uiState, onPickSystemSound, onPickSong, onUseDefaultSound)
            }
        }
        OutlinedButton(onClick = onTryPhoneAction, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rule_action_try))
        }
    }
}

@Composable
private fun RingSettings(
    uiState: RuleUiState,
    onPickSystemSound: () -> Unit,
    onPickSong: () -> Unit,
    onUseDefaultSound: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.rule_ring_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    R.string.rule_label_sound,
                    uiState.soundName.ifBlank { stringResource(R.string.rule_sound_default) },
                ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickSystemSound) { Text(stringResource(R.string.rule_action_pick_system_sound)) }
            OutlinedButton(onClick = onPickSong) { Text(stringResource(R.string.rule_action_pick_song)) }
        }
        if (uiState.soundUri.isNotBlank()) {
            TextButton(onClick = onUseDefaultSound) { Text(stringResource(R.string.rule_action_use_default_sound)) }
        }
    }
}

/** A read-only dropdown picking one of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
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
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
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
