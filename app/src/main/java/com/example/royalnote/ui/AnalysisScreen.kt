package com.example.royalnote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.royalnote.reflection.CandidateStatus
import com.example.royalnote.reflection.ExperimentStatus
import com.example.royalnote.reflection.MemoryCandidateEntity
import com.example.royalnote.reflection.MemoryCategory
import com.example.royalnote.reflection.MemoryEntry
import com.example.royalnote.reflection.MemoryFileStore
import com.example.royalnote.reflection.MessageDeliveryState
import com.example.royalnote.reflection.ReflectionBlindSpot
import com.example.royalnote.reflection.ReflectionExperiment
import com.example.royalnote.reflection.ReflectionExperimentStateEntity
import com.example.royalnote.reflection.ReflectionHistoryItem
import com.example.royalnote.reflection.ReflectionMessageEntity
import com.example.royalnote.reflection.ReflectionMessageRole
import com.example.royalnote.reflection.SevenDayReflection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val AnalysisCardShape = RoundedCornerShape(8.dp)
private val displayDateFormatter = DateTimeFormatter.ofPattern("M月d日")

private enum class AnalysisPage {
    THREAD,
    HISTORY,
    MEMORY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    uiState: ReflectionUiState = ReflectionUiState(),
    onVisible: () -> Unit = {},
    onSelectThread: (String) -> Unit = {},
    onSelectToday: () -> Unit = {},
    onRetryGeneration: () -> Unit = {},
    onInputChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onRetryMessage: (Long) -> Unit = {},
    onAcceptCandidate: (Long) -> Unit = {},
    onRejectCandidate: (Long) -> Unit = {},
    onUpdateMemory: (String, String) -> Unit = { _, _ -> },
    onDeleteMemory: (String) -> Unit = {},
    onTerminateMemory: (String) -> Unit = {},
    onAcceptExperiment: (ReflectionExperiment) -> Unit = {},
    onSkipExperiment: (ReflectionExperiment) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    var pageName by rememberSaveable { mutableStateOf(AnalysisPage.THREAD.name) }
    val page = AnalysisPage.valueOf(pageName)
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var deletingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var editingExperiment by remember { mutableStateOf<ReflectionExperiment?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = page != AnalysisPage.THREAD) {
        pageName = AnalysisPage.THREAD.name
    }
    LaunchedEffect(Unit) { onVisible() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
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
                        when (page) {
                            AnalysisPage.THREAD -> "七日省察"
                            AnalysisPage.HISTORY -> "往日省察"
                            AnalysisPage.MEMORY -> "长期记忆"
                        }
                    )
                },
                navigationIcon = {
                    if (page != AnalysisPage.THREAD) {
                        TextButton(onClick = { pageName = AnalysisPage.THREAD.name }) {
                            Text("返回")
                        }
                    }
                },
                actions = {
                    if (page == AnalysisPage.THREAD) {
                        TextButton(onClick = { pageName = AnalysisPage.HISTORY.name }) {
                            Text("往日")
                        }
                        TextButton(onClick = { pageName = AnalysisPage.MEMORY.name }) {
                            Text("记忆")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (page == AnalysisPage.THREAD) {
                ReflectionComposer(
                    value = uiState.inputText,
                    enabled = uiState.reflection != null && !uiState.isSending,
                    isSending = uiState.isSending,
                    onValueChange = onInputChange,
                    onSend = onSend,
                )
            }
        },
    ) { padding ->
        when (page) {
            AnalysisPage.THREAD -> ReflectionThreadContent(
                uiState = uiState,
                padding = padding,
                onRetryGeneration = onRetryGeneration,
                onRetryMessage = onRetryMessage,
                onAcceptCandidate = onAcceptCandidate,
                onRejectCandidate = onRejectCandidate,
                onAcceptExperiment = onAcceptExperiment,
                onEditExperiment = { editingExperiment = it },
                onSkipExperiment = onSkipExperiment,
            )
            AnalysisPage.HISTORY -> ReflectionHistoryContent(
                history = uiState.history,
                today = uiState.todayThreadDate,
                padding = padding,
                onSelect = { threadDate ->
                    if (threadDate == uiState.todayThreadDate) onSelectToday()
                    else onSelectThread(threadDate)
                    pageName = AnalysisPage.THREAD.name
                },
            )
            AnalysisPage.MEMORY -> ReflectionMemoryContent(
                entries = uiState.memoryEntries,
                characterCount = uiState.memoryCharacterCount,
                pendingCandidates = uiState.pendingCandidates,
                padding = padding,
                onAcceptCandidate = onAcceptCandidate,
                onRejectCandidate = onRejectCandidate,
                onEdit = { editingMemory = it },
                onDelete = { deletingMemory = it },
                onTerminate = onTerminateMemory,
            )
        }
    }

    editingMemory?.let { entry ->
        MemoryEditDialog(
            entry = entry,
            onDismiss = { editingMemory = null },
            onSave = { value ->
                onUpdateMemory(entry.id, value)
                editingMemory = null
            },
        )
    }
    deletingMemory?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text("删去这条记忆？") },
            text = { Text("这不会删除原始记录或聊天，但后续省察将不再使用它。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMemory(entry.id)
                    deletingMemory = null
                }) { Text("删去") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }) { Text("作罢") }
            },
        )
    }
    editingExperiment?.let { experiment ->
        ExperimentEditDialog(
            experiment = experiment,
            onDismiss = { editingExperiment = null },
            onAccept = {
                onAcceptExperiment(it)
                editingExperiment = null
            },
        )
    }
}

