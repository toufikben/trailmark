package dev.toufik.trailmark.app.maps

import android.content.Context
import dev.toufik.trailmark.regions.BytesFetcher
import dev.toufik.trailmark.regions.RegionFiles
import dev.toufik.trailmark.regions.RegionManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where the app fetches the pack catalogue. */
const val MANIFEST_URL =
    "https://github.com/toufikben/trailmark-maps/releases/download/maps-latest/manifest.json"

/** The global online basemap. Also the style offline areas are downloaded for. */
const val ONLINE_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
const val ONLINE_STYLE_URL_LIGHT = "https://tiles.openfreemap.org/styles/positron"

/** A File-backed [RegionFiles] rooted in the app's private storage. */
class FileRegionFiles(private val dir: File) : RegionFiles {
    private fun f(name: String) = File(dir, name)
    override fun exists(name: String) = f(name).exists()
    override fun size(name: String) = f(name).length()
    override fun write(name: String, bytes: ByteArray) = f(name).writeBytes(bytes)
    override fun delete(name: String) { f(name).delete() }
}

/** Downloads bytes with a redirect-following HTTP GET (GitHub Releases redirects). */
object HttpBytesFetcher : BytesFetcher {
    override fun fetch(url: String): ByteArray {
        var current = URL(url)
        repeat(5) {
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location") ?: return@repeat
                current = URL(next)
                return@repeat
            }
            return conn.inputStream.use { it.readBytes() }
        }
        error("too many redirects fetching $url")
    }
}

object MapPacks {
    fun dir(context: Context): File = File(context.filesDir, "maps").apply { mkdirs() }

    fun manager(context: Context): RegionManager =
        RegionManager(FileRegionFiles(dir(context)), HttpBytesFetcher)

    /** The installed map pack file, if any (routing/terrain use other suffixes). */
    fun installedMapPack(context: Context): File? =
        dir(context).listFiles()?.firstOrNull { it.name.endsWith(".map.pack") }
}
