package com.neteinstein.smsredirect.presentation.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.R

data class PermissionItem(
    val id: String,
    val label: String,
    val description: String,
    val isGranted: Boolean,
    val isOptional: Boolean = false,
    val warningMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    permissions: List<PermissionItem>,
    onRequestPermission: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            permissions.forEach { permission ->
                PermissionRow(
                    permission = permission,
                    onRequest = { onRequestPermission(permission.id) },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: PermissionItem,
    onRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        if (permission.isGranted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                    contentDescription = null,
                    tint =
                        if (permission.isGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = permission.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (permission.isOptional) {
                            Surface(
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = stringResource(R.string.permission_optional_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Optional permissions get a distinct (green) "granted" color instead of the
                // default disabled gray, so an enabled optional permission stands out from the
                // required ones.
                val buttonColors =
                    if (permission.isOptional && permission.isGranted) {
                        ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                Button(
                    onClick = onRequest,
                    enabled = !permission.isGranted,
                    colors = buttonColors,
                ) {
                    Text(
                        text =
                            if (permission.isGranted) {
                                stringResource(R.string.permissions_granted)
                            } else {
                                stringResource(R.string.permissions_request)
                            },
                    )
                }
            }

            if (!permission.isGranted && permission.warningMessage != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .width(20.dp),
                    )
                    Text(
                        text = permission.warningMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
