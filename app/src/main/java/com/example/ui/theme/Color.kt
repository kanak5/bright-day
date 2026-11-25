package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light High-Contrast Palette (Warm Ivory & Deep Navy - WCAG AAA)
val LightBackground = Color(0xFFFFFDF7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF3EFE6)
val LightPrimary = Color(0xFF102A43)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD9E6F2)
val LightOnPrimaryContainer = Color(0xFF0A192F)
val LightSecondary = Color(0xFF9A3412)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFFEDD5)
val LightOnSecondaryContainer = Color(0xFF431407)
val LightTertiary = Color(0xFF115E59)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCFBF1)
val LightOnTertiaryContainer = Color(0xFF042F2E)
val LightOnBackground = Color(0xFF0A192F)
val LightOnSurface = Color(0xFF0A192F)
val LightOnSurfaceVariant = Color(0xFF243B53)
val LightOutline = Color(0xFF102A43)
val LightError = Color(0xFFB91C1C)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)

// Ultra High-Contrast Dark Palette (Pure Black, Pure White, Vivid Sun Gold - WCAG AAA)
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1F2937)
val DarkPrimary = Color(0xFFFFD166)
val DarkOnPrimary = Color(0xFF000000)
val DarkPrimaryContainer = Color(0xFF2B2308)
val DarkOnPrimaryContainer = Color(0xFFFFE8A3)
val DarkSecondary = Color(0xFF7DD3FC)
val DarkOnSecondary = Color(0xFF000000)
val DarkSecondaryContainer = Color(0xFF0C2B3D)
val DarkOnSecondaryContainer = Color(0xFFE0F2FE)
val DarkTertiary = Color(0xFF6EE7B7)
val DarkOnTertiary = Color(0xFF000000)
val DarkTertiaryContainer = Color(0xFF063326)
val DarkOnTertiaryContainer = Color(0xFFD1FAE5)
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFFE5E7EB)
val DarkOutline = Color(0xFFFFD166)
val DarkError = Color(0xFFFCA5A5)
val DarkOnError = Color(0xFF000000)
val DarkErrorContainer = Color(0xFF450A0A)
val DarkOnErrorContainer = Color(0xFFFEE2E2)

// Warm Gold Accent for Weekly Positive Memories & Recording
val SunGold = Color(0xFFFFB703)
val DeepAmber = Color(0xFFB45309)
val RecordingCrimson = Color(0xFFDC2626)
val RecordingCrimsonDark = Color(0xFFF87171)

data class EmotionColorSet(
    val container: Color,
    val content: Color,
    val border: Color,
    val bar: Color
)

fun emotionColorSet(emotionName: String, darkHighContrast: Boolean): EmotionColorSet {
    return if (!darkHighContrast) {
        when (emotionName) {
            "JOYFUL" -> EmotionColorSet(
                container = Color(0xFFFEF3C7),
                content = Color(0xFF78350F),
                border = Color(0xFFB45309),
                bar = Color(0xFFD97706)
            )
            "PEACEFUL" -> EmotionColorSet(
                container = Color(0xFFCCFBF1),
                content = Color(0xFF134E4A),
                border = Color(0xFF0F766E),
                bar = Color(0xFF0D9488)
            )
            "GRATEFUL" -> EmotionColorSet(
                container = Color(0xFFDCFCE7),
                content = Color(0xFF14532D),
                border = Color(0xFF15803D),
                bar = Color(0xFF16A34A)
            )
            "REFLECTIVE" -> EmotionColorSet(
                container = Color(0xFFE0E7FF),
                content = Color(0xFF1E1B4B),
                border = Color(0xFF3730A3),
                bar = Color(0xFF4338CA)
            )
            "WORRIED" -> EmotionColorSet(
                container = Color(0xFFF3E8FF),
                content = Color(0xFF3B0764),
                border = Color(0xFF6B21A8),
                bar = Color(0xFF7E22CE)
            )
            else -> EmotionColorSet( // SAD
                container = Color(0xFFDBEAFE),
                content = Color(0xFF1E3A8A),
                border = Color(0xFF1D4ED8),
                bar = Color(0xFF2563EB)
            )
        }
    } else {
        when (emotionName) {
            "JOYFUL" -> EmotionColorSet(
                container = Color(0xFF332600),
                content = Color(0xFFFFE082),
                border = Color(0xFFFFD166),
                bar = Color(0xFFFFD166)
            )
            "PEACEFUL" -> EmotionColorSet(
                container = Color(0xFF052E2B),
                content = Color(0xFF99F6E4),
                border = Color(0xFF5EEAD4),
                bar = Color(0xFF2DD4BF)
            )
            "GRATEFUL" -> EmotionColorSet(
                container = Color(0xFF052E16),
                content = Color(0xFFBBF7D0),
                border = Color(0xFF4ADE80),
                bar = Color(0xFF4ADE80)
            )
            "REFLECTIVE" -> EmotionColorSet(
                container = Color(0xFF1E1B4B),
                content = Color(0xFFC7D2FE),
                border = Color(0xFFA5B4FC),
                bar = Color(0xFF818CF8)
            )
            "WORRIED" -> EmotionColorSet(
                container = Color(0xFF2E1065),
                content = Color(0xFFE9D5FF),
                border = Color(0xFFD8B4FE),
                bar = Color(0xFFC084FC)
            )
            else -> EmotionColorSet( // SAD
                container = Color(0xFF172554),
                content = Color(0xFFBFDBFE),
                border = Color(0xFF93C5FD),
                bar = Color(0xFF60A5FA)
            )
        }
    }
}
