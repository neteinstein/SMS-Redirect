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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
                    Text(
                        text = permission.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequest,
                    enabled = !permission.isGranted,
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
        }
    }
}
