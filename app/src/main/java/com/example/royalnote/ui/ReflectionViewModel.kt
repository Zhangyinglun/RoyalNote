package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.reflection.CandidatePolicy
import com.example.royalnote.reflection.CandidateStatus
import com.example.royalnote.reflection.ConversationContextBudget
import com.example.royalnote.reflection.CrisisDetector
import com.example.royalnote.reflection.DailyReflectionEntity
import com.example.royalnote.reflection.ExperimentStatus
import com.example.royalnote.reflection.GENERIC_SAFETY_MESSAGE
import com.example.royalnote.reflection.MemoryCandidateEntity
import com.example.royalnote.reflection.MemoryCategory
import com.example.royalnote.reflection.MemoryDecision
import com.example.royalnote.reflection.MemoryEntry
import com.example.royalnote.reflection.MemoryPolicy
import com.example.royalnote.reflection.MessageDeliveryState
import com.example.royalnote.reflection.ReflectionAiGateway
import com.example.royalnote.reflection.ReflectionChatInput
import com.example.royalnote.reflection.ReflectionContextBuilder
import com.example.royalnote.reflection.ReflectionConversationMessage
import com.example.royalnote.reflection.ReflectionExperiment
import com.example.royalnote.reflection.ReflectionExperimentStateEntity
import com.example.royalnote.reflection.ReflectionGenerationInput
import com.example.royalnote.reflection.ReflectionHistoryItem
import com.example.royalnote.reflection.ReflectionMessageEntity
import com.example.royalnote.reflection.ReflectionMessageRole
import com.example.royalnote.reflection.ReflectionOperations
import com.example.royalnote.reflection.ReflectionPeriod
import com.example.royalnote.reflection.ReflectionRecordSnapshot
import com.example.royalnote.reflection.ReflectionThreadEntity
import com.example.royalnote.reflection.SevenDayReflection
import com.example.royalnote.reflection.emptyReflection
import com.example.royalnote.reflection.validateReflection
import com.example.royalnote.reflection.renderMemory
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ReflectionUiState(
    val todayThreadDate: String = "",
    val selectedThreadDate: String = "",
    val reflection: SevenDayReflection? = null,
    val cachedReflection: SevenDayReflection? = null,
    val cachedReflectionThreadDate: String? = null,
    val isGenerating: Boolean = false,
    val generationError: String? = null,
    val isMissingApiKey: Boolean = false,
    val history: List<ReflectionHistoryItem> = emptyList(),
    val messages: List<ReflectionMessageEntity> = emptyList(),
    val candidates: List<MemoryCandidateEntity> = emptyList(),
    val pendingCandidates: List<MemoryCandidateEntity> = emptyList(),
    val experimentStates: Map<String, ReflectionExperimentStateEntity> = emptyMap(),
    val memoryEntries: List<MemoryEntry> = emptyList(),
    val memoryCharacterCount: Int = 0,
    val inputText: String = "",
    val isSending: Boolean = false,
    val message: String? = null,
)

