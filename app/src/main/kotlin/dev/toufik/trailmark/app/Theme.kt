package dev.toufik.trailmark.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Trailmark's dark athletic identity. Tokens are laws; the contrast test will pin them. */
object Palette {
    val bg = Color(0xFF0E1113)
    val surface = Color(0xFF171B1E)
    val text = Color(0xFFF4F7F2)
    val textSecondary = Color(0xFF9DA4A1)
    val accent = Color(0xFFC6FF3D)
    val onAccent = Color(0xFF0E1113)
    val danger = Color(0xFFB42318)
}

@Composable
fun TrailmarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.bg,
            surface = Palette.surface,
            onBackground = Palette.text,
            onSurface = Palette.text,
            primary = Palette.accent,
            onPrimary = Palette.onAccent,
            error = Palette.danger,
            onError = Palette.text,
        ),
        content = content,
    )
}
