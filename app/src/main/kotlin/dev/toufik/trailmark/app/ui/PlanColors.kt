package dev.toufik.trailmark.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Gradient palette for planned routes: red is the steepest climb, yellow/amber
 * gentler climbing, gray-blue flat, teal/green downhill.
 */
fun gradeColorArgb(gradePercent: Double): Int = when {
    gradePercent >= 9.0 -> 0xFFE5484D.toInt()
    gradePercent >= 5.0 -> 0xFFF76808.toInt()
    gradePercent >= 2.5 -> 0xFFE2B203.toInt()
    gradePercent > -2.5 -> 0xFF8A93A6.toInt()
    gradePercent > -5.0 -> 0xFF12A594.toInt()
    else -> 0xFF30A46C.toInt()
}

fun gradeColorHex(gradePercent: Double): String =
    String.format("#%06X", 0xFFFFFF and gradeColorArgb(gradePercent))

fun gradeColor(gradePercent: Double): Color = Color(gradeColorArgb(gradePercent))
