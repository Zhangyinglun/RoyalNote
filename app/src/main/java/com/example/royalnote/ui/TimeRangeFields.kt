package com.example.royalnote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var dateTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("起止时间", style = MaterialTheme.typography.labelLarge)
        TimeSelectionRow(
            label = "始于",
            accessibilityLabel = "开始",
            millis = startedAt,
            prefix = "start",
            onDateClick = { dateTarget = TimeTarget.START },
            onTimeClick = { timeTarget = TimeTarget.START },
            zoneId = zoneId,
        )
        TimeSelectionRow(
            label = "终于",
            accessibilityLabel = "结束",
            millis = endedAt,
            prefix = "end",
            onDateClick = { dateTarget = TimeTarget.END },
            onTimeClick = { timeTarget = TimeTarget.END },
            zoneId = zoneId,
        )
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
private fun TimeSelectionRow(
    label: String,
    accessibilityLabel: String,
    millis: Long,
    prefix: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    zoneId: ZoneId,
) {
    val dateText = formatEditorDate(millis, zoneId)
    val timeText = formatEditorTime(millis, zoneId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = onDateClick,
            modifier = Modifier
                .weight(1f)
                .testTag("${prefix}DateButton")
                .semantics { contentDescription = "${accessibilityLabel}日期，$dateText" },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onTimeClick,
            modifier = Modifier
                .width(96.dp)
                .testTag("${prefix}TimeButton")
                .semantics { contentDescription = "${accessibilityLabel}时间，$timeText" },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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
