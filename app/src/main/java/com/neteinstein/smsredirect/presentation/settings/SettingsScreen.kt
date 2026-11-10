package com.neteinstein.smsredirect.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.model.LandingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sendWhileRoaming: Boolean,
    showNotifications: Boolean,
    forwardPrefix: String,
    defaultPrefix: String,
    currentLanguageLabel: String,
    landingScreen: LandingScreen,
    onLandingScreenChanged: (LandingScreen) -> Unit,
    gmailAddress: String,
    hasGmailAppPassword: Boolean,
    gmailVerification: GmailVerificationState,
    onSaveGmailCredentials: (String, String) -> Unit,
    onClearGmailCredentials: () -> Unit,
    onOpenAppPasswordHelp: () -> Unit,
    onSendWhileRoamingChanged: (Boolean) -> Unit,
    onShowNotificationsChanged: (Boolean) -> Unit,
    onForwardPrefixSaved: (String) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onBack: () -> Unit,
    /** The landing screens this build ships; the picker is hidden when there is only one. */
    availableLandingScreens: List<LandingScreen> = LandingScreen.entries,
    /**
     * Whether Redirect/Schedule settings (roaming, notifications, prefixes, configuration
     * export/import) are shown. False in the Play build, which has neither feature.
     */
    showMessagingSettings: Boolean = true,
    /** The Updates section, shown first; null in builds without the self-update. */
    updatesSection: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                    .verticalScroll(rememberScrollState()),
        ) {
            if (updatesSection != null) {
                updatesSection()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            LanguageRow(
                currentLanguageLabel = currentLanguageLabel,
                onClick = onOpenLanguageSettings,
            )

            if (availableLandingScreens.size > 1) {
                LandingScreenDropdown(
                    options = availableLandingScreens,
                    selected = landingScreen,
                    onSelected = onLandingScreenChanged,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (showMessagingSettings) {
                MessagingSettings(
                    sendWhileRoaming = sendWhileRoaming,
                    showNotifications = showNotifications,
                    forwardPrefix = forwardPrefix,
                    defaultPrefix = defaultPrefix,
                    onSendWhileRoamingChanged = onSendWhileRoamingChanged,
                    onShowNotificationsChanged = onShowNotificationsChanged,
                    onForwardPrefixSaved = onForwardPrefixSaved,
                    onDefaultPrefixChanged = onDefaultPrefixChanged,
                )
            }

            GmailAccountSection(
                gmailAddress = gmailAddress,
                hasGmailAppPassword = hasGmailAppPassword,
                verification = gmailVerification,
                onSave = onSaveGmailCredentials,
                onClear = onClearGmailCredentials,
                onOpenHelp = onOpenAppPasswordHelp,
            )

            if (showMessagingSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ConfigurationExportImport(
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                )
            }
        }
    }
}

@Composable
private fun MessagingSettings(
    sendWhileRoaming: Boolean,
    showNotifications: Boolean,
    forwardPrefix: String,
    defaultPrefix: String,
    onSendWhileRoamingChanged: (Boolean) -> Unit,
    onShowNotificationsChanged: (Boolean) -> Unit,
    onForwardPrefixSaved: (String) -> Unit,
    onDefaultPrefixChanged: (String) -> Unit,
) {
    Column {
        SettingSwitchRow(
            icon = Icons.Default.SignalCellularAlt,
            title = stringResource(R.string.settings_send_while_roaming),
            checked = sendWhileRoaming,
            onCheckedChange = onSendWhileRoamingChanged,
        )

        SettingSwitchRow(
            icon = Icons.Default.NotificationsActive,
            title = stringResource(R.string.settings_show_notifications),
            checked = showNotifications,
            onCheckedChange = onShowNotificationsChanged,
        )

        ForwardPrefixRow(
            currentPrefix = forwardPrefix,
            onSave = onForwardPrefixSaved,
        )

        DefaultPrefixDropdown(
            selectedPrefix = defaultPrefix,
            onPrefixSelected = onDefaultPrefixChanged,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun ConfigurationExportImport(
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onExportBackup,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.settings_export_rules),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                onClick = onImportBackup,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.settings_import_rules),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_export_import_gmail_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardPrefixRow(
    currentPrefix: String,
    onSave: (String) -> Unit,
) {
    var text by remember(currentPrefix) { mutableStateOf(currentPrefix) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.settings_forward_prefix_label)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Tag, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = { onSave(text) },
            enabled = text != currentPrefix,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.settings_forward_prefix_save),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandingScreenDropdown(
    options: List<LandingScreen>,
    selected: LandingScreen,
    onSelected: (LandingScreen) -> Unit,
) {
    val labels =
        mapOf(
            LandingScreen.BACKUP to stringResource(R.string.settings_landing_screen_backup),
            LandingScreen.REDIRECT to stringResource(R.string.settings_landing_screen_redirect),
            LandingScreen.SCHEDULE to stringResource(R.string.settings_landing_screen_schedule),
        )
    val options = options.map { it to labels.getValue(it) }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: labels.getValue(selected)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_landing_screen_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultPrefixDropdown(
    selectedPrefix: String,
    onPrefixSelected: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val noneLabel = stringResource(R.string.settings_prefix_none)
    val options =
        remember(locale, noneLabel) {
            listOf("" to noneLabel) + countryPrefixOptions(locale)
        }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        options.firstOrNull { it.first == selectedPrefix }?.second
            ?: selectedPrefix

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_default_prefix_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPrefixSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    currentLanguageLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = currentLanguageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.action_open_screen),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun GmailAccountSection(
    gmailAddress: String,
    hasGmailAppPassword: Boolean,
    verification: GmailVerificationState,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    var address by remember(gmailAddress) { mutableStateOf(gmailAddress) }
    // Never pre-filled from storage: the saved password should not re-enter the UI layer, so a
    // blank field plus the "saved" indicator below is all the user sees.
    var appPassword by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }
    val verifying = verification is GmailVerificationState.Verifying

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_gmail_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.settings_gmail_address)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !verifying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        )

        OutlinedTextField(
            value = appPassword,
            onValueChange = { appPassword = it },
            label = { Text(stringResource(R.string.settings_gmail_app_password)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { revealPassword = !revealPassword }) {
                    Icon(
                        imageVector = if (revealPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription =
                            if (revealPassword) {
                                stringResource(R.string.settings_gmail_cd_hide_password)
                            } else {
                                stringResource(R.string.settings_gmail_cd_show_password)
                            },
                    )
                }
            },
            visualTransformation =
                if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !verifying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )

        if (hasGmailAppPassword && appPassword.isBlank()) {
            Text(
                text = stringResource(R.string.settings_gmail_app_password_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onSave(address, appPassword) },
                enabled = !verifying && address.isNotBlank() && appPassword.isNotBlank(),
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.settings_gmail_verifying),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(stringResource(R.string.settings_gmail_save))
                }
            }
            if (hasGmailAppPassword) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.settings_gmail_remove))
                }
            }
        }

        VerificationMessage(verification)

        // The address is stored but the password could not be decrypted -- the database was
        // restored onto a device whose keystore does not hold the key.
        if (gmailAddress.isNotBlank() && !hasGmailAppPassword) {
            Text(
                text = stringResource(R.string.settings_gmail_credentials_lost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            text = stringResource(R.string.settings_gmail_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        TextButton(
            onClick = onOpenHelp,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(stringResource(R.string.settings_gmail_help))
        }
    }
}

@Composable
private fun VerificationMessage(verification: GmailVerificationState) {
    val (messageRes, color) =
        when (verification) {
            is GmailVerificationState.Success -> {
                R.string.settings_gmail_verified to MaterialTheme.colorScheme.primary
            }

            is GmailVerificationState.Failure -> {
                when (verification.result) {
                    is MailVerificationResult.InvalidCredentials -> {
                        R.string.settings_gmail_invalid to MaterialTheme.colorScheme.error
                    }

                    is MailVerificationResult.NetworkError -> {
                        R.string.settings_gmail_network_error to MaterialTheme.colorScheme.error
                    }

                    else -> {
                        R.string.settings_gmail_unknown_error to MaterialTheme.colorScheme.error
                    }
                }
            }

            else -> {
                return
            }
        }

    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}
