@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.samra.downloader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.samra.downloader.R

private fun vw(w: Int) = FontVariation.Settings(FontVariation.weight(w))

/** IBM Plex Sans Arabic — static weights (AR UI). */
val PlexArabic = FontFamily(
    Font(R.font.ibmplexsansarabic_regular, FontWeight.Normal),
    Font(R.font.ibmplexsansarabic_medium, FontWeight.Medium),
    Font(R.font.ibmplexsansarabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibmplexsansarabic_bold, FontWeight.Bold),
    Font(R.font.ibmplexsansarabic_bold, FontWeight.ExtraBold),
)

/** Plus Jakarta Sans — variable (Latin UI). */
val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.plusjakartasans, FontWeight(w), variationSettings = vw(w))
    }
)

/** Lora — variable (Latin serif, reading). */
val Lora = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.lora, FontWeight(w), variationSettings = vw(w))
    }
)

/** Noto Naskh Arabic — variable (AR serif, reading). */
val NotoNaskh = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.notonaskharabic, FontWeight(w), variationSettings = vw(w))
    }
)

// UI font follows the SYSTEM font (Roboto / Noto / the device's chosen font) for both
// languages. It carries both Arabic and Latin glyphs, so the same text (e.g. the app name
// «سَمْره») renders identically regardless of the chosen UI language, and it adapts to the
// user's system font setting — instead of swapping between bundled IBM Plex Arabic / Jakarta.
fun uiFamily(ar: Boolean): FontFamily = FontFamily.Default
fun serifFamily(ar: Boolean): FontFamily = if (ar) NotoNaskh else Lora

val LocalSerifFont = staticCompositionLocalOf<FontFamily> { Lora }
val LocalUiFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
