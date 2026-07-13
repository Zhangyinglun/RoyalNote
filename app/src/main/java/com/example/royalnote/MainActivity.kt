package com.example.royalnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.royalnote.data.MoodLabels
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.data.NoteRepository
import com.example.royalnote.data.RoyalNoteDatabase
import com.example.royalnote.network.OpenRouterService
import com.example.royalnote.settings.SettingsRepository
import com.example.royalnote.settings.SharedPreferencesSettingsStorage
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportViewModel
import com.example.royalnote.ui.ImportViewModelFactory
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.RecordTimelineViewModel
import com.example.royalnote.ui.RecordTimelineViewModelFactory
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.theme.MoodBrick
import com.example.royalnote.ui.theme.MoodCeladon
import com.example.royalnote.ui.theme.MoodGray
import com.example.royalnote.ui.theme.MoodInkBlue
import com.example.royalnote.ui.theme.MoodOchre
import com.example.royalnote.ui.theme.MoodPurple
import com.example.royalnote.ui.theme.MoodRed
import com.example.royalnote.ui.theme.RoyalNoteTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardShape = RoundedCornerShape(8.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoyalNoteTheme {
                val context = LocalContext.current
                val database = remember { RoyalNoteDatabase.getInstance(context) }
                val repository = remember { NoteRepository(database.noteRecordDao()) }
                val settingsRepository = remember {
                    SettingsRepository(SharedPreferencesSettingsStorage(context.applicationContext))
                }
                val parser = remember { OpenRouterService(settingsRepository) }

                val timelineViewModel: RecordTimelineViewModel = viewModel(
                    factory = RecordTimelineViewModelFactory(repository),
                )
                val importViewModel: ImportViewModel = viewModel(
                    factory = ImportViewModelFactory(parser, repository),
                )

                LaunchedEffect(Unit) {
                    SeedData.seedIfEmpty(repository)
                }

                val navController = rememberNavController()
                val uiState by timelineViewModel.uiState.collectAsStateWithLifecycle()
                val importUiState by importViewModel.uiState.collectAsStateWithLifecycle()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        RoyalNoteApp(
                            uiState = uiState,
                            onEventTextChange = timelineViewModel::updateEventText,
                            onMoodSelected = timelineViewModel::selectMood,
                            onMoodNoteChange = timelineViewModel::updateMoodNote,
                            onEditEventTextChange = timelineViewModel::updateEditEventText,
                            onEditMoodSelected = timelineViewModel::selectEditMood,
                            onEditMoodNoteChange = timelineViewModel::updateEditMoodNote,
                            onSave = timelineViewModel::save,
                            onEdit = timelineViewModel::startEditing,
                            onCancelEdit = timelineViewModel::cancelEditing,
                            onDelete = timelineViewModel::delete,
                            onMessageShown = timelineViewModel::clearMessage,
                            onImportClick = { navController.navigate("import") },
                        )
                    }
                    composable("import") {
                        ImportScreen(
                            uiState = importUiState,
                            onTextChange = importViewModel::updateText,
                            onImportClick = importViewModel::importRecords,
                            onBack = {
                                importViewModel.resetState()
                                navController.popBackStack()
                            },
                            onMessageShown = importViewModel::clearMessage,
                            onSuccessConfirmed = {
                                importViewModel.dismissSuccessDialog()
                                importViewModel.resetState()
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalNoteApp(
    uiState: RecordTimelineUiState,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onEditEventTextChange: (String) -> Unit,
    onEditMoodSelected: (String?) -> Unit,
    onEditMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (NoteRecord) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (NoteRecord) -> Unit,
    onMessageShown: () -> Unit,
    onImportClick: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("起居注", fontFamily = FontFamily.Serif) },
                actions = {
                    TextButton(onClick = onImportClick) {
                        Text("导入", fontFamily = FontFamily.Serif)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "录今日之事，存此刻之心。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                CircleShape,
                            )
                    )
                }
            }
            item {
                RecordEditor(
                    eventText = uiState.eventText,
                    selectedMood = uiState.selectedMood,
                    moodNote = uiState.moodNote,
                    title = "速录一则",
                    saveLabel = "入录",
                    showCancel = false,
                    onEventTextChange = onEventTextChange,
                    onMoodSelected = onMoodSelected,
                    onMoodNoteChange = onMoodNoteChange,
                    onSave = onSave,
                    onCancelEdit = {},
                )
            }
            if (uiState.timelineDays.isEmpty()) {
                item { EmptyTimeline() }
            } else {
                uiState.timelineDays.forEach { day ->
                    item { TimelineHeader(day.label) }
                    items(day.records, key = { it.id }) { record ->
                        if (uiState.editingRecord?.id == record.id) {
                            RecordEditor(
                                eventText = uiState.editEventText,
                                selectedMood = uiState.editSelectedMood,
                                moodNote = uiState.editMoodNote,
                                title = "修订此则",
                                saveLabel = "改毕入录",
                                showCancel = true,
                                onEventTextChange = onEditEventTextChange,
                                onMoodSelected = onEditMoodSelected,
                                onMoodNoteChange = onEditMoodNoteChange,
                                onSave = onSave,
                                onCancelEdit = onCancelEdit,
                            )
                        } else {
                            RecordCard(record = record, onEdit = onEdit, onDelete = onDelete)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditor(
    eventText: String,
    selectedMood: String?,
    moodNote: String,
    title: String,
    saveLabel: String,
    showCancel: Boolean,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CardShape,
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    color = primaryColor.copy(alpha = 0.7f),
                    topLeft = Offset.Zero,
                    size = Size(width = 3.dp.toPx(), height = size.height),
                )
            },
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = eventText,
                onValueChange = onEventTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("今日何为") },
                minLines = 2,
            )
            Text("心绪", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoodLabels.ALL.forEach { mood ->
                    val color = moodColor(mood)
                    FilterChip(
                        selected = selectedMood == mood,
                        onClick = {
                            if (selectedMood == mood) onMoodSelected(null)
                            else onMoodSelected(mood)
                        },
                        label = { Text(mood) },
                        colors = if (color != null) FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color,
                            selectedLabelColor = Color.White,
                        ) else FilterChipDefaults.filterChipColors(),
                    )
                }
            }
            if (selectedMood != null) {
                OutlinedTextField(
                    value = moodNote,
                    onValueChange = onMoodNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("心绪补述，可留白") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(saveLabel) }
                if (showCancel) {
                    TextButton(onClick = onCancelEdit) { Text("作罢") }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RecordCard(
    record: NoteRecord,
    onEdit: (NoteRecord) -> Unit,
    onDelete: (NoteRecord) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val moodColorValue = moodColor(record.moodTag)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = outlineColor,
                    topLeft = Offset(x = 8.dp.toPx(), y = 0f),
                    size = Size(width = 1.dp.toPx(), height = size.height),
                )
            }
            .padding(start = 20.dp)
            .border(
                width = 1.dp,
                color = outlineColor,
                shape = CardShape,
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    color = primaryColor.copy(alpha = 0.6f),
                    topLeft = Offset.Zero,
                    size = Size(width = 3.dp.toPx(), height = size.height),
                )
            },
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatTime(record.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = onSurfaceVariantColor,
            )
            Text(record.eventText, style = MaterialTheme.typography.bodyLarge)
            if (record.moodTag != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(record.moodTag) },
                    colors = if (moodColorValue != null) AssistChipDefaults.assistChipColors(
                        containerColor = moodColorValue,
                        labelColor = Color.White,
                    ) else AssistChipDefaults.assistChipColors(),
                )
                if (!record.moodNote.isNullOrBlank()) {
                    Text(record.moodNote, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(record) }) { Text("修订") }
                TextButton(onClick = { showDeleteDialog = true }) { Text("抹去") }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("抹去此则？") },
            text = { Text("抹去之后，不可复还。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(record)
                    },
                ) { Text("决意抹去") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("留之") }
            },
        )
    }
}

