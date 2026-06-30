package com.samra.downloader.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Builds a squircle (superellipse) [Path] that mirrors the Android adaptive-icon / Samsung
 * One UI launcher mask. [n] controls squareness: 2 = ellipse, higher = squarer; ~4 is the
 * canonical launcher squircle.
 *
 * Prefer DRAWING this path (Canvas `drawPath`, which is antialiased) over using it as a
 * `Modifier.clip` shape — path clipping in Compose is NOT antialiased and leaves jagged edges.
 */
fun squirclePath(size: Size, n: Float = 4f): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val a = size.width / 2f
    val b = size.height / 2f
    val steps = 240
    val exp = 2f / n
    for (i in 0..steps) {
        val t = i.toFloat() / steps * (2f * PI.toFloat())
        val ct = cos(t)
        val st = sin(t)
        val x = cx + a * sign(ct) * abs(ct).pow(exp)
        val y = cy + b * sign(st) * abs(st).pow(exp)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Squircle [Shape] (for clipping). Note: clipping is not antialiased — prefer [squirclePath] + drawPath. */
class SquircleShape(private val n: Float = 4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size, n))
}

val SamraSquircle = SquircleShape()
