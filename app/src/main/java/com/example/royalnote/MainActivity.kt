package com.example.royalnote

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.example.royalnote.data.MoodLabels
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.data.NoteRepository
import com.example.royalnote.data.RoyalNoteDatabase
import com.example.royalnote.network.OpenRouterService
import com.example.royalnote.reflection.AssetReflectionPromptProvider
import com.example.royalnote.reflection.MemoryFileStore
import com.example.royalnote.reflection.OpenRouterReflectionService
import com.example.royalnote.reflection.ReflectionRepository
import com.example.royalnote.ui.AnalysisScreen
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportViewModel
import com.example.royalnote.ui.ImportViewModelFactory
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.RecordTimelineViewModel
import com.example.royalnote.ui.RecordTimelineViewModelFactory
import com.example.royalnote.ui.ReflectionViewModel
import com.example.royalnote.ui.ReflectionViewModelFactory
import com.example.royalnote.ui.RoyalNoteNavigation
import com.example.royalnote.ui.SettingsScreen
import com.example.royalnote.ui.SettingsViewModel
import com.example.royalnote.ui.SettingsViewModelFactory
import com.example.royalnote.ui.TimeRangeFields
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.formatRecordTimeRange
import com.example.royalnote.ui.theme.RoyalNoteTheme
import com.example.royalnote.ui.theme.moodColor
import java.io.File
import java.time.ZoneId