@Composable
private fun EmptyTimeline() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "尚无起居之录。且记今日之事。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun moodColor(mood: String?): Color? {
    if (mood == null) return null
    return when (mood) {
        "开心" -> MoodRed
        "满足" -> MoodOchre
        "平静" -> MoodCeladon
        "疲惫" -> MoodGray
        "烦躁" -> MoodBrick
        "低落" -> MoodInkBlue
        "焦虑" -> MoodPurple
        else -> null
    }
}

@Preview(showBackground = true)
@Composable
private fun RoyalNotePreview() {
    RoyalNoteTheme {
        RoyalNoteApp(
            uiState = RecordTimelineUiState(
                eventText = "整理书桌",
                selectedMood = "平静",
                moodNote = "心里安稳了一点",
                timelineDays = listOf(
                    TimelineDay(
                        label = "今日",
                        records = listOf(
                            NoteRecord(
                                id = 1,
                                eventText = "读了半晌书",
                                moodTag = "满足",
                                moodNote = null,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                        ),
                    )
                ),
            ),
            onEventTextChange = {},
            onMoodSelected = {},
            onMoodNoteChange = {},
            onEditEventTextChange = {},
            onEditMoodSelected = {},
            onEditMoodNoteChange = {},
            onSave = {},
            onEdit = {},
            onCancelEdit = {},
            onDelete = {},
            onMessageShown = {},
            onImportClick = {},
        )
    }
}
