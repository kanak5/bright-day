package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JournalAiRepository
import com.example.data.JournalRepository
import com.example.media.AudioJournalController
import com.example.media.PlayerState
import com.example.media.RecorderState
import com.example.model.DailyEmotionAccumulation
import com.example.model.DashboardPeriod
import com.example.model.EmotionCategory
import com.example.model.EmotionCountStat
import com.example.model.EntryType
import com.example.model.JournalEntry
import com.example.model.TextScaleLevel
import com.example.model.UserPreferences
import com.example.model.WeeklySummary
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "BrightDayVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface PromptState {
    data object Idle : PromptState
    data object Loading : PromptState
    data class Ready(val prompts: List<String>, val activeIndex: Int = 0) : PromptState {
        val currentPrompt: String get() = prompts.getOrElse(activeIndex) { prompts.firstOrNull().orEmpty() }
    }
    data class Error(val message: String) : PromptState
}

sealed interface ActionState {
    data object Idle : ActionState
    data class Working(val statusMessage: String) : ActionState
    data class Success(val message: String) : ActionState
    data class Error(val message: String) : ActionState
}

class BrightDayViewModel(
    private val repository: JournalRepository,
    private val aiRepository: JournalAiRepository,
    val audioController: AudioJournalController,
    private val currentUserId: String
) : ViewModel() {

    val entriesState: StateFlow<UiState<List<JournalEntry>>> =
        repository.observeEntries(currentUserId)
            .map<List<JournalEntry>, UiState<List<JournalEntry>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing journal entries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Unable to load your memories right now."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val weeklySummariesState: StateFlow<UiState<List<WeeklySummary>>> =
        repository.observeWeeklySummaries(currentUserId)
            .map<List<WeeklySummary>, UiState<List<WeeklySummary>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing weekly summaries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Unable to load weekly summaries."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val preferencesState: StateFlow<UserPreferences> =
        repository.observePreferences(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing preferences", firestoreError ?: error)
                emit(UserPreferences(userId = currentUserId))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UserPreferences(userId = currentUserId)
            )

    val recorderState: StateFlow<RecorderState> = audioController.recorderState
    val playerState: StateFlow<PlayerState> = audioController.playerState
    val speakingId: StateFlow<String?> = audioController.speakingId

    private val _promptState = MutableStateFlow<PromptState>(PromptState.Idle)
    val promptState: StateFlow<PromptState> = _promptState.asStateFlow()

    private val _entryActionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val entryActionState: StateFlow<ActionState> = _entryActionState.asStateFlow()

    private val _weeklySummaryAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val weeklySummaryAction: StateFlow<ActionState> = _weeklySummaryAction.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(DashboardPeriod.WEEK)
    val selectedPeriod: StateFlow<DashboardPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedEmotionFilter = MutableStateFlow<EmotionCategory?>(null)
    val selectedEmotionFilter: StateFlow<EmotionCategory?> = _selectedEmotionFilter.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var promptJob: Job? = null
    private var weeklySummaryJob: Job? = null
    private var audioAnalysisJob: Job? = null

    init {
        fetchJournalingPrompts()
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearEntryActionState() {
        audioAnalysisJob?.cancel()
        audioAnalysisJob = null
        _entryActionState.value = ActionState.Idle
    }

    fun clearWeeklySummaryAction() {
        _weeklySummaryAction.value = ActionState.Idle
    }

    fun setDashboardPeriod(period: DashboardPeriod) {
        _selectedPeriod.value = period
    }

    fun setEmotionFilter(emotion: EmotionCategory?) {
        _selectedEmotionFilter.value = if (_selectedEmotionFilter.value == emotion) null else emotion
    }

    fun fetchJournalingPrompts(forceRefresh: Boolean = false) {
        val current = _promptState.value
        if (!forceRefresh && current is PromptState.Loading) {
            return
        }
        if (!forceRefresh && current is PromptState.Ready && current.prompts.size > 1) {
            val nextIdx = (current.activeIndex + 1) % current.prompts.size
            if (nextIdx != 0) {
                _promptState.value = current.copy(activeIndex = nextIdx)
                return
            }
        }
        promptJob?.cancel()
        promptJob = viewModelScope.launch {
            _promptState.value = PromptState.Loading
            val entries = (entriesState.value as? UiState.Success)?.data.orEmpty()
            val recentTopics = entries.take(10).flatMap { it.keyTopics }
            val result = aiRepository.generateStarterPrompts(
                timeOfDay = currentTimeOfDayLabel(),
                recentTopics = recentTopics
            )
            result.fold(
                onSuccess = { prompts ->
                    _promptState.value = PromptState.Ready(prompts = prompts, activeIndex = 0)
                },
                onFailure = { err ->
                    _promptState.value = PromptState.Error(
                        err.localizedMessage ?: "Could not load journaling ideas. Tap Try Again."
                    )
                }
            )
        }
    }

    fun analyzeAndSaveAudioEntry(
        audioFile: File,
        durationSec: Int,
        promptUsed: String?,
        onSaved: () -> Unit
    ) {
        if (audioAnalysisJob?.isActive == true) return
        audioAnalysisJob = viewModelScope.launch {
            _entryActionState.value = ActionState.Working("Understanding your voice and feelings…")
            val analysisResult = aiRepository.analyzeAudioJournal(
                audioFile = audioFile,
                promptUsed = promptUsed
            )
            analysisResult.fold(
                onSuccess = { analyzed ->
                    _entryActionState.value = ActionState.Working("Saving your memory safely…")
                    val saveResult = repository.addEntry(
                        dateKey = todayDateKey(),
                        title = analyzed.title,
                        content = analyzed.transcript,
                        entryType = EntryType.AUDIO,
                        audioFileName = audioFile.name,
                        audioDurationSec = durationSec,
                        primaryEmotion = analyzed.primaryEmotion,
                        sentimentPolarity = analyzed.sentimentPolarity,
                        sentimentScore = analyzed.sentimentScore,
                        positiveHighlight = analyzed.positiveHighlight,
                        promptUsed = promptUsed,
                        keyTopics = analyzed.keyTopics
                    )
                    saveResult.fold(
                        onSuccess = {
                            audioController.resetRecorder(deleteUnsavedFile = false)
                            _entryActionState.value = ActionState.Idle
                            _snackbarMessage.value = "Audio memory saved as ${analyzed.primaryEmotion.displayName}!"
                            onSaved()
                        },
                        onFailure = { saveErr ->
                            _entryActionState.value = ActionState.Error(
                                saveErr.localizedMessage ?: "Failed to save your entry."
                            )
                        }
                    )
                },
                onFailure = { aiErr ->
                    _entryActionState.value = ActionState.Error(
                        aiErr.localizedMessage ?: "Unable to analyze audio recording."
                    )
                }
            )
        }
    }

    fun analyzeAndSaveTextEntry(
        noteText: String,
        preferredEmotion: EmotionCategory?,
        promptUsed: String?,
        onSaved: () -> Unit
    ) {
        val cleaned = noteText.trim()
        if (cleaned.isBlank()) {
            _entryActionState.value = ActionState.Error("Please write a few words before saving.")
            return
        }
        viewModelScope.launch {
            _entryActionState.value = ActionState.Working("Reading your note gently…")
            val analysisResult = aiRepository.analyzeTextJournal(
                text = cleaned,
                preferredEmotion = preferredEmotion,
                promptUsed = promptUsed
            )
            analysisResult.fold(
                onSuccess = { analyzed ->
                    _entryActionState.value = ActionState.Working("Saving your memory safely…")
                    val saveResult = repository.addEntry(
                        dateKey = todayDateKey(),
                        title = analyzed.title,
                        content = analyzed.transcript,
                        entryType = EntryType.TEXT,
                        audioFileName = null,
                        audioDurationSec = 0,
                        primaryEmotion = analyzed.primaryEmotion,
                        sentimentPolarity = analyzed.sentimentPolarity,
                        sentimentScore = analyzed.sentimentScore,
                        positiveHighlight = analyzed.positiveHighlight,
                        promptUsed = promptUsed,
                        keyTopics = analyzed.keyTopics
                    )
                    saveResult.fold(
                        onSuccess = {
                            _entryActionState.value = ActionState.Idle
                            _snackbarMessage.value = "Memory saved under ${analyzed.primaryEmotion.displayName}!"
                            onSaved()
                        },
                        onFailure = { saveErr ->
                            _entryActionState.value = ActionState.Error(
                                saveErr.localizedMessage ?: "Could not save your note."
                            )
                        }
                    )
                },
                onFailure = { aiErr ->
                    _entryActionState.value = ActionState.Error(
                        aiErr.localizedMessage ?: "Could not analyze your note."
                    )
                }
            )
        }
    }

    fun updateEntryEmotion(entryId: String, newEmotion: EmotionCategory) {
        viewModelScope.launch {
            val res = repository.updateEntryEmotion(entryId, newEmotion)
            res.fold(
                onSuccess = {
                    _snackbarMessage.value = "Updated mood to ${newEmotion.displayName}."
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not update mood."
                }
            )
        }
    }

    fun deleteEntry(entry: JournalEntry) {
        viewModelScope.launch {
            val res = repository.deleteEntry(entry.id)
            res.fold(
                onSuccess = {
                    audioController.deleteLocalAudioFile(entry.audioFileName)
                    _snackbarMessage.value = "Memory removed."
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not delete memory."
                }
            )
        }
    }

    /**
     * Summarizes the user's past 7 days of top positive memories using Gemini AI
     * and stores the summary in Firestore for easy revisiting and well-being support.
     */
    fun generateWeeklyPositiveMemorySummary() {
        val allEntries = (entriesState.value as? UiState.Success)?.data.orEmpty()
        val weekDateKeys = pastSevenDateKeys()
        val weekEntries = allEntries.filter { it.dateKey in weekDateKeys }
        if (weekEntries.isEmpty()) {
            _weeklySummaryAction.value = ActionState.Error(
                "Add at least one audio or written memory this week first."
            )
            return
        }

        weeklySummaryJob?.cancel()
        weeklySummaryJob = viewModelScope.launch {
            _weeklySummaryAction.value = ActionState.Working("Gathering your brightest memories…")
            val weekLabel = currentWeekRangeLabel()
            val weekId = currentWeekDocId()
            val positiveCount = weekEntries.count {
                it.emotionEnum.positiveMood || it.sentimentScore >= 60L
            }

            val aiResult = aiRepository.summarizeWeeklyPositiveMemories(
                weekEntries = weekEntries,
                weekLabel = weekLabel
            )
            aiResult.fold(
                onSuccess = { generated ->
                    val saveResult = repository.saveWeeklySummary(
                        weekId = weekId,
                        weekLabel = weekLabel,
                        headline = generated.headline,
                        summaryText = generated.summaryText,
                        highlights = generated.highlights,
                        encouragingClosing = generated.encouragingClosing,
                        positiveEntryCount = positiveCount,
                        totalEntryCount = weekEntries.size
                    )
                    saveResult.fold(
                        onSuccess = {
                            _weeklySummaryAction.value = ActionState.Idle
                            _snackbarMessage.value = "Weekly positive memories updated!"
                        },
                        onFailure = { saveErr ->
                            _weeklySummaryAction.value = ActionState.Error(
                                saveErr.localizedMessage ?: "Could not save weekly summary."
                            )
                        }
                    )
                },
                onFailure = { aiErr ->
                    _weeklySummaryAction.value = ActionState.Error(
                        aiErr.localizedMessage ?: "Could not generate weekly positive memories."
                    )
                }
            )
        }
    }

    fun updatePreferences(updated: UserPreferences, onSaved: (UserPreferences) -> Unit = {}) {
        viewModelScope.launch {
            val normalized = updated.copy(userId = currentUserId)
            val res = repository.savePreferences(normalized)
            res.fold(
                onSuccess = {
                    onSaved(normalized)
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not save settings."
                }
            )
        }
    }

    fun setTextScale(level: TextScaleLevel) {
        updatePreferences(preferencesState.value.copy(textScaleLevel = level.name))
    }

    fun setHighContrastDark(enabled: Boolean) {
        updatePreferences(preferencesState.value.copy(highContrastDark = enabled))
    }

    override fun onCleared() {
        super.onCleared()
        audioController.release()
    }

    companion object {
        fun todayDateKey(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }

        fun friendlyTodayHeader(): String {
            return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        }

        fun currentTimeOfDayLabel(): String {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when {
                hour < 12 -> "Morning"
                hour < 17 -> "Afternoon"
                else -> "Evening"
            }
        }

        fun pastSevenDateKeys(): Set<String> {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val keys = mutableSetOf<String>()
            repeat(7) {
                keys.add(fmt.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            return keys
        }

        fun currentWeekRangeLabel(): String {
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val end = Calendar.getInstance()
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            return "${fmt.format(start.time)} – ${fmt.format(end.time)}"
        }

        fun currentWeekDocId(): String {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            return "${year}_W${week}"
        }

        fun formatDateKeyFriendly(dateKey: String): String {
            val today = todayDateKey()
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it.time) }

            if (dateKey == today) return "Today"
            if (dateKey == yesterday) return "Yesterday"
            return try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
                if (parsed != null) {
                    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(parsed)
                } else {
                    dateKey
                }
            } catch (_: Exception) {
                dateKey
            }
        }

        fun filterByPeriod(entries: List<JournalEntry>, period: DashboardPeriod): List<JournalEntry> {
            return when (period) {
                DashboardPeriod.TODAY -> {
                    val today = todayDateKey()
                    entries.filter { it.dateKey == today }
                }
                DashboardPeriod.WEEK -> {
                    val weekKeys = pastSevenDateKeys()
                    entries.filter { it.dateKey in weekKeys }
                }
                DashboardPeriod.ALL -> entries
            }
        }

        fun computeEmotionStats(entries: List<JournalEntry>): List<EmotionCountStat> {
            val total = entries.size.coerceAtLeast(1)
            val grouped = entries.groupingBy { it.emotionEnum }.eachCount()
            return EmotionCategory.entries.map { category ->
                val count = grouped[category] ?: 0
                EmotionCountStat(
                    emotion = category,
                    count = count,
                    fraction = count.toFloat() / total.toFloat()
                )
            }
        }

        fun accumulateByDay(entries: List<JournalEntry>): List<DailyEmotionAccumulation> {
            return entries
                .groupBy { it.dateKey.ifBlank { todayDateKey() } }
                .entries
                .sortedByDescending { it.key }
                .map { (dateKey, dayEntries) ->
                    val counts = dayEntries.groupingBy { it.emotionEnum }.eachCount()
                    val dominant = counts.entries
                        .sortedWith(
                            compareByDescending<Map.Entry<EmotionCategory, Int>> { it.value }
                                .thenByDescending { it.key.positiveMood }
                        )
                        .firstOrNull()?.key ?: EmotionCategory.REFLECTIVE
                    val avgScore = if (dayEntries.isNotEmpty()) {
                        dayEntries.map { it.sentimentScore.toInt() }.average().toInt()
                    } else 50
                    DailyEmotionAccumulation(
                        dateKey = dateKey,
                        formattedDate = formatDateKeyFriendly(dateKey),
                        dominantEmotion = dominant,
                        emotionCounts = counts,
                        entries = dayEntries,
                        averageSentimentScore = avgScore
                    )
                }
        }
    }
}
