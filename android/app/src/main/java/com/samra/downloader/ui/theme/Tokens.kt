package com.samra.downloader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens — mirrors the prototype's PAL (dark primary, light). */
data class SamraColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val card2: Color,
    val input: Color,
    val line: Color,
    val line2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val success: Color,
    val info: Color,
    val error: Color,
    val queued: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val DarkColors = SamraColors(
    bg = Color(0xFF0E0F12),
    surface = Color(0xFF15171D),
    card = Color(0xFF1B1E26),
    card2 = Color(0xFF23262F),
    input = Color(0xFF0A0B0E),
    line = Color(0xFF262A33),
    line2 = Color(0xFF343A47),
    text = Color(0xFFF3F1EC),
    text2 = Color(0xFF9BA1AC),
    text3 = Color(0xFF5F646F),
    accent = Color(0xFFECA93C),
    accentDeep = Color(0xFFC9881F),
    accentSoft = Color(0xFFECA93C).copy(alpha = 0.13f),
    success = Color(0xFF4FB985),
    info = Color(0xFF5B8DEF),
    error = Color(0xFFE5594E),
    queued = Color(0xFF6B7280),
    onAccent = Color(0xFF1A1206),
    isDark = true,
)

val LightColors = SamraColors(
    bg = Color(0xFFFBF8F1),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF4EFE6),
    input = Color(0xFFF4EFE6),
    line = Color(0xFFEBE4D7),
    line2 = Color(0xFFDCD4C4),
    text = Color(0xFF1A1C20),
    text2 = Color(0xFF5B616A),
    text3 = Color(0xFF9AA0A8),
    accent = Color(0xFFC9881F),
    accentDeep = Color(0xFFA86E12),
    accentSoft = Color(0xFFC9881F).copy(alpha = 0.13f),
    success = Color(0xFF2E9E66),
    info = Color(0xFF3B6FD4),
    error = Color(0xFFCE4438),
    queued = Color(0xFFA7ADB5),
    onAccent = Color(0xFFFFFFFF),
    isDark = false,
)

val LocalSamraColors = staticCompositionLocalOf { DarkColors }
