package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.EmotionCategory
import com.example.model.EntryType
import com.example.model.JournalEntry
import com.example.model.SentimentPolarity
import com.example.model.TextScaleLevel
import com.example.model.UserPreferences
import com.example.model.WeeklySummary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JournalRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeEntries(userId: String): Flow<List<JournalEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/journal_entries"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("journal_entries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JournalEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWeeklySummaries(userId: String): Flow<List<WeeklySummary>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/weekly_summaries"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("weekly_summaries")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WeeklySummary::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePreferences(userId: String): Flow<UserPreferences> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/preferences/settings"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("preferences")
                .document("settings")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferences(userId = uid)
                    } else {
                        UserPreferences(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addEntry(
        dateKey: String,
        title: String,
        content: String,
        entryType: EntryType,
        audioFileName: String?,
        audioDurationSec: Int,
        primaryEmotion: EmotionCategory,
        sentimentPolarity: SentimentPolarity,
        sentimentScore: Int,
        positiveHighlight: String?,
        promptUsed: String?,
        keyTopics: List<String>
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().ifBlank { "My Memory" }.take(120)
            val safeContent = content.trim().ifBlank { "Voice memory saved." }.take(5000)
            val safeTopics = keyTopics
                .map { it.trim().take(60) }
                .filter { it.isNotBlank() }
                .take(5)
            val safeAudioFile = audioFileName?.trim()?.takeIf { it.isNotBlank() }?.take(200)
            val safeHighlight = positiveHighlight?.trim()?.takeIf { it.isNotBlank() }?.take(400)
            val safePrompt = promptUsed?.trim()?.takeIf { it.isNotBlank() }?.take(300)

            val docRef = db.collection("users")
                .document(uid)
                .collection("journal_entries")
                .document()
            docPath = docRef.path

            val rawPayload: Map<String, Any?> = mapOf(
                "userId" to uid,
                "dateKey" to dateKey.take(10),
                "title" to safeTitle,
                "content" to safeContent,
                "entryType" to entryType.name,
                "audioFileName" to safeAudioFile,
                "audioDurationSec" to audioDurationSec.coerceIn(0, 3600).toLong(),
                "primaryEmotion" to primaryEmotion.name,
                "sentimentPolarity" to sentimentPolarity.name,
                "sentimentScore" to sentimentScore.coerceIn(0, 100).toLong(),
                "positiveHighlight" to safeHighlight,
                "promptUsed" to safePrompt,
                "keyTopics" to safeTopics,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val payload = rawPayload.filterValues { it != null }.mapValues { it.value!! }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEntryEmotion(
        entryId: String,
        newEmotion: EmotionCategory
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries/$entryId"
        return try {
            val uid = requireUserId()
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("journal_entries")
                .document(entryId)
            docPath = docRef.path

            val newPolarity = when {
                newEmotion.positiveMood -> SentimentPolarity.POSITIVE
                newEmotion == EmotionCategory.REFLECTIVE -> SentimentPolarity.NEUTRAL
                else -> SentimentPolarity.NEEDS_CARE
            }

            docRef.update(
                mapOf(
                    "primaryEmotion" to newEmotion.name,
                    "sentimentPolarity" to newPolarity.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(entryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries/$entryId"
        return try {
            val uid = requireUserId()
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("journal_entries")
                .document(entryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveWeeklySummary(
        weekId: String,
        weekLabel: String,
        headline: String,
        summaryText: String,
        highlights: List<String>,
        encouragingClosing: String,
        positiveEntryCount: Int,
        totalEntryCount: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/weekly_summaries/$weekId"
        return try {
            val uid = requireUserId()
            val safeWeekId = weekId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "current_week" }
            val docRef = db.collection("users")
                .document(uid)
                .collection("weekly_summaries")
                .document(safeWeekId)
            docPath = docRef.path

            val safeHighlights = highlights
                .map { it.trim().take(300) }
                .filter { it.isNotBlank() }
                .take(5)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "weekLabel" to weekLabel.trim().ifBlank { "This Week" }.take(80),
                        "headline" to headline.trim().ifBlank { "Your Bright Moments" }.take(160),
                        "summaryText" to summaryText.trim().ifBlank { "Here are your positive memories." }.take(2000),
                        "highlights" to safeHighlights,
                        "encouragingClosing" to encouragingClosing.trim().ifBlank { "Wishing you a calm and bright day." }.take(300),
                        "positiveEntryCount" to positiveEntryCount.coerceIn(0, 500).toLong(),
                        "totalEntryCount" to totalEntryCount.coerceIn(0, 500).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "weekLabel" to weekLabel.trim().ifBlank { "This Week" }.take(80),
                        "headline" to headline.trim().ifBlank { "Your Bright Moments" }.take(160),
                        "summaryText" to summaryText.trim().ifBlank { "Here are your positive memories." }.take(2000),
                        "highlights" to safeHighlights,
                        "encouragingClosing" to encouragingClosing.trim().ifBlank { "Wishing you a calm and bright day." }.take(300),
                        "positiveEntryCount" to positiveEntryCount.coerceIn(0, 500).toLong(),
                        "totalEntryCount" to totalEntryCount.coerceIn(0, 500).toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun savePreferences(prefs: UserPreferences): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/preferences/settings"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users")
                .document(uid)
                .collection("preferences")
                .document("settings")
            docPath = docRef.path

            val safeMessage = prefs.reminderMessage.trim()
                .ifBlank { "Time to share a gentle thought in BrightDay." }
                .take(200)
            val safeScale = TextScaleLevel.fromRaw(prefs.textScaleLevel).name

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "morningReminderEnabled" to prefs.morningReminderEnabled,
                        "morningHour" to prefs.morningHour.coerceIn(0L, 23L),
                        "morningMinute" to prefs.morningMinute.coerceIn(0L, 59L),
                        "eveningReminderEnabled" to prefs.eveningReminderEnabled,
                        "eveningHour" to prefs.eveningHour.coerceIn(0L, 23L),
                        "eveningMinute" to prefs.eveningMinute.coerceIn(0L, 59L),
                        "reminderMessage" to safeMessage,
                        "highContrastDark" to prefs.highContrastDark,
                        "textScaleLevel" to safeScale,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "morningReminderEnabled" to prefs.morningReminderEnabled,
                        "morningHour" to prefs.morningHour.coerceIn(0L, 23L),
                        "morningMinute" to prefs.morningMinute.coerceIn(0L, 59L),
                        "eveningReminderEnabled" to prefs.eveningReminderEnabled,
                        "eveningHour" to prefs.eveningHour.coerceIn(0L, 23L),
                        "eveningMinute" to prefs.eveningMinute.coerceIn(0L, 59L),
                        "reminderMessage" to safeMessage,
                        "highContrastDark" to prefs.highContrastDark,
                        "textScaleLevel" to safeScale,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
