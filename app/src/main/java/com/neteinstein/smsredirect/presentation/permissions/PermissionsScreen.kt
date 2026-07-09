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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            colors =
                if (permission.isGranted) {
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF2E7D32),
                        disabledContentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
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
