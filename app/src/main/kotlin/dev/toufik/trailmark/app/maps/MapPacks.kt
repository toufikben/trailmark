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

/** Where the app fetches the offline routing graph catalogue. */
const val ROUTING_MANIFEST_URL =
    "https://github.com/toufikben/trailmark-maps/releases/download/routing-latest/routing-manifest.json"

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

    /** Where downloaded routing graph zips live until [GhRouter] unpacks them. */
    fun routingPacksDir(context: Context): File =
        File(context.filesDir, "routing-packs").apply { mkdirs() }

    fun manager(context: Context): RegionManager =
        RegionManager(FileRegionFiles(dir(context)), HttpBytesFetcher)

    fun routingManager(context: Context): RegionManager =
        RegionManager(FileRegionFiles(routingPacksDir(context)), HttpBytesFetcher)

    /** The installed map pack file, if any (routing/terrain use other suffixes). */
    fun installedMapPack(context: Context): File? =
        dir(context).listFiles()?.firstOrNull { it.name.endsWith(".map.pack") }

    /** Every installed map pack, name-sorted for a stable fallback order. */
    fun installedMapPacks(context: Context): List<File> =
        (dir(context).listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".map.pack") }
            .sortedBy { it.name }

    /**
     * Bounds baked into a PMTiles v3 header as (minLon, minLat, maxLon, maxLat)
     * in degrees, or null when the file is not a readable PMTiles archive.
     */
    fun packBounds(pack: File): DoubleArray? = runCatching {
        java.io.RandomAccessFile(pack, "r").use { raf ->
            val head = ByteArray(127)
            raf.readFully(head)
            if (String(head, 0, 7, Charsets.US_ASCII) != "PMTiles") return@use null
            if (head[7].toInt() != 3) return@use null
            fun i32(at: Int): Int =
                (head[at].toInt() and 0xFF) or
                    ((head[at + 1].toInt() and 0xFF) shl 8) or
                    ((head[at + 2].toInt() and 0xFF) shl 16) or
                    ((head[at + 3].toInt() and 0xFF) shl 24)
            doubleArrayOf(i32(102) / 1e7, i32(106) / 1e7, i32(110) / 1e7, i32(114) / 1e7)
        }
    }.getOrNull()

    /** Best pack for a point: the one whose bounds contain it, else the first installed. */
    fun packFor(context: Context, lat: Double?, lon: Double?): File? {
        val packs = installedMapPacks(context)
        if (packs.size <= 1 || lat == null || lon == null) return packs.firstOrNull()
        return packs.firstOrNull { p ->
            val b = packBounds(p) ?: return@firstOrNull false
            lat >= b[1] && lat <= b[3] && lon >= b[0] && lon <= b[2]
        } ?: packs.first()
    }
}
