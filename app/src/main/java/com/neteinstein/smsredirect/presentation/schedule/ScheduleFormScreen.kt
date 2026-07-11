package com.neteinstein.smsredirect.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.RepeatType
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormScreen(
    uiState: ScheduleFormUiState,
    onNameChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onContactChanged: (String) -> Unit,
    onPickContact: () -> Unit,
    onMessageChanged: (String) -> Unit,
    onDateSelected: (Long) -> Unit,
    onRepeatTypeChanged: (RepeatType) -> Unit,
    onStopAtSelected: (Long?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.id == 0L) {
                            stringResource(R.string.schedule_form_title_create)
                        } else {
                            stringResource(R.string.schedule_form_title_edit)
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
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_form_label_name)) },
                placeholder = { Text(stringResource(R.string.schedule_form_placeholder_name_optional)) },
            )
            ScheduleCategoryField(
                category = uiState.category,
                existingCategories = uiState.existingCategories,
                onCategoryChanged = onCategoryChanged,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.contactNumber,
                    onValueChange = onContactChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.schedule_form_label_contact)) },
                    supportingText =
                        uiState.contactName?.let { name ->
                            { Text(name) }
                        },
                )
                IconButton(onClick = onPickContact) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = stringResource(R.string.schedule_form_cd_pick_contact),
                    )
                }
            }
            OutlinedTextField(
                value = uiState.message,
                onValueChange = onMessageChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_form_label_message)) },
                placeholder = { Text(stringResource(R.string.schedule_form_placeholder_message)) },
                minLines = 5,
            )
            DateAndTimeRow(
                dateEpochMs = uiState.dateEpochMs,
                onSelected = onDateSelected,
            )
            RepeatTypeField(
                selected = uiState.repeatType,
                onSelected = onRepeatTypeChanged,
            )
            if (uiState.repeatType != RepeatType.NONE) {
                StopAtField(
                    epochMs = uiState.stopAtEpochMs,
                    onSelected = onStopAtSelected,
                )
            }
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.schedule_form_action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCategoryField(
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
            label = { Text(stringResource(R.string.schedule_form_label_category)) },
            placeholder = { Text(stringResource(R.string.schedule_form_placeholder_category)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatTypeField(
    selected: RepeatType,
    onSelected: (RepeatType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = repeatTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(stringResource(R.string.schedule_form_label_repeat)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            RepeatType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(repeatTypeLabel(type)) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A read-only, label + value field styled like an outlined text field, clickable to open [onClick]. */
@Composable
private fun ReadOnlyPickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAndTimeRow(
    dateEpochMs: Long?,
    onSelected: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateDisplay = dateEpochMs?.let { formatDate(it) } ?: stringResource(R.string.schedule_form_date_placeholder)
    val timeDisplay = dateEpochMs?.let { formatTime(it) } ?: stringResource(R.string.schedule_form_time_placeholder)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadOnlyPickerField(
            label = stringResource(R.string.schedule_form_label_date),
            value = dateDisplay,
            modifier = Modifier.weight(1f),
            onClick = { showDatePicker = true },
        )
        ReadOnlyPickerField(
            label = stringResource(R.string.schedule_form_label_time),
            value = timeDisplay,
            modifier = Modifier.weight(1f),
            onClick = { showTimePicker = true },
        )
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = dateEpochMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millisUtc ->
                        // Keep whatever time-of-day was already picked (or now, if none yet) and
                        // only replace the date portion.
                        val existingTime =
                            Calendar.getInstance().apply {
                                dateEpochMs?.let { timeInMillis = it }
                            }
                        onSelected(
                            combineUtcDateWithLocalTime(
                                millisUtc,
                                existingTime.get(Calendar.HOUR_OF_DAY),
                                existingTime.get(Calendar.MINUTE),
                            ),
                        )
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initialCalendar =
            Calendar.getInstance().apply {
                dateEpochMs?.let { timeInMillis = it }
            }
        val timePickerState =
            rememberTimePickerState(
                initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
                initialMinute = initialCalendar.get(Calendar.MINUTE),
            )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.schedule_form_pick_time_title)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    // Keep whatever date was already picked (or today, if none yet) and only
                    // replace the time-of-day portion. This value is already a proper local
                    // instant (not UTC-anchored), so no UTC re-anchoring is needed here.
                    val baseDateMillis = dateEpochMs ?: System.currentTimeMillis()
                    val baseLocalDate = Instant.ofEpochMilli(baseDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val combined =
                        baseLocalDate
                            .atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    onSelected(combined)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopAtField(
    epochMs: Long?,
    onSelected: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val displayValue = epochMs?.let { formatDate(it) } ?: stringResource(R.string.schedule_form_stop_at_placeholder)

    Row(verticalAlignment = Alignment.CenterVertically) {
        ReadOnlyPickerField(
            label = stringResource(R.string.schedule_form_label_stop_at),
            value = displayValue,
            modifier = Modifier.weight(1f),
            onClick = { showDatePicker = true },
        )
        if (epochMs != null) {
            IconButton(onClick = { onSelected(null) }) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.schedule_form_cd_clear_stop_at),
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = epochMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millisUtc ->
                        onSelected(endOfDayEpochMs(millisUtc))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatDate(epochMs: Long): String = DateFormat.getDateInstance().format(Date(epochMs))

private fun formatTime(epochMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

/**
 * Material3's [DatePicker] reports the selected date as UTC milliseconds at midnight of that date,
 * regardless of the device's timezone. To combine it with a wall-clock time picked in the device's
 * own timezone, the UTC date must first be re-anchored as a [java.time.LocalDate] using [ZoneOffset.UTC],
 * then re-combined with the chosen hour/minute in the system default zone.
 */
private fun combineUtcDateWithLocalTime(
    dateMillisUtc: Long,
    hour: Int,
    minute: Int,
): Long {
    val localDate = Instant.ofEpochMilli(dateMillisUtc).atZone(ZoneOffset.UTC).toLocalDate()
    return localDate
        .atTime(hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

/** Same UTC-anchoring caveat as [combineUtcDateWithLocalTime]; returns 23:59:59 local time of that date. */
private fun endOfDayEpochMs(dateMillisUtc: Long): Long {
    val localDate = Instant.ofEpochMilli(dateMillisUtc).atZone(ZoneOffset.UTC).toLocalDate()
    return localDate
        .atTime(23, 59, 59)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