private val CardShape = RoundedCornerShape(8.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            val context = LocalContext.current
            val settingsDependencies = remember {
                MainActivityDependencyOverrides.resolve(context.applicationContext)
            }
            val settingsRepository = settingsDependencies.settingsRepository
            val appSettings by settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = appSettings.themeMode.useDarkTheme(isSystemInDarkTheme())
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            RoyalNoteTheme(darkTheme = darkTheme) {
                val database = remember { RoyalNoteDatabase.getInstance(context) }
                val repository = remember { NoteRepository(database.noteRecordDao()) }
                val parser = remember { OpenRouterService(settingsRepository) }
                val usageService = settingsDependencies.usageProvider
                val reflectionRepository = remember {
                    ReflectionRepository(
                        noteDao = database.noteRecordDao(),
                        reflectionDao = database.reflectionDao(),
                        memoryStore = MemoryFileStore(
                            File(context.filesDir, "reflection/MEMORY.md")
                        ),
                    )
                }
                val reflectionGateway = remember {
                    OpenRouterReflectionService(
                        settingsProvider = settingsRepository,
                        prompts = AssetReflectionPromptProvider(context),
                    )
                }

                val timelineViewModel: RecordTimelineViewModel = viewModel(
                    factory = RecordTimelineViewModelFactory(repository),
                )
                val importViewModel: ImportViewModel = viewModel(
                    factory = ImportViewModelFactory(parser, repository),
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModelFactory(settingsRepository, usageService),
                )
                val reflectionViewModel: ReflectionViewModel = viewModel(
                    factory = ReflectionViewModelFactory(reflectionRepository, reflectionGateway),
                )

                LaunchedEffect(Unit) {
                    SeedData.seedIfEmpty(repository)
                }

                val uiState by timelineViewModel.uiState.collectAsStateWithLifecycle()
                val importUiState by importViewModel.uiState.collectAsStateWithLifecycle()
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val reflectionUiState by reflectionViewModel.uiState.collectAsStateWithLifecycle()
                var keyVisible by rememberSaveable { mutableStateOf(false) }

                RoyalNoteNavigation(
                    homeContent = { onImport ->
                        RoyalNoteApp(
                            uiState = uiState,
                            onEventTextChange = timelineViewModel::updateEventText,
                            onMoodSelected = timelineViewModel::selectMood,
                            onMoodNoteChange = timelineViewModel::updateMoodNote,
                            onStartedAtChange = timelineViewModel::updateStartedAt,
                            onEndedAtChange = timelineViewModel::updateEndedAt,
                            onSetTimeToNow = timelineViewModel::setNewRecordTimeToNow,
                            onEditEventTextChange = timelineViewModel::updateEditEventText,
                            onEditMoodSelected = timelineViewModel::selectEditMood,
                            onEditMoodNoteChange = timelineViewModel::updateEditMoodNote,
                            onEditStartedAtChange = timelineViewModel::updateEditStartedAt,
                            onEditEndedAtChange = timelineViewModel::updateEditEndedAt,
                            onSave = timelineViewModel::save,
                            onEdit = timelineViewModel::startEditing,
                            onCancelEdit = timelineViewModel::cancelEditing,
                            onDelete = timelineViewModel::delete,
                            onMessageShown = timelineViewModel::clearMessage,
                            onImportClick = onImport,
                        )
                    },
                    analysisContent = {
                        AnalysisScreen(
                            uiState = reflectionUiState,
                            onVisible = reflectionViewModel::onScreenVisible,
                            onSelectThread = reflectionViewModel::selectThread,
                            onSelectToday = reflectionViewModel::selectToday,
                            onRetryGeneration = reflectionViewModel::retryGeneration,
                            onInputChange = reflectionViewModel::updateInputText,
                            onSend = reflectionViewModel::sendMessage,
                            onRetryMessage = reflectionViewModel::retryMessage,
                            onAcceptCandidate = reflectionViewModel::acceptCandidate,
                            onRejectCandidate = reflectionViewModel::rejectCandidate,
                            onUpdateMemory = reflectionViewModel::updateMemory,
                            onDeleteMemory = reflectionViewModel::deleteMemory,
                            onTerminateMemory = reflectionViewModel::terminateMemory,
                            onAcceptExperiment = reflectionViewModel::acceptExperiment,
                            onSkipExperiment = reflectionViewModel::skipExperiment,
                            onMessageShown = reflectionViewModel::clearMessage,
                        )
                    },
                    settingsContent = {
                        SettingsScreen(
                            uiState = settingsUiState,
                            keyVisible = keyVisible,
                            onApiKeyChange = settingsViewModel::updateApiKey,
                            onToggleKeyVisibility = { keyVisible = !keyVisible },
                            onThemeModeSelected = settingsViewModel::selectThemeMode,
                            onModelSelected = settingsViewModel::selectModel,
                            onEffortSelected = settingsViewModel::selectEffort,
                            onRefreshUsage = settingsViewModel::refreshUsage,
                            onVisible = settingsViewModel::onScreenVisible,
                        )
                    },
                    importContent = { onBack ->
                        ImportScreen(
                            uiState = importUiState,
                            onTextChange = importViewModel::updateText,
                            onImportClick = importViewModel::importRecords,
                            onBack = {
                                importViewModel.resetState()
                                onBack()
                            },
                            onMessageShown = importViewModel::clearMessage,
                            onSuccessConfirmed = {
                                importViewModel.dismissSuccessDialog()
                                importViewModel.resetState()
                                onBack()
                            },
                        )
                    },
                )
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
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    onSetTimeToNow: () -> Unit,
    onEditEventTextChange: (String) -> Unit,
    onEditMoodSelected: (String?) -> Unit,
    onEditMoodNoteChange: (String) -> Unit,
    onEditStartedAtChange: (Long) -> Unit,
    onEditEndedAtChange: (Long) -> Unit,
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "起居注",
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                actions = {
                    TextButton(
                        onClick = onImportClick,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("导入", fontFamily = FontFamily.Serif)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val horizontalPadding = if (maxWidth <= 375.dp) 12.dp else 16.dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("recordTimeline"),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    RecordEditor(
                        eventText = uiState.eventText,
                        selectedMood = uiState.selectedMood,
                        moodNote = uiState.moodNote,
                        startedAt = uiState.startedAt,
                        endedAt = uiState.endedAt,
                        zoneId = ZoneId.systemDefault(),
                        title = "速录一则",
                        saveLabel = "入录",
                        saveEnabled = !uiState.isSaving,
                        showCancel = false,
                        onEventTextChange = onEventTextChange,
                        onMoodSelected = onMoodSelected,
                        onMoodNoteChange = onMoodNoteChange,
                        onStartedAtChange = onStartedAtChange,
                        onEndedAtChange = onEndedAtChange,
                        onSetTimeToNow = onSetTimeToNow,
                        onSave = onSave,
                        onCancelEdit = {},
                    )
                }
                if (uiState.timelineDays.isEmpty()) {
                    item { EmptyTimeline() }
                } else {
                    uiState.timelineDays.forEach { day ->
                        item { TimelineHeader(day.label) }
                        itemsIndexed(day.records, key = { _, record -> record.id }) { index, record ->
                            if (uiState.editingRecord?.id == record.id) {
                                Box(modifier = Modifier.padding(vertical = 10.dp)) {
                                    RecordEditor(
                                        eventText = uiState.editEventText,
                                        selectedMood = uiState.editSelectedMood,
                                        moodNote = uiState.editMoodNote,
                                        startedAt = uiState.editStartedAt ?: record.startedAt,
                                        endedAt = uiState.editEndedAt ?: record.endedAt,
                                        zoneId = recordZoneId(record),
                                        title = "修订此则",
                                        saveLabel = "改毕入录",
                                        saveEnabled = !uiState.isSaving,
                                        showCancel = true,
                                        onEventTextChange = onEditEventTextChange,
                                        onMoodSelected = onEditMoodSelected,
                                        onMoodNoteChange = onEditMoodNoteChange,
                                        onStartedAtChange = onEditStartedAtChange,
                                        onEndedAtChange = onEditEndedAtChange,
                                        onSetTimeToNow = null,
                                        onSave = onSave,
                                        onCancelEdit = onCancelEdit,
                                    )
                                }
                            } else {
                                RecordCard(
                                    record = record,
                                    isLast = index == day.records.lastIndex,
                                    onEdit = onEdit,
                                    onDelete = onDelete,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditor(
    eventText: String,
    selectedMood: String?,
    moodNote: String,
    startedAt: Long,
    endedAt: Long,
    zoneId: ZoneId,
    title: String,
    saveLabel: String,
    saveEnabled: Boolean,
    showCancel: Boolean,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    onSetTimeToNow: (() -> Unit)?,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = outlineColor,
                shape = CardShape,
            )
            .drawWithContent {
                drawContent()
                val inset = 4.dp.toPx()
                drawRoundRect(
                    color = outlineColor.copy(alpha = 0.55f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(x = 0f, y = 18.dp.toPx()),
                    size = Size(width = 3.dp.toPx(), height = 44.dp.toPx()),
                )
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(1.dp)
                        .background(primaryColor)
                )
            }
            OutlinedTextField(
                value = eventText,
                onValueChange = onEventTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("记录此刻，言简意赅……") },
                supportingText = {
                    Text(
                        text = "${eventText.length} 字",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                minLines = 2,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
            TimeRangeFields(
                startedAt = startedAt,
                endedAt = endedAt,
                onStartedAtChange = onStartedAtChange,
                onEndedAtChange = onEndedAtChange,
                onSetToNow = onSetTimeToNow,
                zoneId = zoneId,
            )
            MoodSelector(selectedMood = selectedMood, onMoodSelected = onMoodSelected)
            if (selectedMood != null) {
                OutlinedTextField(
                    value = moodNote,
                    onValueChange = onMoodNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("心绪补述，可留白") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onSave()
                    },
                    enabled = saveEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(saveLabel, fontWeight = FontWeight.Bold)
                }
                if (showCancel) {
                    TextButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("作罢") }
                }
            }
        }
    }
}

@Composable
private fun MoodSelector(
    selectedMood: String?,
    onMoodSelected: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("心绪", style = MaterialTheme.typography.labelLarge)
            Text(
                " · 可留白",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemWidth = maxWidth / MoodLabels.ALL.size
            Row(modifier = Modifier.fillMaxWidth()) {
                MoodLabels.ALL.forEach { mood ->
                    val isSelected = selectedMood == mood
                    val iconSize = if (itemWidth < 44.dp) 30.dp else 34.dp
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .heightIn(min = 58.dp)
                            .semantics {
                                role = Role.Button
                                selected = isSelected
                                contentDescription = "$mood，${if (isSelected) "已选择" else "未选择"}"
                            }
                            .clickable {
                                onMoodSelected(if (isSelected) null else mood)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(moodIconResource(mood)),
                                contentDescription = null,
                                modifier = Modifier.size(if (iconSize < 34.dp) 18.dp else 21.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(
                            mood,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(label: String) {
    Column(
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun RecordCard(
    record: NoteRecord,
    isLast: Boolean,
    onEdit: (NoteRecord) -> Unit,
    onDelete: (NoteRecord) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    val moodColorValue = moodColor(record.moodTag)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Text(
            text = formatRecordTimeRange(record.startedAt, record.endedAt, recordZoneId(record)),
            modifier = Modifier
                .width(50.dp)
                .padding(top = 8.dp, end = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val nodeY = 13.dp.toPx()
                    val terminalY = (size.height - 14.dp.toPx()).coerceAtLeast(nodeY)
                    drawLine(
                        color = outlineColor,
                        start = Offset(x, if (isLast) nodeY else 0f),
                        end = Offset(x, if (isLast) terminalY else size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawCircle(
                        color = backgroundColor,
                        radius = 6.dp.toPx(),
                        center = Offset(x, nodeY),
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(x, nodeY),
                    )
                    if (isLast && terminalY > nodeY) {
                        drawCircle(
                            color = backgroundColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, terminalY),
                        )
                        drawCircle(
                            color = outlineColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(x, terminalY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp, bottom = if (isLast) 2.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    record.eventText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Box {
                    IconButton(
                        onClick = { showActions = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert_24),
                            contentDescription = "更多记录操作",
                            tint = onSurfaceVariantColor,
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("修订") },
                            onClick = {
                                showActions = false
                                onEdit(record)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("抹去") },
                            onClick = {
                                showActions = false
                                showDeleteDialog = true
                            },
                        )
                    }
                }
            }
            if (!record.moodNote.isNullOrBlank()) {
                Text(
                    record.moodNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariantColor,
                )
            }
            if (record.moodTag != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        painter = painterResource(moodIconResource(record.moodTag)),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = moodColorValue ?: primaryColor,
                    )
                    Text(
                        record.moodTag,
                        style = MaterialTheme.typography.labelMedium,
                        color = moodColorValue ?: primaryColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = outlineColor.copy(alpha = 0.75f),
                )
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

private fun moodIconResource(mood: String): Int = when (mood) {
    "开心" -> R.drawable.ic_mood_happy_24
    "满足" -> R.drawable.ic_mood_satisfied_24
    "平静" -> R.drawable.ic_mood_calm_24
    "疲惫" -> R.drawable.ic_mood_tired_24
    "烦躁" -> R.drawable.ic_mood_irritated_24
    "低落" -> R.drawable.ic_mood_low_24
    "焦虑" -> R.drawable.ic_mood_anxious_24
    else -> R.drawable.ic_mood_calm_24
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

private fun recordZoneId(record: NoteRecord): ZoneId = runCatching {
    ZoneId.of(record.zoneId)
}.getOrDefault(ZoneId.systemDefault())

@Preview(name = "首页 · 430 浅色", widthDp = 430, heightDp = 900, showBackground = true)
@Preview(
    name = "首页 · 430 深色",
    widthDp = 430,
    heightDp = 900,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "首页 · 360 窄屏", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun RoyalNotePreview() {
    val previewTime = System.currentTimeMillis()
    RoyalNoteTheme {
        RoyalNoteApp(
            uiState = RecordTimelineUiState(
                eventText = "整理书桌",
                selectedMood = "平静",
                moodNote = "心里安稳了一点",
                startedAt = previewTime,
                endedAt = previewTime,
                timelineDays = listOf(
                    TimelineDay(
                        label = "今日",
                        records = listOf(
                            NoteRecord(
                                id = 1,
                                eventText = "读了半晌书",
                                moodTag = "满足",
                                moodNote = null,
                                startedAt = previewTime,
                                endedAt = previewTime,
                                eventDate = java.time.LocalDate.now().toString(),
                                zoneId = ZoneId.systemDefault().id,
                                source = com.example.royalnote.data.RecordSources.MANUAL,
                                importBatchId = null,
                                importOrdinal = null,
                                createdAt = previewTime,
                                updatedAt = previewTime,
                            )
                        ),
                    )
                ),
            ),
            onEventTextChange = {},
            onMoodSelected = {},
            onMoodNoteChange = {},
            onStartedAtChange = {},
            onEndedAtChange = {},
            onSetTimeToNow = {},
            onEditEventTextChange = {},
            onEditMoodSelected = {},
            onEditMoodNoteChange = {},
            onEditStartedAtChange = {},
            onEditEndedAtChange = {},
            onSave = {},
            onEdit = {},
            onCancelEdit = {},
            onDelete = {},
            onMessageShown = {},
            onImportClick = {},
        )
    }
}
