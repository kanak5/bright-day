package com.example.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.model.UserPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "BrightDayReminders"
private const val CHANNEL_ID = "brightday_daily_reminders"
private const val CHANNEL_NAME = "Daily Journal Reminders"

const val REMINDER_SLOT_MORNING = 1001
const val REMINDER_SLOT_EVENING = 1002
const val REMINDER_SLOT_TEST = 1099

const val WORK_NAME_MORNING = "brightday_daily_morning_reminder"
const val WORK_NAME_EVENING = "brightday_daily_evening_reminder"
const val WORK_NAME_TEST = "brightday_test_reminder"
const val WORK_TAG_DAILY_REMINDER = "brightday_daily_reminder"

object ReminderScheduler {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workMutex = Mutex()

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle daily reminders to record or write a memory in BrightDay."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Synchronizes both daily Morning and Evening WorkManager reminder jobs off the Main Thread
     * so WorkManager database initialization and scheduling never block UI frame rendering.
     */
    fun syncReminders(context: Context, prefs: UserPreferences) {
        val appContext = context.applicationContext
        ioScope.launch {
            workMutex.withLock {
                ensureNotificationChannel(appContext)
                val message = prefs.reminderMessage.trim()
                    .ifBlank { DailyJournalReminderWorker.DEFAULT_REMINDER_MESSAGE }

                if (prefs.morningReminderEnabled) {
                    scheduleDailyReminderBlocking(
                        context = appContext,
                        uniqueWorkName = WORK_NAME_MORNING,
                        slot = REMINDER_SLOT_MORNING,
                        hour = prefs.morningHour.toInt().coerceIn(0, 23),
                        minute = prefs.morningMinute.toInt().coerceIn(0, 59),
                        message = message
                    )
                } else {
                    cancelDailyReminderBlocking(appContext, WORK_NAME_MORNING)
                }

                if (prefs.eveningReminderEnabled) {
                    scheduleDailyReminderBlocking(
                        context = appContext,
                        uniqueWorkName = WORK_NAME_EVENING,
                        slot = REMINDER_SLOT_EVENING,
                        hour = prefs.eveningHour.toInt().coerceIn(0, 23),
                        minute = prefs.eveningMinute.toInt().coerceIn(0, 59),
                        message = message
                    )
                } else {
                    cancelDailyReminderBlocking(appContext, WORK_NAME_EVENING)
                }
            }
        }
    }

    fun scheduleDailyReminder(
        context: Context,
        uniqueWorkName: String,
        slot: Int,
        hour: Int,
        minute: Int,
        message: String
    ) {
        val appContext = context.applicationContext
        ioScope.launch {
            workMutex.withLock {
                scheduleDailyReminderBlocking(appContext, uniqueWorkName, slot, hour, minute, message)
            }
        }
    }

    private fun scheduleDailyReminderBlocking(
        context: Context,
        uniqueWorkName: String,
        slot: Int,
        hour: Int,
        minute: Int,
        message: String
    ) {
        try {
            val initialDelayMillis = computeInitialDelayMillis(hour, minute)
            val inputData = Data.Builder()
                .putInt(DailyJournalReminderWorker.KEY_REMINDER_SLOT, slot)
                .putString(DailyJournalReminderWorker.KEY_REMINDER_MESSAGE, message)
                .build()

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyJournalReminderWorker>(
                24L,
                TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG_DAILY_REMINDER)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                dailyWorkRequest
            )
            Log.d(
                TAG,
                "Scheduled WorkManager daily reminder ($uniqueWorkName) for %02d:%02d (initialDelayMs=%d)".format(
                    hour,
                    minute,
                    initialDelayMillis
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager daily reminder $uniqueWorkName", e)
        }
    }

    fun cancelDailyReminder(context: Context, uniqueWorkName: String) {
        val appContext = context.applicationContext
        ioScope.launch {
            workMutex.withLock {
                cancelDailyReminderBlocking(appContext, uniqueWorkName)
            }
        }
    }

    private fun cancelDailyReminderBlocking(context: Context, uniqueWorkName: String) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
            Log.d(TAG, "Cancelled WorkManager daily reminder ($uniqueWorkName)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel WorkManager daily reminder $uniqueWorkName", e)
        }
    }

    /**
     * Triggers an immediate one-time WorkManager reminder off the Main Thread.
     */
    fun enqueueTestReminderWork(context: Context, message: String) {
        val appContext = context.applicationContext
        ioScope.launch {
            workMutex.withLock {
                ensureNotificationChannel(appContext)
                val inputData = Data.Builder()
                    .putInt(DailyJournalReminderWorker.KEY_REMINDER_SLOT, REMINDER_SLOT_TEST)
                    .putString(
                        DailyJournalReminderWorker.KEY_REMINDER_MESSAGE,
                        message.trim().ifBlank { DailyJournalReminderWorker.DEFAULT_REMINDER_MESSAGE }
                    )
                    .build()

                val testRequest = OneTimeWorkRequestBuilder<DailyJournalReminderWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    WORK_NAME_TEST,
                    ExistingWorkPolicy.REPLACE,
                    testRequest
                )
            }
        }
    }

    /**
     * Observes whether a given unique WorkManager daily reminder is actively enqueued or running,
     * initializing WorkManager off the Main Thread.
     */
    fun observeReminderActive(context: Context, uniqueWorkName: String): Flow<Boolean> = flow {
        val workManager = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context.applicationContext)
        }
        emitAll(
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
                .map { infos ->
                    infos.any { info ->
                        info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
                    }
                }
        )
    }.flowOn(Dispatchers.IO)

    fun computeInitialDelayMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(1_000L)
    }

    fun formatNextReminderCountdown(hour: Int, minute: Int): String {
        val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(computeInitialDelayMillis(hour, minute))
        val hours = delayMinutes / 60
        val mins = delayMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "Next in ${hours}h ${mins}m"
            hours > 0 -> "Next in ${hours}h"
            mins > 0 -> "Next in ${mins}m"
            else -> "Coming up shortly"
        }
    }

    fun showReminderNotification(context: Context, notificationId: Int, message: String): Boolean {
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification.")
                return false
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (notificationId) {
            REMINDER_SLOT_MORNING -> "Good Morning from BrightDay"
            REMINDER_SLOT_EVENING -> "Evening Reflection in BrightDay"
            else -> "BrightDay Gentle Reminder"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission error", e)
            false
        }
    }
}
