package com.example.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

private const val WORKER_TAG = "DailyJournalReminderWorker"

/**
 * WorkManager worker that delivers a gentle, dementia-friendly daily journaling reminder
 * notification at the user's configured time of day.
 */
class DailyJournalReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val slot = inputData.getInt(KEY_REMINDER_SLOT, REMINDER_SLOT_MORNING)
        val message = inputData.getString(KEY_REMINDER_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_REMINDER_MESSAGE

        Log.d(WORKER_TAG, "Triggering daily WorkManager reminder for slot=$slot")
        ReminderScheduler.showReminderNotification(
            context = applicationContext,
            notificationId = slot,
            message = message
        )
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_SLOT = "key_reminder_slot"
        const val KEY_REMINDER_MESSAGE = "key_reminder_message"
        const val DEFAULT_REMINDER_MESSAGE = "Time to share a gentle thought in BrightDay."
    }
}
