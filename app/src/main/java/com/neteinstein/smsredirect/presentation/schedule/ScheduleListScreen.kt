package com.neteinstein.smsredirect.presentation.schedule

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.presentation.theme.CategoryAccentColors
import com.neteinstein.smsredirect.presentation.theme.Dimens
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    scheduledSmsList: List<ScheduledSms>,
    contactNames: Map<String, String>,
    onAddScheduledSms: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditScheduledSms: (Long) -> Unit,
    onDeleteScheduledSms: (ScheduledSms) -> Unit,
    onMoveScheduledSms: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var itemToDelete by remember { mutableStateOf<ScheduledSms?>(null) }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.schedule_delete_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.schedule_delete_dialog_message,
                        item.name.ifBlank { item.id.toString() },
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteScheduledSms(item)
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title_list)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.schedule_cd_history),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.schedule_cd_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddScheduledSms) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.schedule_cd_add),
                )
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (scheduledSmsList.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.SpaceLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sms_forward_illustration),
                        contentDescription = stringResource(R.string.schedule_no_items_illustration_cd),
                        modifier = Modifier.size(Dimens.IllustrationSize),
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpaceDefault))
                    Text(
                        text = stringResource(R.string.schedule_no_items),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpaceLarge))
                    Button(onClick = onAddScheduledSms) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.SpaceSmall))
                        Text(stringResource(R.string.schedule_add_first_item))
                    }
                }
            } else {
                val groupedItems =
                    remember(scheduledSmsList) {
                        scheduledSmsList.groupBy { it.category.ifBlank { "" } }
                    }
                val sortedCategories =
                    remember(groupedItems) {
                        groupedItems.keys.sorted()
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
                        val categoryItems = groupedItems[category] ?: emptyList()
                        val isExpanded = category !in collapsedCategories

                        item(key = "category_header_$category") {
                            ScheduleCategoryHeader(
                                title =
                                    if (category.isBlank()) {
                                        stringResource(R.string.schedule_category_uncategorized)
                                    } else {
                                        category
                                    },
                                itemCount = categoryItems.size,
                                isExpanded = isExpanded,
                                accentColor = categoryAccentColor(category),
                                onToggle = {
                                    collapsedCategories =
                                        if (isExpanded) {
                                            collapsedCategories + category
                                        } else {
                                            collapsedCategories - category
                                        }
                                },
                            )
                        }

                        if (isExpanded) {
                            items(
                                count = categoryItems.size,
                                key = { categoryItems[it].id },
                            ) { index ->
                                val scheduledSms = categoryItems[index]
                                val globalIndex = scheduledSmsList.indexOf(scheduledSms)
                                val isDragging = draggedIndex == globalIndex
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "drag_elevation",
                                )

                                val currentCategoryItems by rememberUpdatedState(categoryItems)
                                val currentItems by rememberUpdatedState(scheduledSmsList)

                                ScheduledSmsCard(
                                    scheduledSms = scheduledSms,
                                    contactName = contactNames[scheduledSms.contactNumber],
                                    accentColor = categoryAccentColor(category),
                                    onEdit = onEditScheduledSms,
                                    onDelete = { itemToDelete = it },
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
                                                        val catItems = currentCategoryItems
                                                        val allItems = currentItems
                                                        val currentLocalIndex =
                                                            catItems.indexOfFirst { allItems.indexOf(it) == draggedIndex }
                                                        if (currentLocalIndex < 0) return@detectDragGesturesAfterLongPress
                                                        val targetLocalIndex =
                                                            (currentLocalIndex + offset)
                                                                .coerceIn(0, catItems.size - 1)
                                                        val targetGlobalIndex = allItems.indexOf(catItems[targetLocalIndex])

                                                        if (targetGlobalIndex != draggedIndex && targetGlobalIndex >= 0) {
                                                            onMoveScheduledSms(draggedIndex, targetGlobalIndex)
                                                            dragOffsetY -= (targetLocalIndex - currentLocalIndex) * itemHeight
                                                            draggedIndex = targetGlobalIndex
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
private fun ScheduleCategoryHeader(
    title: String,
    itemCount: Int,
    isExpanded: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "category_chevron_rotation",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.CardCornerRadius))
                .background(accentColor.copy(alpha = 0.1f))
                .clickable { onToggle() }
                .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor),
        )
        Spacer(modifier = Modifier.width(Dimens.SpaceSmall))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!isExpanded) {
            Text(
                text = stringResource(R.string.schedule_category_item_count, itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription =
                if (isExpanded) {
                    stringResource(R.string.home_cd_collapse_category)
                } else {
                    stringResource(R.string.home_cd_expand_category)
                },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

private fun categoryAccentColor(category: String): Color {
    val index = Math.floorMod(category.hashCode(), CategoryAccentColors.size)
    return CategoryAccentColors[index]
}

@Composable
private fun ScheduledSmsCard(
    scheduledSms: ScheduledSms,
    contactName: String?,
    accentColor: Color,
    onEdit: (Long) -> Unit,
    onDelete: (ScheduledSms) -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onEdit(scheduledSms.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(Dimens.CategoryAccentWidth)
                        .fillMaxHeight()
                        .background(accentColor),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(end = 12.dp)
                            .size(28.dp),
                    tint = accentColor,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        scheduledSms.name.ifBlank {
                            stringResource(R.string.schedule_default_name_format, scheduledSms.id)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val contactDisplay = buildContactDisplay(scheduledSms.contactNumber, contactName)
                    Text(stringResource(R.string.schedule_contact_format, contactDisplay))
                    Text(
                        stringResource(
                            R.string.schedule_next_send_format,
                            DateFormat.getDateTimeInstance().format(Date(scheduledSms.dateEpochMs)),
                        ),
                    )
                    if (scheduledSms.repeatType != RepeatType.NONE) {
                        Text(stringResource(R.string.schedule_repeat_format, repeatTypeLabel(scheduledSms.repeatType)))
                    }
                }
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.home_cd_drag_to_reorder),
                    modifier = Modifier.padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onDelete(scheduledSms) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.schedule_cd_delete),
                    )
                }
            }
        }
    }
}

private fun buildContactDisplay(
    number: String,
    contactName: String?,
): String {
    if (number.isBlank()) return "-"
    return if (contactName != null) "$contactName ($number)" else number
}
