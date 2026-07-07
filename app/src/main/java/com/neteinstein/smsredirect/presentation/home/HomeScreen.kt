package com.neteinstein.smsredirect.presentation.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.Rule
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rules: List<Rule>,
    contactNames: Map<String, String>,
    onAddRule: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    onMoveRule: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
) {
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(stringResource(R.string.delete_rule_dialog_title)) },
            text = { Text(stringResource(R.string.delete_rule_dialog_message, rule.name.ifBlank { rule.id.toString() })) },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRule(rule)
                    ruleToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title_rules)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.home_cd_history),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_cd_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_cd_add_rule),
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sms_forward_illustration),
                        contentDescription = stringResource(R.string.home_no_rules_illustration_cd),
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.home_no_rules),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val groupedRules = remember(rules) {
                    rules.groupBy { it.category.ifBlank { "" } }
                }
                val sortedCategories = remember(groupedRules) {
                    groupedRules.keys.sorted()
                }
                var collapsedCategories by remember { mutableStateOf(emptySet<String>()) }

                val listState = rememberLazyListState()
                var draggedIndex by remember { mutableIntStateOf(-1) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedCategories.forEach { category ->
                        val categoryRules = groupedRules[category] ?: emptyList()
                        val isExpanded = category !in collapsedCategories

                        item(key = "category_header_$category") {
                            CategoryHeader(
                                title = if (category.isBlank()) {
                                    stringResource(R.string.home_category_uncategorized)
                                } else {
                                    category
                                },
                                ruleCount = categoryRules.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    collapsedCategories = if (isExpanded) {
                                        collapsedCategories + category
                                    } else {
                                        collapsedCategories - category
                                    }
                                },
                            )
                        }

                        if (isExpanded) {
                            items(
                                count = categoryRules.size,
                                key = { categoryRules[it].id },
                            ) { index ->
                                val rule = categoryRules[index]
                                val globalIndex = rules.indexOf(rule)
                                val isDragging = draggedIndex == globalIndex
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "drag_elevation",
                                )

                                RuleCard(
                                    rule = rule,
                                    senderContactName = contactNames[rule.senderText],
                                    targetContactName = contactNames[rule.targetNumber],
                                    onEditRule = onEditRule,
                                    onDeleteRule = { ruleToDelete = it },
                                    modifier =
                                        Modifier
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                                            .pointerInput(Unit) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedIndex = globalIndex
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y

                                                        val itemHeight = size.height + 8.dp.toPx()
                                                        val offset = (dragOffsetY / itemHeight).roundToInt()
                                                        val targetIndex =
                                                            (draggedIndex + offset)
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
                                                    },
                                                )
                                            },
                                    elevation = elevation,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    ruleCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
        if (!isExpanded) {
            Text(
                text = stringResource(R.string.home_category_rule_count, ruleCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) {
                stringResource(R.string.home_cd_collapse_category)
            } else {
                stringResource(R.string.home_cd_expand_category)
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    senderContactName: String?,
    targetContactName: String?,
    onEditRule: (Long) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onEditRule(rule.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.home_cd_drag_to_reorder),
                modifier = Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.name.ifBlank {
                        stringResource(R.string.rule_default_name_format, rule.id)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val senderDisplay = buildSenderTargetDisplay(rule.senderText, senderContactName)
                Text(
                    stringResource(R.string.home_sender_format, senderDisplay),
                )
                val targetDisplay = buildSenderTargetDisplay(rule.targetNumber, targetContactName)
                Text(
                    stringResource(R.string.home_target_format, targetDisplay),
                )
            }
            IconButton(onClick = { onDeleteRule(rule) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_cd_delete_rule),
                )
            }
        }
    }
}

private fun buildSenderTargetDisplay(
    number: String,
    contactName: String?,
): String {
    if (number.isBlank()) return "-"
    return if (contactName != null) "$contactName ($number)" else number
}
