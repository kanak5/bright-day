package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.PlayerState
import com.example.model.EmotionCategory
import com.example.model.EntryType
import com.example.model.JournalEntry
import com.example.ui.theme.LocalHighContrastDark
import com.example.ui.theme.emotionColorSet
import java.text.SimpleDateFormat
import java.util.Locale

fun emotionIcon(emotion: EmotionCategory): ImageVector = when (emotion) {
    EmotionCategory.JOYFUL -> Icons.Filled.SentimentVerySatisfied
    EmotionCategory.PEACEFUL -> Icons.Filled.Spa
    EmotionCategory.GRATEFUL -> Icons.Filled.Favorite
    EmotionCategory.REFLECTIVE -> Icons.AutoMirrored.Filled.MenuBook
    EmotionCategory.WORRIED -> Icons.Filled.Cloud
    EmotionCategory.SAD -> Icons.Filled.WaterDrop
}

fun formatDurationSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatTime12Hour(hour24: Int, minute: Int): String {
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
}

@Composable
fun EmotionBadge(
    emotion: EmotionCategory,
    modifier: Modifier = Modifier,
    countSuffix: Int? = null
) {
    val isDark = LocalHighContrastDark.current
    val colors = emotionColorSet(emotion.name, isDark)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = colors.container,
        border = BorderStroke(2.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = emotionIcon(emotion),
                contentDescription = emotion.displayName,
                tint = colors.content,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (countSuffix != null) "${emotion.displayName} ($countSuffix)" else emotion.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.content,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEntryCard(
    entry: JournalEntry,
    playerState: PlayerState,
    speakingId: String?,
    onPlayAudio: (String) -> Unit,
    onReadAloud: (String, String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalHighContrastDark.current
    val emotion = entry.emotionEnum
    val emotionColors = emotionColorSet(emotion.name, isDark)
    val isThisPlaying = !entry.audioFileName.isNullOrBlank() &&
        playerState.activeFileName == entry.audioFileName &&
        playerState.playingActive
    val isThisSpeaking = speakingId == "entry_${entry.id}"

    val timeLabel = entry.createdAt?.toDate()?.let { date ->
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } ?: ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journal_entry_card_${entry.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: Emotion badge + Audio tag + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmotionBadge(emotion = emotion)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (entry.entryTypeEnum == EntryType.AUDIO) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = "Audio Diary",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = formatDurationSeconds(entry.audioDurationSec.toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title
            Text(
                text = entry.title.ifBlank { "My Memory" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Transcript / Note text
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Warm AI Positive Highlight Box
            if (!entry.positiveHighlight.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(emotionColors.container)
                        .border(1.5.dp, emotionColors.border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Warm note",
                        tint = emotionColors.content,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                    )
                    Text(
                        text = entry.positiveHighlight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = emotionColors.content,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Audio playback bar when playing
            if (isThisPlaying) {
                LinearProgressIndicator(
                    progress = { playerState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Action row: Play Audio (if audio) + Read Aloud
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entry.entryTypeEnum == EntryType.AUDIO && !entry.audioFileName.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onPlayAudio(entry.audioFileName) },
                        modifier = Modifier
                            .heightIn(min = 50.dp)
                            .testTag("play_audio_button_${entry.id}"),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isThisPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isThisPlaying) {
                                stringResource(R.string.action_pause_audio)
                            } else {
                                stringResource(R.string.action_play_audio)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isThisPlaying) {
                                stringResource(R.string.action_pause_audio)
                            } else {
                                stringResource(R.string.action_play_audio)
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        onReadAloud("entry_${entry.id}", "${entry.title}. ${entry.content}")
                    },
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .testTag("read_entry_button_${entry.id}"),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = if (isThisSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.today_prompt_read_aloud),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isThisSpeaking) {
                            stringResource(R.string.today_prompt_stop_reading)
                        } else {
                            stringResource(R.string.today_prompt_read_aloud)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun HighContrastErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
fun CircularIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
    }
}
