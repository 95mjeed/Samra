package com.samra.downloader.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * Central motion spec for Samra so every transition shares the same feel.
 * Uses a Material-style emphasized-decelerate easing and gentle springs.
 */
object Motion {
    // Emphasized decelerate — content arrives fast then eases to rest.
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val Fast = 200
    const val Med = 300
    const val Slow = 420

    /** Smooth, low-bounce spring for offset/float values (scrubbers, progress, sheets). */
    fun <T> gentle() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** A touch springier — for indicators / selection chrome. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    /** Fade-through with a subtle scale — direction-agnostic, safe for RTL screen swaps. */
    fun screenSwap(): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        (fadeIn(tween(Med, easing = Emphasized)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(Med, easing = Emphasized)))
            .togetherWith(fadeOut(tween(Fast, easing = Standard)))
    }

    /** Mini-player / docked chrome: slide up from the bottom with a fade. */
    fun dockEnter(): EnterTransition =
        slideInVertically(tween(Med, easing = Emphasized)) { it } + fadeIn(tween(Med))

    fun dockExit(): ExitTransition =
        slideOutVertically(tween(Fast, easing = Standard)) { it } + fadeOut(tween(Fast))
}
