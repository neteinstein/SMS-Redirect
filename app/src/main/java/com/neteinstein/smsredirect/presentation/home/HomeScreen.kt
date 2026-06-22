package com.neteinstein.smsredirect.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neteinstein.smsredirect.domain.model.Rule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rules: List<Rule>,
    onAddRule: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Redirect Rules") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add rule")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No rules yet. Add one to get started.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(rule = rule, onEditRule = onEditRule, onDeleteRule = onDeleteRule)
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditRule(rule.id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Rule #${rule.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Sender: ${rule.senderText.ifBlank { "-" }}")
                Text("Phone: ${rule.phoneNumber.ifBlank { "-" }}")
                Text("Target: ${rule.targetNumber.ifBlank { "-" }}")
            }
            IconButton(onClick = { onDeleteRule(rule) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
