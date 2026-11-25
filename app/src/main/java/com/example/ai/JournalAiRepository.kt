package com.example.ai

import android.util.Log
import com.example.model.AnalyzedJournalResult
import com.example.model.EmotionCategory
import com.example.model.GeneratedWeeklySummary
import com.example.model.JournalEntry
import com.example.model.SentimentPolarity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "JournalAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val FAST_MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MILLIS = 60_000L
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024 // Stay safely below 20MB limit

class JournalAiRepository {

    private val promptsSchema = Schema.obj(
        properties = mapOf(
            "prompts" to Schema.array(
                items = Schema.string(description = "A warm, simple, one-sentence journaling starter question"),
                description = "Exactly 3 gentle, easy-to-understand journaling prompts for someone living with dementia"
            )
        )
    )

    private val journalAnalysisSchema = Schema.obj(
        properties = mapOf(
            "transcript" to Schema.string(description = "Clean, accurate transcript of the spoken audio or the written entry text"),
            "title" to Schema.string(description = "Short, warm 3 to 6 word title for this memory"),
            "primaryEmotion" to Schema.enumeration(
                values = listOf("JOYFUL", "PEACEFUL", "GRATEFUL", "REFLECTIVE", "WORRIED", "SAD"),
                description = "Primary emotion detected from natural language sentiment and tone"
            ),
            "sentimentPolarity" to Schema.enumeration(
                values = listOf("POSITIVE", "NEUTRAL", "NEEDS_CARE"),
                description = "Overall emotional polarity of the entry"
            ),
            "sentimentScore" to Schema.integer(description = "Positivity and well-being score from 0 to 100"),
            "positiveHighlight" to Schema.string(description = "One warm, gentle, encouraging sentence highlighting a comforting detail or offering kind reassurance"),
            "keyTopics" to Schema.array(
                items = Schema.string(),
                description = "1 to 4 simple one-or-two-word topics mentioned (such as Family, Garden, Music, Tea)"
            )
        )
    )

