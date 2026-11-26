package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.TextScaleLevel
import com.example.model.UserPreferences
import com.example.reminder.ReminderScheduler
import com.example.reminder.WORK_NAME_EVENING
import com.example.reminder.WORK_NAME_MORNING
import com.example.ui.components.formatTime12Hour

@Composable
fun RemindersSettingsScreen(
    preferences: UserPreferences,
    userDisplayName: String?,
    userEmail: String?,
    onSavePreferences: (UserPreferences) -> Unit,
    onSetTextScale: (TextScaleLevel) -> Unit,
    onSetHighContrastDark: (Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var reminderMessageDraft by remember(preferences.reminderMessage) {
        mutableStateOf(preferences.reminderMessage)
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            ReminderScheduler.syncReminders(context, preferences)
            onShowMessage("Daily reminders are ready!")
        }
    }

    val morningScheduled by remember(context) {
        ReminderScheduler.observeReminderActive(context, WORK_NAME_MORNING)
    }.collectAsStateWithLifecycle(initialValue = false)
    val eveningScheduled by remember(context) {
        ReminderScheduler.observeReminderActive(context, WORK_NAME_EVENING)
    }.collectAsStateWithLifecycle(initialValue = false)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "reminders_header") {
                Text(
                    text = stringResource(R.string.reminders_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("reminders_screen_title")
                )
            }

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item(key = "notification_permission_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.reminder_permission_note),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("allow_notifications_button")
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.reminder_allow_notifications))
                            }
                        }
                    }
                }
            }

            // Morning Reminder Card
            item(key = "morning_reminder_card") {
                ReminderSlotCard(
                    title = stringResource(R.string.reminder_morning_title),
                    icon = Icons.Filled.WbSunny,
                    enabled = preferences.morningReminderEnabled,
                    hour = preferences.morningHour.toInt(),
                    minute = preferences.morningMinute.toInt(),
                    workScheduled = morningScheduled,
                    testTagPrefix = "morning_reminder",
                    onToggle = { enabled ->
                        val updated = preferences.copy(morningReminderEnabled = enabled)
                        onSavePreferences(updated)
                        ReminderScheduler.syncReminders(context, updated)
                    },
                    onStepMinutes = { delta ->
                        val (newH, newM) = stepTimeByMinutes(
                            preferences.morningHour.toInt(),
                            preferences.morningMinute.toInt(),
                            delta
                        )
                        val updated = preferences.copy(
                            morningHour = newH.toLong(),
                            morningMinute = newM.toLong()
                        )
                        onSavePreferences(updated)
                        ReminderScheduler.syncReminders(context, updated)
                    }
                )
            }

            // Evening Reminder Card
            item(key = "evening_reminder_card") {
                ReminderSlotCard(
                    title = stringResource(R.string.reminder_evening_title),
                    icon = Icons.Filled.NightsStay,
                    enabled = preferences.eveningReminderEnabled,
                    hour = preferences.eveningHour.toInt(),
                    minute = preferences.eveningMinute.toInt(),
                    workScheduled = eveningScheduled,
                    testTagPrefix = "evening_reminder",
                    onToggle = { enabled ->
                        val updated = preferences.copy(eveningReminderEnabled = enabled)
                        onSavePreferences(updated)
                        ReminderScheduler.syncReminders(context, updated)
                    },
                    onStepMinutes = { delta ->
                        val (newH, newM) = stepTimeByMinutes(
                            preferences.eveningHour.toInt(),
                            preferences.eveningMinute.toInt(),
                            delta
                        )
                        val updated = preferences.copy(
                            eveningHour = newH.toLong(),
                            eveningMinute = newM.toLong()
                        )
                        onSavePreferences(updated)
                        ReminderScheduler.syncReminders(context, updated)
                    }
                )
            }

            // Gentle Reminder Message & Test Reminder Button
            item(key = "reminder_message_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reminder_message_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = reminderMessageDraft,
                            onValueChange = { if (it.length <= 180) reminderMessageDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reminder_message_input"),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = false,
                            maxLines = 3
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val updated = preferences.copy(
                                        reminderMessage = reminderMessageDraft.trim()
                                            .ifBlank { "Time to share a gentle thought in BrightDay." }
                                    )
                                    onSavePreferences(updated)
                                    ReminderScheduler.syncReminders(context, updated)
                                    onShowMessage("Reminder message saved.")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp)
                                    .testTag("save_reminder_message_button")
                            ) {
                                Text("Save Message")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    ReminderScheduler.enqueueTestReminderWork(
                                        context = context,
                                        message = reminderMessageDraft.ifBlank {
                                            "Time to share a gentle thought in BrightDay."
                                        }
                                    )
                                    onShowMessage("Test reminder scheduled — check your notification bar!")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .testTag("send_test_reminder_button"),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.reminder_test_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Accessibility: High Contrast & Text Size
            item(key = "accessibility_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Contrast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.display_section_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.display_dark_contrast_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.display_dark_contrast_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.highContrastDark,
                                onCheckedChange = onSetHighContrastDark,
                                modifier = Modifier.testTag("high_contrast_dark_switch")
                            )
                        }

                        Text(
                            text = stringResource(R.string.display_text_size_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextScaleOptionButton(
                                label = stringResource(R.string.text_size_standard),
                                selected = preferences.textScaleEnum == TextScaleLevel.STANDARD,
                                onClick = { onSetTextScale(TextScaleLevel.STANDARD) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("text_scale_standard")
                            )
                            TextScaleOptionButton(
                                label = stringResource(R.string.text_size_large),
                                selected = preferences.textScaleEnum == TextScaleLevel.LARGE,
                                onClick = { onSetTextScale(TextScaleLevel.LARGE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("text_scale_large")
                            )
                            TextScaleOptionButton(
                                label = stringResource(R.string.text_size_extra_large),
                                selected = preferences.textScaleEnum == TextScaleLevel.EXTRA_LARGE,
                                onClick = { onSetTextScale(TextScaleLevel.EXTRA_LARGE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("text_scale_extra_large")
                            )
                        }
                    }
                }
            }

            // Account & Sign Out Card
            item(key = "account_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.account_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!userDisplayName.isNullOrBlank()) {
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("sign_out_button"),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.account_sign_out),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReminderSlotCard(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    hour: Int,
    minute: Int,
    workScheduled: Boolean,
    testTagPrefix: String,
    onToggle: (Boolean) -> Unit,
    onStepMinutes: (Int) -> Unit
) {
    val countdownLabel = remember(enabled, hour, minute) {
        if (enabled) ReminderScheduler.formatNextReminderCountdown(hour, minute) else "Paused"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (enabled && workScheduled) {
                                "Scheduled daily • $countdownLabel"
                            } else if (enabled) {
                                "Daily reminder • $countdownLabel"
                            } else {
                                "Reminder turned off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("${testTagPrefix}_status")
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("${testTagPrefix}_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onStepMinutes(-15) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("${testTagPrefix}_minus"),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "15 minutes earlier")
                    Spacer(Modifier.width(4.dp))
                    Text("15m")
                }

                Text(
                    text = formatTime12Hour(hour, minute),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("${testTagPrefix}_time")
                )

                OutlinedButton(
                    onClick = { onStepMinutes(15) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("${testTagPrefix}_plus"),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "15 minutes later")
                    Spacer(Modifier.width(4.dp))
                    Text("15m")
                }
            }
        }
    }
}

@Composable
private fun TextScaleOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun stepTimeByMinutes(hour: Int, minute: Int, deltaMinutes: Int): Pair<Int, Int> {
    val total = ((hour * 60 + minute + deltaMinutes) % (24 * 60) + (24 * 60)) % (24 * 60)
    return (total / 60) to (total % 60)
}
