package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.PlayerState
import com.example.model.DashboardPeriod
import com.example.model.EmotionCategory
import com.example.model.EmotionCountStat
import com.example.model.JournalEntry
import com.example.model.WeeklySummary
import com.example.ui.components.EmotionBadge
import com.example.ui.components.HighContrastErrorBanner
import com.example.ui.components.JournalEntryCard
import com.example.ui.components.emotionIcon
import com.example.ui.theme.LocalHighContrastDark
import com.example.ui.theme.emotionColorSet
import com.example.viewmodel.ActionState
import com.example.viewmodel.BrightDayViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionDashboardScreen(
    entriesState: UiState<List<JournalEntry>>,
    weeklySummariesState: UiState<List<WeeklySummary>>,
    weeklySummaryAction: ActionState,
    selectedPeriod: DashboardPeriod,
    selectedEmotionFilter: EmotionCategory?,
    playerState: PlayerState,
    speakingId: String?,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    onSelectEmotionFilter: (EmotionCategory?) -> Unit,
    onGenerateWeeklySummary: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onReadAloud: (String, String) -> Unit,
    onSelectEntry: (JournalEntry) -> Unit
) {
    val allEntries = (entriesState as? UiState.Success)?.data.orEmpty()
    val periodEntries = remember(allEntries, selectedPeriod) {
        BrightDayViewModel.filterByPeriod(allEntries, selectedPeriod)
    }
    val emotionStats = remember(periodEntries) {
        BrightDayViewModel.computeEmotionStats(periodEntries)
    }
    val filteredEntries = remember(periodEntries, selectedEmotionFilter) {
        if (selectedEmotionFilter == null) periodEntries
        else periodEntries.filter { it.emotionEnum == selectedEmotionFilter }
    }
    val dailyAccumulations = remember(filteredEntries) {
        BrightDayViewModel.accumulateByDay(filteredEntries)
    }
    val latestWeeklySummary = (weeklySummariesState as? UiState.Success)?.data?.firstOrNull()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "dashboard_header") {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("emotion_dashboard_title")
                )
            }

            // 1. Weekly Top Positive Memories Hero Card
            item(key = "weekly_positive_memories_card") {
                WeeklyPositiveMemoriesCard(
                    summary = latestWeeklySummary,
                    actionState = weeklySummaryAction,
                    speakingId = speakingId,
                    onGenerateSummary = onGenerateWeeklySummary,
                    onReadAloud = onReadAloud
                )
            }

            // 2. Time Period Filter (Today / This Week / All Time)
            item(key = "period_selector") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PeriodPillButton(
                        label = stringResource(R.string.period_today),
                        selected = selectedPeriod == DashboardPeriod.TODAY,
                        onClick = { onSelectPeriod(DashboardPeriod.TODAY) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("period_today_button")
                    )
                    PeriodPillButton(
                        label = stringResource(R.string.period_week),
                        selected = selectedPeriod == DashboardPeriod.WEEK,
                        onClick = { onSelectPeriod(DashboardPeriod.WEEK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("period_week_button")
                    )
                    PeriodPillButton(
                        label = stringResource(R.string.period_all),
                        selected = selectedPeriod == DashboardPeriod.ALL,
                        onClick = { onSelectPeriod(DashboardPeriod.ALL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("period_all_button")
                    )
                }
            }

            // 3. Emotion Accumulation Breakdown Cards (2-column grid)
            item(key = "emotion_breakdown") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.emotion_breakdown_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (selectedEmotionFilter != null) {
                            OutlinedButton(
                                onClick = { onSelectEmotionFilter(null) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_emotion_filter_button"),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(stringResource(R.string.filter_all_emotions))
                            }
                        }
                    }

                    // 2-column grid of high-contrast emotion category cards
                    val rows = emotionStats.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair.forEach { stat ->
                                    EmotionStatCard(
                                        stat = stat,
                                        selected = selectedEmotionFilter == stat.emotion,
                                        onClick = { onSelectEmotionFilter(stat.emotion) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowPair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 4. Daily Emotion Accumulation Section Header
            item(key = "daily_accumulation_header") {
                Text(
                    text = if (selectedEmotionFilter != null) {
                        "${selectedEmotionFilter.displayName} Memories by Day"
                    } else {
                        stringResource(R.string.daily_accumulation_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 5. Entries grouped by day with accumulated emotion summary
            when (entriesState) {
                UiState.Loading -> {
                    item(key = "dashboard_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "dashboard_error") {
                        HighContrastErrorBanner(message = entriesState.message)
                    }
                }
                is UiState.Success -> {
                    if (dailyAccumulations.isEmpty()) {
                        item(key = "dashboard_empty") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_empty_filter),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        dailyAccumulations.forEach { dayGroup ->
                            item(key = "day_header_${dayGroup.dateKey}") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dayGroup.formattedDate,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${dayGroup.entries.size} ${if (dayGroup.entries.size == 1) "memory" else "memories"}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            dayGroup.emotionCounts.entries
                                                .sortedByDescending { it.value }
                                                .forEach { (emotion, count) ->
                                                    EmotionBadge(emotion = emotion, countSuffix = count)
                                                }
                                        }
                                    }
                                }
                            }

                            items(
                                items = dayGroup.entries,
                                key = { "dash_${dayGroup.dateKey}_${it.id}" }
                            ) { entry ->
                                JournalEntryCard(
                                    entry = entry,
                                    playerState = playerState,
                                    speakingId = speakingId,
                                    onPlayAudio = onPlayAudio,
                                    onReadAloud = onReadAloud,
                                    onClick = { onSelectEntry(entry) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WeeklyPositiveMemoriesCard(
    summary: WeeklySummary?,
    actionState: ActionState,
    speakingId: String?,
    onGenerateSummary: () -> Unit,
    onReadAloud: (String, String) -> Unit
) {
    val isDark = LocalHighContrastDark.current
    val warmColors = emotionColorSet("JOYFUL", isDark)
    val isGenerating = actionState is ActionState.Working
    val isSpeakingWeekly = speakingId == "weekly_summary"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_positive_memories_card"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = warmColors.container),
        border = BorderStroke(3.dp, warmColors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, warmColors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = warmColors.content,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.weekly_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = warmColors.content,
                        fontWeight = FontWeight.Bold
                    )
                    if (summary != null) {
                        Text(
                            text = "${summary.weekLabel}  •  ${summary.positiveEntryCount} uplifting memories",
                            style = MaterialTheme.typography.labelMedium,
                            color = warmColors.content
                        )
                    }
                }
            }

            if (summary == null) {
                Text(
                    text = stringResource(R.string.weekly_card_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = warmColors.content
                )
            } else {
                Text(
                    text = summary.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = warmColors.content,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("weekly_summary_headline")
                )
                Text(
                    text = summary.summaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = warmColors.content
                )

                if (summary.highlights.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, warmColors.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            summary.highlights.forEach { highlight ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = warmColors.border,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(22.dp)
                                    )
                                    Text(
                                        text = highlight,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (summary.encouragingClosing.isNotBlank()) {
                    Text(
                        text = summary.encouragingClosing,
                        style = MaterialTheme.typography.titleMedium,
                        color = warmColors.content,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val fullSpeech = buildString {
                            append(summary.headline).append(". ")
                            append(summary.summaryText).append(" ")
                            if (summary.highlights.isNotEmpty()) {
                                append("Your top bright memories were: ")
                                append(summary.highlights.joinToString(". "))
                                append(". ")
                            }
                            append(summary.encouragingClosing)
                        }
                        onReadAloud("weekly_summary", fullSpeech)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("read_weekly_summary_button"),
                    border = BorderStroke(2.dp, warmColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = if (isSpeakingWeekly) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSpeakingWeekly) stringResource(R.string.today_prompt_stop_reading)
                        else stringResource(R.string.today_prompt_read_aloud),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (actionState is ActionState.Error) {
                HighContrastErrorBanner(message = actionState.message)
            }

            Button(
                onClick = onGenerateSummary,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .testTag("generate_weekly_summary_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.weekly_generating),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = if (summary == null) Icons.Filled.AutoAwesome else Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (summary == null) {
                            stringResource(R.string.weekly_generate_button)
                        } else {
                            stringResource(R.string.weekly_refresh_button)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmotionStatCard(
    stat: EmotionCountStat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalHighContrastDark.current
    val colors = emotionColorSet(stat.emotion.name, isDark)

    Surface(
        modifier = modifier
            .heightIn(min = 112.dp)
            .testTag("emotion_stat_card_${stat.emotion.name.lowercase()}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.container else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 3.5.dp else 2.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = emotionIcon(stat.emotion),
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stat.emotion.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) colors.content else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = colors.content,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "${stat.count} ${if (stat.count == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) colors.content else MaterialTheme.colorScheme.onSurface
            )

            LinearProgressIndicator(
                progress = { stat.fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = colors.bar,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
