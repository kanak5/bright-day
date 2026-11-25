package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.JournalAiRepository
import com.example.data.JournalRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutGoogle
import com.example.media.AudioJournalController
import com.example.model.JournalEntry
import com.example.reminder.ReminderScheduler
import com.example.ui.screens.AudioRecorderDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EmotionDashboardScreen
import com.example.ui.screens.EntryDetailDialog
import com.example.ui.screens.RemindersSettingsScreen
import com.example.ui.screens.TodayJournalScreen
import com.example.ui.screens.WriteNoteDialog
import com.example.ui.theme.BrightDayTheme
import com.example.viewmodel.BrightDayViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class BrightDayTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    TODAY(R.string.nav_today, Icons.Filled.Book, Icons.Outlined.Book, "nav_tab_today"),
    EMOTIONS(R.string.nav_emotions, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_tab_emotions"),
    REMINDERS(R.string.nav_reminders, Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_tab_reminders")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent BEFORE Compose UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge display and notification channel
        enableEdgeToEdge()
        ReminderScheduler.ensureNotificationChannel(this)

        setContent {
            BrightDayRoot()
        }
    }
}

@Composable
fun BrightDayRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        BrightDayTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation */ })
            }
        }
    } else {
        // Key by authenticated UID so account switches or sign-out dispose listeners cleanly
        key(user.uid) {
            BrightDayAuthenticatedShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun BrightDayAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BrightDayViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BrightDayViewModel(
                    repository = JournalRepository(db),
                    aiRepository = JournalAiRepository(),
                    audioController = AudioJournalController(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val entriesState by viewModel.entriesState.collectAsStateWithLifecycle()
    val weeklySummariesState by viewModel.weeklySummariesState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val promptState by viewModel.promptState.collectAsStateWithLifecycle()
    val entryActionState by viewModel.entryActionState.collectAsStateWithLifecycle()
    val weeklySummaryAction by viewModel.weeklySummaryAction.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val selectedEmotionFilter by viewModel.selectedEmotionFilter.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(BrightDayTab.TODAY) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showWriteDialog by remember { mutableStateOf(false) }
    var dialogPromptContext by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(playerState.errorMessage) {
        val err = playerState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
        }
    }

    // Keep selectedEntry synced if updated in Firestore
    val currentSelectedEntry = remember(selectedEntry, entriesState) {
        val targetId = selectedEntry?.id ?: return@remember null
        val list = (entriesState as? com.example.viewmodel.UiState.Success)?.data
        list?.firstOrNull { it.id == targetId } ?: selectedEntry
    }

    // Navigate back to Today tab from secondary tabs
    BackHandler(enabled = activeTab != BrightDayTab.TODAY) {
        activeTab = BrightDayTab.TODAY
    }

    BrightDayTheme(
        highContrastDark = preferences.highContrastDark,
        textScaleLevel = preferences.textScaleEnum
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            BrightDayTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            BrightDayTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (activeTab) {
                            BrightDayTab.TODAY -> TodayJournalScreen(
                                entriesState = entriesState,
                                promptState = promptState,
                                playerState = playerState,
                                speakingId = speakingId,
                                onNewPrompt = { viewModel.fetchJournalingPrompts(forceRefresh = false) },
                                onRetryPrompt = { viewModel.fetchJournalingPrompts(forceRefresh = true) },
                                onReadAloud = { id, text -> viewModel.audioController.toggleSpeak(id, text) },
                                onOpenAudioRecorder = { prompt ->
                                    viewModel.clearEntryActionState()
                                    viewModel.audioController.resetRecorder(deleteUnsavedFile = true)
                                    dialogPromptContext = prompt
                                    showAudioDialog = true
                                },
                                onOpenWriteNote = { prompt ->
                                    viewModel.clearEntryActionState()
                                    dialogPromptContext = prompt
                                    showWriteDialog = true
                                },
                                onPlayAudio = { fileName -> viewModel.audioController.togglePlayback(fileName) },
                                onSelectEntry = { entry -> selectedEntry = entry }
                            )

                            BrightDayTab.EMOTIONS -> EmotionDashboardScreen(
                                entriesState = entriesState,
                                weeklySummariesState = weeklySummariesState,
                                weeklySummaryAction = weeklySummaryAction,
                                selectedPeriod = selectedPeriod,
                                selectedEmotionFilter = selectedEmotionFilter,
                                playerState = playerState,
                                speakingId = speakingId,
                                onSelectPeriod = viewModel::setDashboardPeriod,
                                onSelectEmotionFilter = viewModel::setEmotionFilter,
                                onGenerateWeeklySummary = viewModel::generateWeeklyPositiveMemorySummary,
                                onPlayAudio = { fileName -> viewModel.audioController.togglePlayback(fileName) },
                                onReadAloud = { id, text -> viewModel.audioController.toggleSpeak(id, text) },
                                onSelectEntry = { entry -> selectedEntry = entry }
                            )

                            BrightDayTab.REMINDERS -> RemindersSettingsScreen(
                                preferences = preferences,
                                userDisplayName = userDisplayName,
                                userEmail = userEmail,
                                onSavePreferences = { updated -> viewModel.updatePreferences(updated) },
                                onSetTextScale = viewModel::setTextScale,
                                onSetHighContrastDark = viewModel::setHighContrastDark,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onSignOut = {
                                    viewModel.audioController.stopPlayback()
                                    viewModel.audioController.stopSpeaking()
                                    signOutGoogle(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }

            if (showAudioDialog) {
                AudioRecorderDialog(
                    promptUsed = dialogPromptContext,
                    recorderState = recorderState,
                    playerState = playerState,
                    actionState = entryActionState,
                    onStartRecording = { viewModel.audioController.startRecording() },
                    onStopRecording = { viewModel.audioController.stopRecording() },
                    onResetRecorder = {
                        viewModel.clearEntryActionState()
                        viewModel.audioController.resetRecorder(deleteUnsavedFile = true)
                    },
                    onTogglePreviewPlay = { fileName -> viewModel.audioController.togglePlayback(fileName) },
                    onSaveAndAnalyze = { file, duration, prompt ->
                        viewModel.analyzeAndSaveAudioEntry(
                            audioFile = file,
                            durationSec = duration,
                            promptUsed = prompt,
                            onSaved = { showAudioDialog = false }
                        )
                    },
                    onDismiss = {
                        viewModel.audioController.resetRecorder(deleteUnsavedFile = true)
                        viewModel.clearEntryActionState()
                        showAudioDialog = false
                    }
                )
            }

            if (showWriteDialog) {
                WriteNoteDialog(
                    promptUsed = dialogPromptContext,
                    actionState = entryActionState,
                    onSaveNote = { text, preferredEmotion, prompt ->
                        viewModel.analyzeAndSaveTextEntry(
                            noteText = text,
                            preferredEmotion = preferredEmotion,
                            promptUsed = prompt,
                            onSaved = { showWriteDialog = false }
                        )
                    },
                    onDismiss = {
                        viewModel.clearEntryActionState()
                        showWriteDialog = false
                    }
                )
            }

            if (currentSelectedEntry != null) {
                EntryDetailDialog(
                    entry = currentSelectedEntry,
                    playerState = playerState,
                    speakingId = speakingId,
                    onPlayAudio = { fileName -> viewModel.audioController.togglePlayback(fileName) },
                    onReadAloud = { id, text -> viewModel.audioController.toggleSpeak(id, text) },
                    onChangeEmotion = { newEmotion ->
                        viewModel.updateEntryEmotion(currentSelectedEntry.id, newEmotion)
                    },
                    onDeleteEntry = {
                        viewModel.deleteEntry(currentSelectedEntry)
                        selectedEntry = null
                    },
                    onDismiss = { selectedEntry = null }
                )
            }
        }
    }
}
