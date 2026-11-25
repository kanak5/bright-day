package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val LexendFontFamily = FontFamily(
    Font(R.font.lexend, FontWeight.Normal),
    Font(R.font.lexend, FontWeight.Medium),
    Font(R.font.lexend, FontWeight.SemiBold),
    Font(R.font.lexend, FontWeight.Bold)
)

val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible, FontWeight.Medium),
    Font(R.font.atkinson_hyperlegible, FontWeight.Bold)
)

fun createBrightDayTypography(scaleFactor: Float = 1.0f): Typography {
    fun s(value: Float) = (value * scaleFactor).sp
    return Typography(
        displaySmall = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(30f),
            lineHeight = s(38f),
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(24f),
            lineHeight = s(32f),
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(21f),
            lineHeight = s(28f),
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(20f),
            lineHeight = s(28f),
            letterSpacing = 0.15.sp
        ),
        titleMedium = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(18f),
            lineHeight = s(25f),
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(16f),
            lineHeight = s(22f),
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = AtkinsonFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(18f),
            lineHeight = s(27f),
            letterSpacing = 0.25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = AtkinsonFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(16f),
            lineHeight = s(24f),
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = AtkinsonFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(14f),
            lineHeight = s(20f),
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(17f),
            lineHeight = s(22f),
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(15f),
            lineHeight = s(20f),
            letterSpacing = 0.2.sp
        ),
        labelSmall = TextStyle(
            fontFamily = LexendFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(13f),
            lineHeight = s(18f),
            letterSpacing = 0.2.sp
        )
    )
}
