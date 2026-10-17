package dev.toufik.trailmark.app.maps

import android.content.Context
import java.io.File

/**
 * Copies the bundled SDF glyph ranges into app storage once, so the map style
 * can point at a plain `file://` glyphs template (the scheme MapLibre Native
 * resolves reliably on Android).
 */
object GlyphStore {
    private val RANGES = listOf(
        "0-255", "256-511", "512-767", "768-1023", "1024-1279",
        "1280-1535", "1536-1791", "1792-2047", "2048-2303",
    )
    private const val VERSION = "v1"

    fun ensure(context: Context): File {
        val root = File(context.filesDir, "glyphs")
        val marker = File(root, ".$VERSION")
        if (marker.exists()) return root
        val stack = File(root, "TrailmarkSans").apply { mkdirs() }
        for (r in RANGES) {
            context.assets.open("glyphs/TrailmarkSans/$r.pbf").use { input ->
                File(stack, "$r.pbf").outputStream().use { output -> input.copyTo(output) }
            }
        }
        marker.writeText(VERSION)
        return root
    }
}