class ReflectionViewModel(
    private val repository: ReflectionOperations,
    private val aiGateway: ReflectionAiGateway,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ViewModel() {
    private var today = LocalDate.now(clock).toString()
    private val selectedThread = MutableStateFlow(today)
    private val mutableUiState = MutableStateFlow(
        ReflectionUiState(todayThreadDate = today, selectedThreadDate = today)
    )
    val uiState: StateFlow<ReflectionUiState> = mutableUiState.asStateFlow()
    private var generationJob: Job? = null
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            repository.loadMemory()
        }
        viewModelScope.launch {
            repository.memoryEntries.collect { entries ->
                update {
                    copy(
                        memoryEntries = entries,
                        memoryCharacterCount = renderMemory(entries).length,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeHistory().collect { history ->
                update { copy(history = history) }
            }
        }
        viewModelScope.launch {
            repository.observePendingCandidates().collect { candidates ->
                update { copy(pendingCandidates = candidates) }
            }
        }
        viewModelScope.launch {
            selectedThread.collectLatest { threadDate ->
                bindThread(threadDate)
            }
        }
    }

    fun onScreenVisible() {
        val currentToday = LocalDate.now(clock).toString()
        if (currentToday != today) {
            generationJob?.cancel()
            generationJob = null
            today = currentToday
            selectedThread.value = currentToday
            update {
                copy(
                    todayThreadDate = currentToday,
                    selectedThreadDate = currentToday,
                    reflection = null,
                    cachedReflection = null,
                    cachedReflectionThreadDate = null,
                    isGenerating = false,
                    generationError = null,
                    isMissingApiKey = false,
                    messages = emptyList(),
                    candidates = emptyList(),
                    experimentStates = emptyMap(),
                )
            }
        }
        ensureTodayReflection()
    }

    fun selectThread(threadDate: String) {
        if (threadDate == selectedThread.value) return
        selectedThread.value = threadDate
    }

    fun selectToday() = selectThread(today)

    fun updateInputText(value: String) {
        if (value.length <= MAX_CHAT_INPUT_CHARACTERS) update { copy(inputText = value) }
    }

    fun clearMessage() = update { copy(message = null) }

    fun retryGeneration() = ensureTodayReflection()

    fun sendMessage() {
        if (sendJob?.isActive == true) return
        val content = uiState.value.inputText.trim()
        if (content.isBlank()) return
        val threadDate = uiState.value.selectedThreadDate
        sendJob = viewModelScope.launch {
            val reflection = repository.reflectionFor(threadDate)
            if (reflection == null) {
                update { copy(message = "省察尚未生成，暂不能开始对话") }
                return@launch
            }
            val messageId = repository.insertMessage(
                ReflectionMessageEntity(
                    threadDate = threadDate,
                    role = ReflectionMessageRole.USER.wireValue,
                    content = content,
                    deliveryState = MessageDeliveryState.SENDING.name,
                    createdAt = clock.millis(),
                )
            )
            update { copy(inputText = "", isSending = true) }
            sendExistingMessage(messageId, threadDate, content)
        }
    }

    fun retryMessage(messageId: Long) {
        if (sendJob?.isActive == true) return
        sendJob = viewModelScope.launch {
            val message = repository.messagesFor(uiState.value.selectedThreadDate)
                .firstOrNull { it.id == messageId && it.role == ReflectionMessageRole.USER.wireValue }
                ?: return@launch
            repository.updateMessageState(message.id, MessageDeliveryState.SENDING)
            update { copy(isSending = true) }
            sendExistingMessage(message.id, message.threadDate, message.content)
        }
    }

    fun acceptCandidate(candidateId: Long) {
        viewModelScope.launch {
            val candidate = repository.candidate(candidateId) ?: return@launch
            if (candidate.status != CandidateStatus.PENDING.name) return@launch
            val category = MemoryCategory.fromWireValue(candidate.category) ?: return@launch
            val entry = repository.addMemory(category, candidate.content)
            if (entry == null) {
                update { copy(message = "长期记忆已接近容量上限，请先修订现有内容") }
            } else {
                repository.updateCandidateStatus(candidate.id, CandidateStatus.ACCEPTED, entry.id)
                update { copy(message = "已收入长期记忆") }
            }
        }
    }

    fun rejectCandidate(candidateId: Long) {
        viewModelScope.launch {
            repository.updateCandidateStatus(candidateId, CandidateStatus.REJECTED, null)
        }
    }

    fun updateMemory(id: String, content: String) {
        viewModelScope.launch {
            if (!repository.updateMemory(id, content)) {
                update { copy(message = "记忆修订未成，请检查内容长度") }
            }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun terminateMemory(id: String) {
        viewModelScope.launch {
            if (repository.terminateMemory(id, clock.millis())) {
                update { copy(message = "已终止这项尝试") }
            }
        }
    }

    fun acceptExperiment(experiment: ReflectionExperiment) {
        val threadDate = uiState.value.selectedThreadDate
        viewModelScope.launch {
            val memoryText = buildString {
                append(experiment.title.trim())
                append("：")
                append(experiment.action.trim())
                if (experiment.frequency.isNotBlank()) append("；频率：${experiment.frequency.trim()}")
                if (experiment.observation.isNotBlank()) append("；观察：${experiment.observation.trim()}")
            }
            val entry = repository.addMemory(MemoryCategory.ACTION, memoryText)
            if (entry == null) {
                update { copy(message = "长期记忆已接近容量上限，请先修订现有内容") }
                return@launch
            }
            repository.saveExperimentState(
                ReflectionExperimentStateEntity(
                    threadDate = threadDate,
                    experimentId = experiment.id,
                    status = ExperimentStatus.ACCEPTED.name,
                    title = experiment.title,
                    action = experiment.action,
                    frequency = experiment.frequency,
                    observation = experiment.observation,
                    memoryEntryId = entry.id,
                    updatedAt = clock.millis(),
                )
            )
            update { copy(message = "已把这项尝试收入长期记忆") }
        }
    }

    fun skipExperiment(experiment: ReflectionExperiment) {
        val threadDate = uiState.value.selectedThreadDate
        viewModelScope.launch {
            repository.saveExperimentState(
                ReflectionExperimentStateEntity(
                    threadDate = threadDate,
                    experimentId = experiment.id,
                    status = ExperimentStatus.SKIPPED.name,
                    title = experiment.title,
                    action = experiment.action,
                    frequency = experiment.frequency,
                    observation = experiment.observation,
                    updatedAt = clock.millis(),
                )
            )
        }
    }

    private suspend fun bindThread(threadDate: String) = coroutineScope {
        repository.markInterruptedMessagesFailed(threadDate)
        val stored = repository.reflectionFor(threadDate)
        update {
            copy(
                selectedThreadDate = threadDate,
                reflection = stored?.decodeReflection(),
                messages = emptyList(),
                candidates = emptyList(),
                experimentStates = emptyMap(),
                generationError = if (threadDate == today) generationError else null,
                isMissingApiKey = if (threadDate == today) isMissingApiKey else false,
            )
        }
        launch {
            repository.observeMessages(threadDate).collect { messages ->
                update { copy(messages = messages) }
            }
        }
        launch {
            repository.observeCandidates(threadDate).collect { candidates ->
                update { copy(candidates = candidates) }
            }
        }
        launch {
            repository.observeExperimentStates(threadDate).collect { states ->
                update { copy(experimentStates = states.associateBy { it.experimentId }) }
            }
        }
    }

    private fun ensureTodayReflection() {
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch {
            val existing = repository.reflectionFor(today)
            if (existing != null) {
                if (uiState.value.selectedThreadDate == today) {
                    update {
                        copy(
                            reflection = existing.decodeReflection(),
                            generationError = null,
                            cachedReflection = null,
                            cachedReflectionThreadDate = null,
                            isMissingApiKey = false,
                        )
                    }
                }
                return@launch
            }
            update {
                copy(
                    isGenerating = true,
                    generationError = null,
                    isMissingApiKey = false,
                )
            }
            val window = ReflectionContextBuilder.window(clock)
            val period = ReflectionPeriod(window.startDate.toString(), window.endDate.toString())
            try {
                val records = repository.recordsInDateRange(
                    window.startDate.toString(),
                    window.threadDate.toString(),
                )
                val snapshots = ReflectionContextBuilder.snapshots(records)
                val input = ReflectionGenerationInput(
                    period = period,
                    records = snapshots,
                    memoryMarkdown = repository.memoryMarkdown(),
                )
                val reflection = if (snapshots.isEmpty()) {
                    emptyReflection(period)
                } else {
                    validateReflection(aiGateway.generateReflection(input), input, clock.zone)
                }
                val now = clock.millis()
                val entity = DailyReflectionEntity(
                    threadDate = today,
                    periodStartDate = period.startDate,
                    periodEndDate = period.endDate,
                    reflectionJson = json.encodeToString(SevenDayReflection.serializer(), reflection),
                    recordSnapshotJson = json.encodeToString(
                        ListSerializer(ReflectionRecordSnapshot.serializer()),
                        snapshots,
                    ),
                    promptVersion = REFLECTION_PROMPT_VERSION,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                repository.saveReflection(
                    entity,
                    ReflectionThreadEntity(threadDate = today, updatedAt = now),
                )
                if (uiState.value.selectedThreadDate == today) {
                    update {
                        copy(
                            reflection = reflection,
                            isGenerating = false,
                            generationError = null,
                            cachedReflection = null,
                            cachedReflectionThreadDate = null,
                        )
                    }
                } else {
                    update { copy(isGenerating = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val cached = repository.latestReflectionBefore(today)
                val missingKey = error is MissingOpenRouterApiKeyException
                update {
                    copy(
                        isGenerating = false,
                        generationError = when {
                            missingKey -> "请先在设置中填写 OpenRouter API Key"
                            error is IOException -> "网络未通，今日省察尚未生成"
                            else -> "今日省察生成未成，请稍后再试"
                        },
                        isMissingApiKey = missingKey,
                        cachedReflection = cached?.decodeReflection(),
                        cachedReflectionThreadDate = cached?.threadDate,
                    )
                }
            }
        }
    }

    private suspend fun sendExistingMessage(
        userMessageId: Long,
        threadDate: String,
        content: String,
    ) {
        try {
            val reflectionEntity = repository.reflectionFor(threadDate)
                ?: error("Reflection is missing for $threadDate")
            if (CrisisDetector.isExplicitImmediateDanger(content)) {
                saveAssistantResponse(
                    userMessageId = userMessageId,
                    threadDate = threadDate,
                    reply = GENERIC_SAFETY_MESSAGE,
                    isSafety = true,
                    resultCandidates = emptyList(),
                    latestUserMessage = content,
                    safetyMode = "immediate_danger",
                )
                return
            }
            var thread = repository.threadFor(threadDate)
                ?: ReflectionThreadEntity(threadDate = threadDate, updatedAt = clock.millis())
            val allMessages = repository.messagesFor(threadDate)
                .filterNot {
                    it.role == ReflectionMessageRole.USER.wireValue &&
                        it.deliveryState == MessageDeliveryState.FAILED.name
                }
                .map { it.toConversationMessage() }
            if (ConversationContextBudget.shouldCompact(allMessages, thread.summarizedThroughMessageId)) {
                val chunk = ConversationContextBudget.compactionChunk(
                    allMessages,
                    thread.summarizedThroughMessageId,
                )
                if (chunk.isNotEmpty() && chunk.last().id < userMessageId) {
                    try {
                        val summary = aiGateway.compactConversation(thread.conversationSummary, chunk)
                        thread = thread.copy(
                            conversationSummary = summary,
                            summarizedThroughMessageId = chunk.last().id,
                            updatedAt = clock.millis(),
                        )
                        repository.saveThread(thread)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Falling back to the bounded recent-message window still prevents oversize requests.
                    }
                }
            }
            val recent = ConversationContextBudget.recentMessages(
                allMessages.filter { it.id > thread.summarizedThroughMessageId }
            )
            val reflection = reflectionEntity.decodeReflection()
            val snapshots = json.decodeFromString(
                ListSerializer(ReflectionRecordSnapshot.serializer()),
                reflectionEntity.recordSnapshotJson,
            )
            val result = aiGateway.chat(
                ReflectionChatInput(
                    threadDate = threadDate,
                    reflection = reflection,
                    records = snapshots,
                    memoryMarkdown = repository.memoryMarkdown(),
                    conversationSummary = thread.conversationSummary,
                    recentMessages = recent,
                )
            )
            val isSafety = result.safetyMode == "immediate_danger"
            val reply = if (isSafety) GENERIC_SAFETY_MESSAGE else result.reply.trim()
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Empty reflection reply")
            saveAssistantResponse(
                userMessageId = userMessageId,
                threadDate = threadDate,
                reply = reply,
                isSafety = isSafety,
                resultCandidates = result.memoryCandidates,
                latestUserMessage = content,
                safetyMode = result.safetyMode,
            )
        } catch (error: CancellationException) {
            repository.updateMessageState(userMessageId, MessageDeliveryState.FAILED)
            throw error
        } catch (error: Exception) {
            repository.updateMessageState(userMessageId, MessageDeliveryState.FAILED)
            update {
                copy(
                    message = when (error) {
                        is MissingOpenRouterApiKeyException -> "请先在设置中填写 OpenRouter API Key"
                        is IOException -> "消息未送达，请检查网络后重试"
                        else -> "消息未送达，请稍后重试"
                    }
                )
            }
        } finally {
            update { copy(isSending = false) }
        }
    }

    private suspend fun saveAssistantResponse(
        userMessageId: Long,
        threadDate: String,
        reply: String,
        isSafety: Boolean,
        resultCandidates: List<com.example.royalnote.reflection.ChatMemoryCandidate>,
        latestUserMessage: String,
        safetyMode: String,
    ) {
        val now = clock.millis()
        val assistantId = repository.insertMessage(
            ReflectionMessageEntity(
                threadDate = threadDate,
                role = ReflectionMessageRole.ASSISTANT.wireValue,
                content = reply.take(MAX_ASSISTANT_MESSAGE_CHARACTERS),
                deliveryState = MessageDeliveryState.SENT.name,
                isSafetyMessage = isSafety,
                createdAt = now,
            )
        )
        repository.updateMessageState(userMessageId, MessageDeliveryState.SENT)
        val entities = buildList {
            resultCandidates.take(MAX_CANDIDATES_PER_REPLY).forEach { candidate ->
                val category = MemoryCategory.fromWireValue(candidate.category) ?: return@forEach
                when (MemoryPolicy.decide(candidate, latestUserMessage, safetyMode)) {
                    MemoryDecision.REJECT -> Unit
                    MemoryDecision.CONFIRM -> add(
                        MemoryCandidateEntity(
                            threadDate = threadDate,
                            sourceMessageId = assistantId,
                            category = category.wireValue,
                            content = candidate.content.trim().take(500),
                            policy = CandidatePolicy.CONFIRM.name,
                            status = CandidateStatus.PENDING.name,
                            createdAt = now,
                        )
                    )
                    MemoryDecision.AUTO -> {
                        val entry = repository.addMemory(category, candidate.content)
                        add(
                            MemoryCandidateEntity(
                                threadDate = threadDate,
                                sourceMessageId = assistantId,
                                category = category.wireValue,
                                content = candidate.content.trim().take(500),
                                policy = CandidatePolicy.AUTO.name,
                                status = if (entry == null) {
                                    CandidateStatus.PENDING.name
                                } else {
                                    CandidateStatus.AUTO_SAVED.name
                                },
                                memoryEntryId = entry?.id,
                                createdAt = now,
                            )
                        )
                    }
                }
            }
        }
        if (entities.isNotEmpty()) repository.insertCandidates(entities)
    }

    private fun DailyReflectionEntity.decodeReflection(): SevenDayReflection =
        json.decodeFromString(SevenDayReflection.serializer(), reflectionJson)

    private fun ReflectionMessageEntity.toConversationMessage() = ReflectionConversationMessage(
        id = id,
        role = ReflectionMessageRole.fromWireValue(role),
        content = content,
    )

    private inline fun update(transform: ReflectionUiState.() -> ReflectionUiState) {
        mutableUiState.value = mutableUiState.value.transform()
    }

    companion object {
        const val REFLECTION_PROMPT_VERSION = "seven_day_reflection_v2"
        const val MAX_CHAT_INPUT_CHARACTERS = 4_000
        const val MAX_ASSISTANT_MESSAGE_CHARACTERS = 8_000
        const val MAX_CANDIDATES_PER_REPLY = 4
    }
}

class ReflectionViewModelFactory(
    private val repository: ReflectionOperations,
    private val aiGateway: ReflectionAiGateway,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReflectionViewModel(repository, aiGateway) as T
}