@Composable
private fun ReflectionThreadContent(
    uiState: ReflectionUiState,
    padding: PaddingValues,
    onRetryGeneration: () -> Unit,
    onRetryMessage: (Long) -> Unit,
    onAcceptCandidate: (Long) -> Unit,
    onRejectCandidate: (Long) -> Unit,
    onAcceptExperiment: (ReflectionExperiment) -> Unit,
    onEditExperiment: (ReflectionExperiment) -> Unit,
    onSkipExperiment: (ReflectionExperiment) -> Unit,
) {
    val listState = rememberLazyListState()
    var hasSentInThisComposition by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isSending) {
        if (uiState.isSending) {
            hasSentInThisComposition = true
        } else if (hasSentInThisComposition && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .testTag("reflectionThread"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReflectionHeading(
                threadDate = uiState.selectedThreadDate,
                today = uiState.todayThreadDate,
                reflection = uiState.reflection,
            )
        }
        if (uiState.isGenerating && uiState.selectedThreadDate == uiState.todayThreadDate) {
            item { ReflectionLoadingCard() }
        }
        uiState.generationError?.takeIf {
            uiState.selectedThreadDate == uiState.todayThreadDate
        }?.let { error ->
            item {
                ReflectionErrorCard(
                    message = error,
                    isMissingKey = uiState.isMissingApiKey,
                    onRetry = onRetryGeneration,
                )
            }
            uiState.cachedReflection?.let { cached ->
                item {
                    Text(
                        "最近一次成功回顾 · ${formatDate(uiState.cachedReflectionThreadDate.orEmpty())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                reflectionItems(
                    reflection = cached,
                    experimentStates = emptyMap(),
                    actionsEnabled = false,
                    onAcceptExperiment = {},
                    onEditExperiment = {},
                    onSkipExperiment = {},
                )
            }
        }
        uiState.reflection?.let { reflection ->
            reflectionItems(
                reflection = reflection,
                experimentStates = uiState.experimentStates,
                actionsEnabled = true,
                onAcceptExperiment = onAcceptExperiment,
                onEditExperiment = onEditExperiment,
                onSkipExperiment = onSkipExperiment,
            )
            item { SectionDivider("对话") }
            if (uiState.messages.isEmpty()) {
                item {
                    AssistantMessageCard(
                        text = reflection.reflectionQuestion.ifBlank {
                            "读到这里，你最想先聊哪一部分？我们可以只从一件小事开始。"
                        },
                        isSafety = false,
                    )
                }
            }
            items(uiState.messages, key = { "message-${it.id}" }) { message ->
                val candidates = uiState.candidates.filter { it.sourceMessageId == message.id }
                ReflectionMessageRow(
                    message = message,
                    candidates = candidates,
                    onRetry = { onRetryMessage(message.id) },
                    onAcceptCandidate = onAcceptCandidate,
                    onRejectCandidate = onRejectCandidate,
                )
            }
            if (uiState.isSending) {
                item { InkThinkingIndicator() }
            }
        }
        if (
            uiState.reflection == null &&
            !uiState.isGenerating &&
            uiState.generationError == null
        ) {
            item {
                EmptyAnalysisCard("今日省察尚未载入")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reflectionItems(
    reflection: SevenDayReflection,
    experimentStates: Map<String, ReflectionExperimentStateEntity>,
    actionsEnabled: Boolean,
    onAcceptExperiment: (ReflectionExperiment) -> Unit,
    onEditExperiment: (ReflectionExperiment) -> Unit,
    onSkipExperiment: (ReflectionExperiment) -> Unit,
) {
    if (reflection.coverage.recordCount == 0) {
        item {
            EmptyAnalysisCard("这七天尚无可供回望的起居记录。你仍可以从此刻想说的事开始。")
        }
        return
    }
    item { ReflectionOverviewCard(reflection) }
    val experiments = reflection.experiments.take(2)
    if (experiments.isNotEmpty()) {
        item { SectionDivider("尝试计划") }
        experiments.forEachIndexed { index, experiment ->
            item(key = "experiment-${experiment.id}-$index") {
                ExperimentPlanCard(
                    index = index,
                    experiment = experiment,
                    state = experimentStates[experiment.id],
                    actionsEnabled = actionsEnabled,
                    onAccept = onAcceptExperiment,
                    onEdit = onEditExperiment,
                    onSkip = onSkipExperiment,
                )
            }
        }
    }
}

@Composable
private fun ReflectionHeading(
    threadDate: String,
    today: String,
    reflection: SevenDayReflection?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (threadDate == today) "今日省察 · 回望昨日以前" else "往日省察 · ${formatDate(threadDate)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            reflection?.period?.let { "${formatDate(it.startDate)}至${formatDate(it.endDate)}" }
                ?: "七日回望",
            style = MaterialTheme.typography.headlineMedium,
        )
        TitleUnderline()
        if (reflection != null) {
            Text(
                "依据 ${reflection.coverage.recordCount} 则记录生成，回顾保存后不随原记录变化。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TitleUnderline() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
        Box(
            Modifier
                .size(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
        )
    }
}

@Composable
private fun ReflectionLoadingCard() {
    InkAnalysisCard(title = "正在整理七日之录") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                "先梳理事实，再温和地提出可能性。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReflectionErrorCard(
    message: String,
    isMissingKey: Boolean,
    onRetry: () -> Unit,
) {
    InkAnalysisCard(title = "今日省察未成") {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry, enabled = !isMissingKey) {
            Text(if (isMissingKey) "请从底部进入设置" else "再试一次")
        }
    }
}

@Composable
private fun ReflectionOverviewCard(reflection: SevenDayReflection) {
    InkAnalysisCard(title = "七日回望") {
        Text(
            reflection.summary
                .joinToString(separator = " ") { it.text.trim() }
                .ifBlank { "现有记录还不足以形成一段可靠的七日总结。" },
            style = MaterialTheme.typography.bodyMedium,
        )
        reflection.blindSpots.firstOrNull()?.let { blindSpot ->
            OverviewDivider()
            Text("另一种可能", style = MaterialTheme.typography.labelLarge)
            BlindSpotText(blindSpot)
        }
    }
}

@Composable
private fun BlindSpotText(item: ReflectionBlindSpot) {
    Text(item.hypothesis, style = MaterialTheme.typography.bodyMedium)
    if (item.alternativeExplanation.isNotBlank()) {
        Text(
            "另一种解释：${item.alternativeExplanation}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (item.uncertainty.isNotBlank()) {
        Text(
            "仍不确定：${item.uncertainty}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        item.question,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun OverviewDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    )
}

@Composable
private fun ExperimentPlanCard(
    index: Int,
    experiment: ReflectionExperiment,
    state: ReflectionExperimentStateEntity?,
    actionsEnabled: Boolean,
    onAccept: (ReflectionExperiment) -> Unit,
    onEdit: (ReflectionExperiment) -> Unit,
    onSkip: (ReflectionExperiment) -> Unit,
) {
    InkAnalysisCard(
        title = "尝试计划${if (index == 0) "一" else "二"} · ${experiment.title}"
    ) {
        Text(experiment.action, style = MaterialTheme.typography.bodyMedium)
        if (experiment.frequency.isNotBlank()) {
            Text(
                "频率：${experiment.frequency}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (experiment.observation.isNotBlank()) {
            Text(
                "观察：${experiment.observation}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state?.status) {
            ExperimentStatus.ACCEPTED.name -> StatusText("已收入长期记忆")
            ExperimentStatus.SKIPPED.name -> StatusText("本次已略过")
            ExperimentStatus.TERMINATED.name -> StatusText("这项尝试已终止")
            else -> if (actionsEnabled) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { onAccept(experiment) }) { Text("愿意试试") }
                    TextButton(onClick = { onEdit(experiment) }) { Text("先修订") }
                    TextButton(onClick = { onSkip(experiment) }) { Text("略过") }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun ReflectionMessageRow(
    message: ReflectionMessageEntity,
    candidates: List<MemoryCandidateEntity>,
    onRetry: () -> Unit,
    onAcceptCandidate: (Long) -> Unit,
    onRejectCandidate: (Long) -> Unit,
) {
    val isUser = message.role == ReflectionMessageRole.USER.wireValue
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            UserMessageBubble(message.content)
            if (message.deliveryState == MessageDeliveryState.FAILED.name) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "未送达",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        } else {
            AssistantMessageCard(message.content, message.isSafetyMessage)
            candidates.forEach { candidate ->
                MemoryCandidateCard(candidate, onAcceptCandidate, onRejectCandidate)
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Box(
        modifier = Modifier
            .widthIn(max = 330.dp)
            .background(MaterialTheme.colorScheme.secondary, AnalysisCardShape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondary)
    }
}

@Composable
private fun AssistantMessageCard(text: String, isSafety: Boolean) {
    val accent = if (isSafety) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
            .drawWithContent {
                drawContent()
                drawRect(
                    accent.copy(alpha = 0.65f),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            },
        shape = AnalysisCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text,
            modifier = Modifier.padding(start = 15.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MemoryCandidateCard(
    candidate: MemoryCandidateEntity,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(top = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, AnalysisCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
            .padding(10.dp),
    ) {
        Text(
            when (candidate.status) {
                CandidateStatus.PENDING.name -> "待确认记忆"
                CandidateStatus.AUTO_SAVED.name -> "已自动收录"
                CandidateStatus.ACCEPTED.name -> "已收入长期记忆"
                else -> "已略过"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(candidate.content, style = MaterialTheme.typography.bodyMedium)
        if (candidate.status == CandidateStatus.PENDING.name) {
            Row {
                TextButton(onClick = { onAccept(candidate.id) }) { Text("收录") }
                TextButton(onClick = { onReject(candidate.id) }) { Text("略过") }
            }
        }
    }
}

@Composable
private fun InkThinkingIndicator() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, AnalysisCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
            .padding(horizontal = 13.dp, vertical = 11.dp)
            .testTag("reflectionThinking"),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape)
            )
        }
    }
}

@Composable
private fun ReflectionComposer(
    value: String,
    enabled: Boolean,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag("reflectionInput"),
            enabled = enabled,
            placeholder = { Text(if (enabled) "写下此刻想说的……" else "省察生成后可开始对话") },
            minLines = 1,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSend() }),
        )
        Button(
            onClick = onSend,
            enabled = enabled && value.isNotBlank() && !isSending,
            modifier = Modifier.height(48.dp),
            shape = AnalysisCardShape,
        ) { Text("发送") }
    }
}

@Composable
private fun ReflectionHistoryContent(
    history: List<ReflectionHistoryItem>,
    today: String,
    padding: PaddingValues,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("每日一篇，回顾与对话都只存于本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            TitleUnderline()
        }
        if (history.isEmpty()) {
            item { EmptyAnalysisCard("尚无往日省察") }
        } else {
            items(history, key = { it.threadDate }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
                        .clickable { onSelect(item.threadDate) },
                    shape = AnalysisCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                if (item.threadDate == today) "今日 · ${formatDate(item.threadDate)}"
                                else formatDate(item.threadDate),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "回顾 ${formatDate(item.periodStartDate)}至${formatDate(item.periodEndDate)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${item.messageCount} 条对话",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflectionMemoryContent(
    entries: List<MemoryEntry>,
    characterCount: Int,
    pendingCandidates: List<MemoryCandidateEntity>,
    padding: PaddingValues,
    onAcceptCandidate: (Long) -> Unit,
    onRejectCandidate: (Long) -> Unit,
    onEdit: (MemoryEntry) -> Unit,
    onDelete: (MemoryEntry) -> Unit,
    onTerminate: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "这里只保留经过确认、对未来仍有帮助的内容，不保存完整聊天或诊断。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            TitleUnderline()
            Text(
                "已使用 $characterCount / ${MemoryFileStore.MAX_MEMORY_CHARACTERS} 字",
                style = MaterialTheme.typography.labelMedium,
                color = if (characterCount >= MemoryFileStore.MAX_MEMORY_CHARACTERS * 4 / 5) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (characterCount >= MemoryFileStore.MAX_MEMORY_CHARACTERS * 4 / 5) {
                Text(
                    "长期记忆已接近上限，建议合并重复内容或删去失效条目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (pendingCandidates.isNotEmpty()) {
            item { SectionDivider("待确认") }
            items(pendingCandidates, key = { "pending-${it.id}" }) { candidate ->
                MemoryCandidateCard(candidate, onAcceptCandidate, onRejectCandidate)
            }
        }
        if (entries.isEmpty()) {
            item { EmptyAnalysisCard("尚无长期记忆。只有明确接受或确认的内容才会收入这里。") }
        } else {
            MemoryCategory.entries.forEach { category ->
                val categoryEntries = entries.filter { it.category == category }
                if (categoryEntries.isNotEmpty()) {
                    item { SectionDivider(category.heading) }
                    items(categoryEntries, key = { it.id }) { entry ->
                        MemoryEntryCard(entry, onEdit, onDelete, onTerminate)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryEntryCard(
    entry: MemoryEntry,
    onEdit: (MemoryEntry) -> Unit,
    onDelete: (MemoryEntry) -> Unit,
    onTerminate: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape),
        shape = AnalysisCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(entry.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { onEdit(entry) }) { Text("修订") }
                if (
                    entry.category in setOf(MemoryCategory.ACTION, MemoryCategory.GOAL) &&
                    entry.status == "进行中"
                ) {
                    TextButton(onClick = { onTerminate(entry.id) }) { Text("终止") }
                }
                TextButton(onClick = { onDelete(entry) }) { Text("删去") }
            }
        }
    }
}

@Composable
private fun MemoryEditDialog(
    entry: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(entry.id) { mutableStateOf(entry.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修订${entry.category.heading}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("存下") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("作罢") } },
    )
}

@Composable
private fun ExperimentEditDialog(
    experiment: ReflectionExperiment,
    onDismiss: () -> Unit,
    onAccept: (ReflectionExperiment) -> Unit,
) {
    var title by remember(experiment.id) { mutableStateOf(experiment.title) }
    var action by remember(experiment.id) { mutableStateOf(experiment.action) }
    var frequency by remember(experiment.id) { mutableStateOf(experiment.frequency) }
    var observation by remember(experiment.id) { mutableStateOf(experiment.observation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修订小尝试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("名称") })
                OutlinedTextField(action, { action = it.take(400) }, label = { Text("怎么做") }, minLines = 2)
                OutlinedTextField(frequency, { frequency = it.take(160) }, label = { Text("频率") })
                OutlinedTextField(observation, { observation = it.take(240) }, label = { Text("观察什么") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAccept(experiment.copy(
                        title = title.trim(),
                        action = action.trim(),
                        frequency = frequency.trim(),
                        observation = observation.trim(),
                    ))
                },
                enabled = title.isNotBlank() && action.isNotBlank(),
            ) { Text("愿意试试") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("作罢") } },
    )
}

@Composable
private fun InkAnalysisCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
            .drawWithContent {
                drawContent()
                drawRect(
                    accent.copy(alpha = 0.65f),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            },
        shape = AnalysisCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 13.dp, top = 13.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SectionDivider(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
    }
}

@Composable
private fun EmptyAnalysisCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, AnalysisCardShape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(40.dp).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(value: String): String = runCatching {
    LocalDate.parse(value).format(displayDateFormatter)
}.getOrDefault(value)
