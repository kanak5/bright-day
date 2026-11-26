package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.media.PlayerState
import com.example.media.RecorderState
import com.example.model.EmotionCategory
import com.example.model.EntryType
import com.example.model.JournalEntry
import com.example.ui.components.EmotionBadge
import com.example.ui.components.HighContrastErrorBanner
import com.example.ui.components.emotionIcon
import com.example.ui.components.formatDurationSeconds
import com.example.ui.theme.LocalHighContrastDark
import com.example.ui.theme.RecordingCrimson
import com.example.ui.theme.RecordingCrimsonDark
import com.example.ui.theme.emotionColorSet
import com.example.viewmodel.ActionState
import java.io.File

@Composable
fun AudioRecorderDialog(
    promptUsed: String?,
    recorderState: RecorderState,
    playerState: PlayerState,
    actionState: ActionState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStopAndAnalyze: (String?) -> Unit,
    onResetRecorder: () -> Unit,
    onTogglePreviewPlay: (String) -> Unit,
    onSaveAndAnalyze: (File, Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = LocalHighContrastDark.current
    val recordColor = if (isDark) RecordingCrimsonDark else RecordingCrimson
    val isBusy = actionState is ActionState.Working || recorderState.preparing

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val latestRecorderState by rememberUpdatedState(recorderState)
    val latestOnPause by rememberUpdatedState(onPauseRecording)

    // Re-check microphone permission on resume and pause recording if backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val grantedNow = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    hasAudioPermission = grantedNow
                    if (grantedNow) permissionDeniedOnce = false
                }
                Lifecycle.Event.ON_STOP -> {
                    if (latestRecorderState.recordingActive && !latestRecorderState.recordingPaused) {
                        latestOnPause()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            permissionDeniedOnce = false
            onStartRecording()
        } else {
            permissionDeniedOnce = true
        }
    }

    BackHandler(enabled = !isBusy) {
        onDismiss()
    }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_sheet_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_audio_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (!promptUsed.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "“$promptUsed”",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (!hasAudioPermission) {
                    Text(
                        text = if (permissionDeniedOnce) {
                            stringResource(R.string.audio_permission_denied_help)
                        } else {
                            stringResource(R.string.audio_permission_needed)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .testTag("grant_mic_permission_button")
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.audio_grant_permission),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (permissionDeniedOnce) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("open_mic_settings_button"),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.audio_open_settings),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    // Status message
                    val statusText = when {
                        actionState is ActionState.Working -> actionState.statusMessage
                        recorderState.preparing -> stringResource(R.string.audio_preparing)
                        recorderState.recordingActive && recorderState.recordingPaused ->
                            stringResource(R.string.audio_paused)
                        recorderState.recordingActive -> stringResource(R.string.audio_recording_now)
                        recorderState.recordedFile != null -> stringResource(R.string.audio_recorded_ready)
                        else -> stringResource(R.string.audio_tap_to_start)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    // Big Clear Timer
                    val liveListening = recorderState.recordingActive && !recorderState.recordingPaused
                    Text(
                        text = formatDurationSeconds(recorderState.elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (liveListening) recordColor else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("audio_timer_text")
                    )

                    // High-contrast Audio Level Meter
                    val animatedAmp by animateFloatAsState(
                        targetValue = if (liveListening) recorderState.amplitudeLevel else 0.08f,
                        label = "audioAmplitude"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val weights = listOf(0.45f, 0.75f, 1.0f, 0.85f, 1.0f, 0.70f, 0.45f)
                        weights.forEach { factor ->
                            val barHeight = (12f + 38f * animatedAmp * factor).dp
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (liveListening) recordColor
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }

                    // Error messages
                    if (!recorderState.errorMessage.isNullOrBlank()) {
                        HighContrastErrorBanner(message = recorderState.errorMessage)
                    }
                    if (actionState is ActionState.Error) {
                        HighContrastErrorBanner(message = actionState.message)
                    }

                    if (isBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(strokeWidth = 4.dp)
                            Text(
                                text = (actionState as? ActionState.Working)?.statusMessage
                                    ?: stringResource(R.string.audio_preparing),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else if (recorderState.recordedFile == null) {
                        // Huge unmistakable Start / Finish & Analyze circular button
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(
                                    if (recorderState.recordingActive) recordColor
                                    else MaterialTheme.colorScheme.primary
                                )
                                .border(4.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .testTag("audio_record_toggle_button")
                                .clickable {
                                    if (recorderState.recordingActive) {
                                        onStopAndAnalyze(promptUsed)
                                    } else {
                                        onStartRecording()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (recorderState.recordingActive) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (recorderState.recordingActive) {
                                    stringResource(R.string.audio_stop_button)
                                } else {
                                    stringResource(R.string.audio_start_button)
                                },
                                tint = if (recorderState.recordingActive) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                modifier = Modifier.size(54.dp)
                            )
                        }
                        Text(
                            text = if (recorderState.recordingActive) {
                                stringResource(R.string.audio_stop_button)
                            } else {
                                stringResource(R.string.audio_start_button)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        if (recorderState.recordingActive) {
                            // Pause / Resume + Stop & Listen First controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (recorderState.recordingPaused) onResumeRecording()
                                        else onPauseRecording()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .testTag("audio_pause_resume_button"),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(
                                        imageVector = if (recorderState.recordingPaused) {
                                            Icons.Filled.PlayArrow
                                        } else {
                                            Icons.Filled.Pause
                                        },
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (recorderState.recordingPaused) {
                                            stringResource(R.string.audio_resume_button)
                                        } else {
                                            stringResource(R.string.audio_pause_button)
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = onStopRecording,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .testTag("audio_stop_preview_button"),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.audio_stop_preview_button),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    } else {
                        // Recorded preview & Save + AI Analyze buttons
                        val recordedFile = recorderState.recordedFile
                        val fileName = recorderState.recordedFileName.orEmpty()
                        val isPreviewPlaying = playerState.activeFileName == fileName && playerState.playingActive
                        val fileSizeKb = (recorderState.recordedFileSizeBytes / 1024L).coerceAtLeast(1L)

                        Text(
                            text = "Voice file ready  •  ${formatDurationSeconds(recorderState.elapsedSeconds)}  •  ${fileSizeKb} KB",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        OutlinedButton(
                            onClick = { onTogglePreviewPlay(fileName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("preview_audio_button"),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isPreviewPlaying) {
                                    stringResource(R.string.action_pause_audio)
                                } else {
                                    stringResource(R.string.action_play_audio)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = {
                                onSaveAndAnalyze(
                                    recordedFile,
                                    recorderState.elapsedSeconds.coerceAtLeast(1),
                                    promptUsed
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .testTag("save_analyze_audio_button"),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.audio_save_analyze),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onResetRecorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .testTag("record_again_button"),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.audio_record_again),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteNoteDialog(
    promptUsed: String?,
    actionState: ActionState,
    onSaveNote: (String, EmotionCategory?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var selectedEmotion by remember { mutableStateOf<EmotionCategory?>(null) }
    val isDark = LocalHighContrastDark.current
    val isBusy = actionState is ActionState.Working

    BackHandler(enabled = !isBusy) {
        onDismiss()
    }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.write_sheet_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_write_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (!promptUsed.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "“$promptUsed”",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 4800) noteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .testTag("write_note_input"),
                    enabled = !isBusy,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.write_placeholder),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.medium
                )

                Text(
                    text = stringResource(R.string.write_emotion_optional),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto-detect chip
                    val autoSelected = selectedEmotion == null
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("emotion_chip_auto")
                            .clickable(enabled = !isBusy) { selectedEmotion = null },
                        shape = RoundedCornerShape(50),
                        color = if (autoSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = if (autoSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.write_auto_detect),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (autoSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    EmotionCategory.entries.forEach { emotion ->
                        val isSelected = selectedEmotion == emotion
                        val colors = emotionColorSet(emotion.name, isDark)
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("emotion_chip_${emotion.name.lowercase()}")
                                .clickable(enabled = !isBusy) { selectedEmotion = emotion },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) colors.container else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (isSelected) 3.dp else 1.5.dp, colors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = emotionIcon(emotion),
                                    contentDescription = null,
                                    tint = colors.content,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = emotion.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) colors.content else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (actionState is ActionState.Error) {
                    HighContrastErrorBanner(message = actionState.message)
                }

                Button(
                    onClick = { onSaveNote(noteText, selectedEmotion, promptUsed) },
                    enabled = !isBusy && noteText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .testTag("save_note_button"),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = (actionState as ActionState.Working).statusMessage,
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.write_save_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailDialog(
    entry: JournalEntry,
    playerState: PlayerState,
    speakingId: String?,
    onPlayAudio: (String) -> Unit,
    onReadAloud: (String, String) -> Unit,
    onChangeEmotion: (EmotionCategory) -> Unit,
    onDeleteEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalHighContrastDark.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isThisPlaying = !entry.audioFileName.isNullOrBlank() &&
        playerState.activeFileName == entry.audioFileName &&
        playerState.playingActive
    val isThisSpeaking = speakingId == "detail_${entry.id}"

    BackHandler { onDismiss() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_confirm_body),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteEntry()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_entry_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmotionBadge(emotion = entry.emotionEnum)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_detail_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${entry.dateKey}  •  Positivity Score: ${entry.sentimentScore}/100 (${entry.polarityEnum.displayName})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!entry.positiveHighlight.isNullOrBlank()) {
                    val colors = emotionColorSet(entry.emotionEnum.name, isDark)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = colors.container,
                        border = BorderStroke(2.dp, colors.border)
                    ) {
                        Text(
                            text = entry.positiveHighlight,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.content,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (entry.keyTopics.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        entry.keyTopics.forEach { topic ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Playback & Read Aloud row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (entry.entryTypeEnum == EntryType.AUDIO && !entry.audioFileName.isNullOrBlank()) {
                        Button(
                            onClick = { onPlayAudio(entry.audioFileName) },
                            modifier = Modifier.heightIn(min = 54.dp)
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isThisPlaying) stringResource(R.string.action_pause_audio)
                                else stringResource(R.string.action_play_audio)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onReadAloud("detail_${entry.id}", "${entry.title}. ${entry.content}") },
                        modifier = Modifier.heightIn(min = 54.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = if (isThisSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isThisSpeaking) stringResource(R.string.today_prompt_stop_reading)
                            else stringResource(R.string.today_prompt_read_aloud)
                        )
                    }
                }

                // Allow gentle mood adjustment
                Text(
                    text = "Adjust Emotion Category:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmotionCategory.entries.forEach { emotion ->
                        val selected = entry.emotionEnum == emotion
                        val colors = emotionColorSet(emotion.name, isDark)
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onChangeEmotion(emotion) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) colors.container else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (selected) 3.dp else 1.5.dp, colors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = emotionIcon(emotion),
                                    contentDescription = null,
                                    tint = colors.content,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = emotion.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) colors.content else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("delete_entry_button"),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}