    private val weeklySummarySchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Warm, uplifting headline celebrating the week's bright moments"),
            "summaryText" to Schema.string(description = "2 to 3 short, easy-to-read sentences written directly to the user in warm second-person language summarizing their top positive memories this week"),
            "highlights" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 clear, short, comforting bullet memories from the user's happiest moments this week"
            ),
            "encouragingClosing" to Schema.string(description = "One short, reassuring sentence promoting dignity and emotional well-being")
        )
    )

    // Ultra-fast Gemini Flash-Lite for quick, low-latency journaling starter prompts
    private val promptsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 256
                responseMimeType = "application/json"
                responseSchema = promptsSchema
            }
        )
    }

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = journalAnalysisSchema
            }
        )
    }

    private val weeklySummaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = weeklySummarySchema
            }
        )
    }

    suspend fun generateStarterPrompts(
        timeOfDay: String,
        recentTopics: List<String>
    ): Result<List<String>> {
        return try {
            val topicsContext = if (recentTopics.isNotEmpty()) {
                "Recent favorite topics they enjoyed talking about: ${recentTopics.distinct().take(6).joinToString(", ")}."
            } else {
                "Focus on comforting everyday sensory joys like favorite songs, cozy drinks, family smiles, nature, or peaceful routines."
            }

            val prompt = """
                You are a kind, gentle companion helping a person living with dementia start a daily journal entry.
                It is currently $timeOfDay.
                $topicsContext
                Generate exactly 3 short, concrete, reassuring journaling starter questions.
                Rules:
                - Use simple, everyday words and short sentences (under 15 words each).
                - Never quiz or test memory (avoid "What did you eat yesterday?" or "Do you remember?").
                - Invite warm feelings, sensory comfort, or favorite stories (e.g., "What is a song that always makes you smile?").
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    promptsModel.generateContent(prompt)
                }
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))

            val json = JSONObject(rawJson)
            val arr = json.getJSONArray("prompts")
            val prompts = (0 until arr.length())
                .map { arr.getString(it).trim() }
                .filter { it.isNotBlank() }

            if (prompts.isEmpty()) {
                Result.failure(IllegalStateException("Could not generate journaling suggestions."))
            } else {
                Result.success(prompts)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Prompt generation timed out", e)
            Result.failure(Exception("Getting journaling ideas timed out. Please tap Try Again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate starter prompts", e)
            Result.failure(Exception(e.localizedMessage ?: "Could not load journaling suggestions."))
        }
    }

    suspend fun analyzeAudioJournal(
        audioFile: File,
        promptUsed: String?
    ): Result<AnalyzedJournalResult> {
        return try {
            val audioBytes = withContext(Dispatchers.IO) {
                if (!audioFile.exists() || audioFile.length() <= 0L) {
                    throw IllegalStateException("Audio recording file is empty or missing.")
                }
                if (audioFile.length() > MAX_INLINE_AUDIO_BYTES) {
                    throw IllegalStateException("Audio recording is too large to analyze. Please keep recordings under 5 minutes.")
                }
                audioFile.readBytes()
            }

            val promptHint = if (!promptUsed.isNullOrBlank()) {
                "The user was responding to the journaling prompt: \"$promptUsed\"."
            } else {
                "The user recorded a free-form audio diary entry."
            }

            val inputContent = content {
                inlineData(audioBytes, "audio/mp4")
                text(
                    """
                    Listen carefully to this personal audio diary entry from a user living with dementia.
                    $promptHint
                    Perform natural language processing (NLP) transcription and sentiment/emotion analysis:
                    1. Transcribe the spoken words faithfully and cleanly in 'transcript'. If the audio is very quiet or mostly silent, describe gently what was heard (e.g., "Quiet audio recording captured.").
                    2. Write a short, warm 3-6 word 'title'.
                    3. Classify 'primaryEmotion' as one of: JOYFUL, PEACEFUL, GRATEFUL, REFLECTIVE, WORRIED, SAD.
                    4. Classify 'sentimentPolarity' as POSITIVE, NEUTRAL, or NEEDS_CARE.
                    5. Provide 'sentimentScore' from 0 (distressed) to 100 (deeply joyful/positive).
                    6. Write one short, kind, reassuring sentence in 'positiveHighlight' celebrating a bright detail or offering warm comfort.
                    7. Extract 1 to 4 simple topic tags in 'keyTopics'.
                    """.trimIndent()
                )
            }

            val response = withContext(Dispatchers.IO) {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    analysisModel.generateContent(inputContent)
                }
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty analysis."))

            Result.success(parseAnalysisJson(rawJson, fallbackText = "Voice diary entry recorded."))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio NLP analysis timed out", e)
            Result.failure(Exception("Audio analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio NLP analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to analyze audio recording."))
        }
    }

    suspend fun analyzeTextJournal(
        text: String,
        preferredEmotion: EmotionCategory?,
        promptUsed: String?
    ): Result<AnalyzedJournalResult> {
        return try {
            val trimmed = text.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Please write a few words first."))
            }

            val emotionHint = if (preferredEmotion != null) {
                "The user indicated they feel ${preferredEmotion.displayName} (${preferredEmotion.name}); respect this choice for primaryEmotion unless strongly contradicted."
            } else {
                "Detect primaryEmotion purely from the natural language sentiment of the entry."
            }

            val promptHint = if (!promptUsed.isNullOrBlank()) {
                "Journaling prompt shown to user: \"$promptUsed\"."
            } else ""

            val instruction = """
                Analyze this personal journal note written by a user living with dementia using natural language sentiment analysis.
                $promptHint
                $emotionHint
                Journal note:
                "$trimmed"

                Return structured JSON with:
                - 'transcript': keep the exact user note text cleanly.
                - 'title': a warm 3-6 word title.
                - 'primaryEmotion': JOYFUL, PEACEFUL, GRATEFUL, REFLECTIVE, WORRIED, or SAD.
                - 'sentimentPolarity': POSITIVE, NEUTRAL, or NEEDS_CARE.
                - 'sentimentScore': integer 0 to 100.
                - 'positiveHighlight': one short, comforting sentence recognizing the user's feeling or bright moment.
                - 'keyTopics': 1 to 4 simple topic tags.
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    analysisModel.generateContent(instruction)
                }
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))

            val parsed = parseAnalysisJson(rawJson, fallbackText = trimmed)
            val finalResult = if (preferredEmotion != null) {
                parsed.copy(
                    transcript = trimmed,
                    primaryEmotion = preferredEmotion
                )
            } else {
                parsed.copy(transcript = trimmed)
            }
            Result.success(finalResult)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Text NLP analysis timed out", e)
            Result.failure(Exception("Sentiment analysis timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text NLP analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to analyze your journal note."))
        }
    }

    /**
     * Synthesizes the past week's top positive memories into an uplifting, dementia-friendly
     * summary to support emotional well-being and reminiscence.
     */
    suspend fun summarizeWeeklyPositiveMemories(
        weekEntries: List<JournalEntry>,
        weekLabel: String
    ): Result<GeneratedWeeklySummary> {
        return try {
            if (weekEntries.isEmpty()) {
                return Result.failure(IllegalStateException("Add at least one memory this week to create a summary."))
            }

            val prioritized = weekEntries
                .sortedWith(
                    compareByDescending<JournalEntry> { it.emotionEnum.positiveMood }
                        .thenByDescending { it.sentimentScore }
                )
                .take(15)

            val entriesBlock = prioritized.joinToString(separator = "\n\n") { entry ->
                """
                - Date: ${entry.dateKey}
                - Title: ${entry.title}
                - Emotion: ${entry.primaryEmotion} (Positivity Score: ${entry.sentimentScore}/100)
                - Highlight: ${entry.positiveHighlight.orEmpty()}
                - Entry: ${entry.content.take(450)}
                """.trimIndent()
            }

            val prompt = """
                You are creating a warm, uplifting "Weekly Positive Memories" reflection for a person living with dementia to help them feel grounded, valued, and happy.
                Week range: $weekLabel
                Here are their journal entries from this week (prioritizing positive moments):

                $entriesBlock

                Instructions:
                - Focus warmly on the TOP positive memories, moments of comfort, gratitude, joy, peace, or connection.
                - If some entries were worried or sad, gently emphasize resilience, comfort, and any small bright spots without dwelling on distress.
                - Use simple, clear, dignified, reassuring language addressed directly to the user ("You enjoyed...", "This week brought...").
                - Keep sentences short and easy to read aloud.
                - Provide 3 to 5 specific positive memory highlights drawn directly from the entries above (or 1-2 if only 1-2 entries exist).
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    weeklySummaryModel.generateContent(prompt)
                }
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty weekly summary."))

            val json = JSONObject(rawJson)
            val highlightsArr = json.getJSONArray("highlights")
            val highlights = (0 until highlightsArr.length())
                .map { highlightsArr.getString(it).trim() }
                .filter { it.isNotBlank() }
                .take(5)

            Result.success(
                GeneratedWeeklySummary(
                    headline = json.optString("headline", "Your Bright Moments This Week").trim(),
                    summaryText = json.optString("summaryText", "").trim(),
                    highlights = highlights,
                    encouragingClosing = json.optString(
                        "encouragingClosing",
                        "Each day holds gentle moments worth treasuring."
                    ).trim()
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Weekly summary generation timed out", e)
            Result.failure(Exception("Creating your weekly summary took too long. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Weekly summary generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Could not create your weekly positive summary."))
        }
    }

    private fun parseAnalysisJson(rawJson: String, fallbackText: String): AnalyzedJournalResult {
        val json = JSONObject(rawJson)
        val transcript = json.optString("transcript", fallbackText).trim().ifBlank { fallbackText }
        val title = json.optString("title", "Daily Memory").trim().ifBlank { "Daily Memory" }
        val emotion = EmotionCategory.fromRaw(json.optString("primaryEmotion"))
        val polarity = SentimentPolarity.fromRaw(json.optString("sentimentPolarity"))
        val score = json.optInt("sentimentScore", if (emotion.positiveMood) 80 else 50).coerceIn(0, 100)
        val highlight = json.optString("positiveHighlight", "").trim()
        val topicsArr = json.optJSONArray("keyTopics")
        val topics = if (topicsArr != null) {
            (0 until topicsArr.length())
                .map { topicsArr.getString(it).trim() }
                .filter { it.isNotBlank() }
                .take(5)
        } else {
            emptyList()
        }
        return AnalyzedJournalResult(
            transcript = transcript,
            title = title,
            primaryEmotion = emotion,
            sentimentPolarity = polarity,
            sentimentScore = score,
            positiveHighlight = highlight,
            keyTopics = topics
        )
    }
}
