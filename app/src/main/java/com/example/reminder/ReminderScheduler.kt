package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.UserPreferences
import java.util.Calendar

private const val TAG = "BrightDayReminders"
private const val CHANNEL_ID = "brightday_daily_reminders"
private const val CHANNEL_NAME = "Daily Journal Reminders"
private const val EXTRA_SLOT = "reminder_slot"
private const val EXTRA_MESSAGE = "reminder_message"
private const val EXTRA_HOUR = "reminder_hour"
private const val EXTRA_MINUTE = "reminder_minute"

const val REMINDER_SLOT_MORNING = 1001
const val REMINDER_SLOT_EVENING = 1002
const val REMINDER_SLOT_TEST = 1099

class JournalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(EXTRA_SLOT, REMINDER_SLOT_MORNING)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: "Time to share a gentle thought in BrightDay."
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)

        ReminderScheduler.showReminderNotification(context, slot, message)

        // Schedule next day's reminder automatically
        if (hour in 0..23 && minute in 0..59) {
            ReminderScheduler.scheduleSingleSlot(
                context = context,
                slot = slot,
                hour = hour,
                minute = minute,
                message = message,
                forceNextDay = true
            )
        }
    }
}

object ReminderScheduler {

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

    fun syncReminders(context: Context, prefs: UserPreferences) {
        ensureNotificationChannel(context)
        val message = prefs.reminderMessage.ifBlank {
            "Time to share a gentle thought in BrightDay."
        }
        if (prefs.morningReminderEnabled) {
            scheduleSingleSlot(
                context = context,
                slot = REMINDER_SLOT_MORNING,
                hour = prefs.morningHour.toInt().coerceIn(0, 23),
                minute = prefs.morningMinute.toInt().coerceIn(0, 59),
                message = message,
                forceNextDay = false
            )
        } else {
            cancelSlot(context, REMINDER_SLOT_MORNING)
        }

        if (prefs.eveningReminderEnabled) {
            scheduleSingleSlot(
                context = context,
                slot = REMINDER_SLOT_EVENING,
                hour = prefs.eveningHour.toInt().coerceIn(0, 23),
                minute = prefs.eveningMinute.toInt().coerceIn(0, 59),
                message = message,
                forceNextDay = false
            )
        } else {
            cancelSlot(context, REMINDER_SLOT_EVENING)
        }
    }

    fun scheduleSingleSlot(
        context: Context,
        slot: Int,
        hour: Int,
        minute: Int,
        message: String,
        forceNextDay: Boolean
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, JournalReminderReceiver::class.java).apply {
                putExtra(EXTRA_SLOT, slot)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_HOUR, hour)
                putExtra(EXTRA_MINUTE, minute)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (forceNextDay || timeInMillis <= System.currentTimeMillis() + 5_000L) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Use setAndAllowWhileIdle (no restricted SCHEDULE_EXACT_ALARM permission required)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCal.timeInMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminder for slot $slot", e)
        }
    }

    fun cancelSlot(context: Context, slot: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, JournalReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slot,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel reminder slot $slot", e)
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
