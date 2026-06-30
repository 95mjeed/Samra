package com.samra.downloader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * Tappable surface with a springy scale-down while pressed. Replaces a plain
 * `.clickable {}` and is direction-agnostic (scale is symmetric → RTL-safe).
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scaleDown else 1f, Motion.snappy(), label = "bounce")
    this
        .scale(s)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Like [bounceClick] but with a long-press handler too — used for library
 * multi-select (long-press a book to start selecting, tap to toggle/open).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.bounceCombined(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.snappy(), label = "bounceCombined")
    this
        .scale(s)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/**
 * Scale-only press feedback for surfaces that already own their click/interaction
 * (e.g. an icon whose `clickable` lives elsewhere). Pass the same interaction source.
 */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource, scaleDown: Float = 0.9f): Float {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.snappy(), label = "pressScale")
    return s
}
