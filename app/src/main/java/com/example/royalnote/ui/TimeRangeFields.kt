package com.example.royalnote.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalnote.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private enum class TimeTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeRangeFields(
    startedAt: Long,
    endedAt: Long,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    onSetToNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var dateTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var showUpdated by remember { mutableStateOf(false) }
    var feedbackVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(feedbackVersion) {
        if (feedbackVersion > 0) {
            delay(1_400)
            showUpdated = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clock_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "起止时间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSetToNow != null) {
                SetToNowButton(
                    updated = showUpdated,
                    onClick = {
                        onSetToNow()
                        showUpdated = true
                        feedbackVersion += 1
                    },
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 260.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeSelectionRow(
                        label = "始",
                        accessibilityLabel = "开始",
                        millis = startedAt,
                        prefix = "start",
                        compactDate = true,
                        modifier = Modifier.weight(1f),
                        onDateClick = { dateTarget = TimeTarget.START },
                        onTimeClick = { timeTarget = TimeTarget.START },
                        zoneId = zoneId,
                    )
                    Text(
                        "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    TimeSelectionRow(
                        label = "终",
                        accessibilityLabel = "结束",
                        millis = endedAt,
                        prefix = "end",
                        compactDate = true,
                        modifier = Modifier.weight(1f),
                        onDateClick = { dateTarget = TimeTarget.END },
                        onTimeClick = { timeTarget = TimeTarget.END },
                        zoneId = zoneId,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    TimeSelectionRow(
                        label = "始",
                        accessibilityLabel = "开始",
                        millis = startedAt,
                        prefix = "start",
                        onDateClick = { dateTarget = TimeTarget.START },
                        onTimeClick = { timeTarget = TimeTarget.START },
                        zoneId = zoneId,
                    )
                    TimeSelectionRow(
                        label = "终",
                        accessibilityLabel = "结束",
                        millis = endedAt,
                        prefix = "end",
                        onDateClick = { dateTarget = TimeTarget.END },
                        onTimeClick = { timeTarget = TimeTarget.END },
                        zoneId = zoneId,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    }

    dateTarget?.let { target ->
        DateSelectionDialog(
            initialMillis = if (target == TimeTarget.START) startedAt else endedAt,
            zoneId = zoneId,
            onDismiss = { dateTarget = null },
            onConfirm = { selected ->
                if (target == TimeTarget.START) onStartedAtChange(selected) else onEndedAtChange(selected)
                dateTarget = null
            },
        )
    }

    timeTarget?.let { target ->
        TimeSelectionDialog(
            initialMillis = if (target == TimeTarget.START) startedAt else endedAt,
            zoneId = zoneId,
            onDismiss = { timeTarget = null },
            onConfirm = { selected ->
                if (target == TimeTarget.START) onStartedAtChange(selected) else onEndedAtChange(selected)
                timeTarget = null
            },
        )
    }
}

@Composable
private fun SetToNowButton(
    updated: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 92.dp)
            .testTag("setTimeToNowButton")
            .semantics { role = Role.Button }
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 34.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clock_24),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (updated) "已更新" else "设为现在",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TimeSelectionRow(
    label: String,
    accessibilityLabel: String,
    millis: Long,
    prefix: String,
    compactDate: Boolean = false,
    modifier: Modifier = Modifier,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    zoneId: ZoneId,
) {
    val dateText = formatEditorDate(millis, zoneId)
    val visibleDateText = if (compactDate) dateText.takeLast(5) else dateText
    val timeText = formatEditorTime(millis, zoneId)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("${prefix}DateButton")
                .semantics { contentDescription = "${accessibilityLabel}日期，$dateText" }
                .clickable(onClick = onDateClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = visibleDateText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .weight(0.8f)
                .heightIn(min = 48.dp)
                .testTag("${prefix}TimeButton")
                .semantics { contentDescription = "${accessibilityLabel}时间，$timeText" }
                .clickable(onClick = onTimeClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionDialog(
    initialMillis: Long,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = toDatePickerMillis(initialMillis, zoneId),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { selectedDate ->
                        onConfirm(replaceDate(initialMillis, selectedDate, zoneId))
                    }
                },
                modifier = Modifier.testTag("dateConfirmButton"),
                enabled = state.selectedDateMillis != null,
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dateCancelButton"),
            ) {
                Text("取消")
            }
        },
    ) {
        DatePicker(state = state, modifier = Modifier.testTag("datePicker"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectionDialog(
    initialMillis: Long,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = Instant.ofEpochMilli(initialMillis).atZone(zoneId)
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(replaceTime(initialMillis, state.hour, state.minute, zoneId)) },
                modifier = Modifier.testTag("timeConfirmButton"),
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("timeCancelButton"),
            ) {
                Text("取消")
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state, modifier = Modifier.testTag("timePicker"))
            }
        },
    )
}
