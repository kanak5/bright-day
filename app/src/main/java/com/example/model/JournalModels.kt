package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EmotionCategory(
    val displayName: String,
    val gentleDescription: String,
    val positiveMood: Boolean
) {
    JOYFUL("Joyful", "Happy, cheerful, or smiling moments", true),
    PEACEFUL("Peaceful", "Calm, relaxed, and cozy feelings", true),
    GRATEFUL("Grateful", "Thankful for people, care, or small gifts", true),
    REFLECTIVE("Reflective", "Thinking back on memories or quiet thoughts", false),
    WORRIED("Worried", "Feeling unsure, anxious, or needing comfort", false),
    SAD("Sad", "Feeling down, lonely, or tender today", false);

    companion object {
        fun fromRaw(raw: String?): EmotionCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: REFLECTIVE
    }
}

enum class SentimentPolarity(val displayName: String) {
    POSITIVE("Uplifting"),
    NEUTRAL("Calm & Balanced"),
    NEEDS_CARE("Gentle Support");

    companion object {
        fun fromRaw(raw: String?): SentimentPolarity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NEUTRAL
    }
}

enum class EntryType {
    AUDIO,
    TEXT;

    companion object {
        fun fromRaw(raw: String?): EntryType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: TEXT
    }
}

enum class TextScaleLevel {
    STANDARD,
    LARGE,
    EXTRA_LARGE;

    companion object {
        fun fromRaw(raw: String?): TextScaleLevel =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: LARGE
    }
}

enum class DashboardPeriod {
    TODAY,
    WEEK,
    ALL
}

@Keep
@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val title: String = "",
    val content: String = "",
    val entryType: String = EntryType.TEXT.name,
    val audioFileName: String? = null,
    val audioDurationSec: Long = 0L,
    val primaryEmotion: String = EmotionCategory.REFLECTIVE.name,
    val sentimentPolarity: String = SentimentPolarity.NEUTRAL.name,
    val sentimentScore: Long = 50L,
    val positiveHighlight: String? = null,
    val promptUsed: String? = null,
    val keyTopics: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val emotionEnum: EmotionCategory
        get() = EmotionCategory.fromRaw(primaryEmotion)

    val polarityEnum: SentimentPolarity
        get() = SentimentPolarity.fromRaw(sentimentPolarity)

    val entryTypeEnum: EntryType
        get() = EntryType.fromRaw(entryType)
}

@Keep
@IgnoreExtraProperties
data class WeeklySummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val weekLabel: String = "",
    val headline: String = "",
    val summaryText: String = "",
    val highlights: List<String> = emptyList(),
    val encouragingClosing: String = "",
    val positiveEntryCount: Long = 0L,
    val totalEntryCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "settings",
    val userId: String = "",
    val morningReminderEnabled: Boolean = true,
    val morningHour: Long = 9L,
    val morningMinute: Long = 30L,
    val eveningReminderEnabled: Boolean = true,
    val eveningHour: Long = 18L,
    val eveningMinute: Long = 30L,
    val reminderMessage: String = "Time to share a gentle thought in BrightDay.",
    val highContrastDark: Boolean = false,
    val textScaleLevel: String = TextScaleLevel.LARGE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val textScaleEnum: TextScaleLevel
        get() = TextScaleLevel.fromRaw(textScaleLevel)
}

data class EmotionCountStat(
    val emotion: EmotionCategory,
    val count: Int,
    val fraction: Float
)

data class DailyEmotionAccumulation(
    val dateKey: String,
    val formattedDate: String,
    val dominantEmotion: EmotionCategory,
    val emotionCounts: Map<EmotionCategory, Int>,
    val entries: List<JournalEntry>,
    val averageSentimentScore: Int
)

data class AnalyzedJournalResult(
    val transcript: String,
    val title: String,
    val primaryEmotion: EmotionCategory,
    val sentimentPolarity: SentimentPolarity,
    val sentimentScore: Int,
    val positiveHighlight: String,
    val keyTopics: List<String>
)

data class GeneratedWeeklySummary(
    val headline: String,
    val summaryText: String,
    val highlights: List<String>,
    val encouragingClosing: String
)
