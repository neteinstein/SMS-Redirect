package com.neteinstein.smsredirect.presentation.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neteinstein.smsredirect.domain.model.Rule
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rules: List<Rule>,
    missingPermissions: List<String>,
    onAddRule: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    onMoveRule: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (missingPermissions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            "Missing permissions — SMS forwarding won't work:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        missingPermissions.forEach { perm ->
                            Text(
                                "• $perm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No rules yet. Add one to get started.")
                }
            } else {
            val listState = rememberLazyListState()
            var draggedIndex by remember { mutableIntStateOf(-1) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    val isDragging = draggedIndex == index
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "drag_elevation"
                    )

                    RuleCard(
                        rule = rule,
                        onEditRule = onEditRule,
                        onDeleteRule = onDeleteRule,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        val itemHeight = size.height + 8.dp.toPx()
                                        val offset = (dragOffsetY / itemHeight).roundToInt()
                                        val targetIndex = (draggedIndex + offset)
                                            .coerceIn(0, rules.size - 1)

                                        if (targetIndex != draggedIndex) {
                                            onMoveRule(draggedIndex, targetIndex)
                                            dragOffsetY -= (targetIndex - draggedIndex) * itemHeight
                                            draggedIndex = targetIndex
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                        onDragEnd()
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    }
                                )
                            },
                        elevation = elevation
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEditRule(rule.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.name.ifBlank { "Rule #${rule.id}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Sender: ${rule.senderText.ifBlank { "-" }}")
                Text("Target: ${rule.targetNumber.ifBlank { "-" }}")
            }
            IconButton(onClick = { onDeleteRule(rule) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
