package dev.toufik.trailmark.regions

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One downloadable pack: the offline map, terrain, or routing graph for a region. */
@Serializable
data class RegionPack(
    val id: String,
    val name: String,
    val kind: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class RegionManifest(
    val version: Int,
    val packs: List<RegionPack> = emptyList(),
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseManifest(text: String): RegionManifest = json.decodeFromString(text)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A pack is accepted only when BOTH its byte count and its SHA-256 match the manifest. */
fun verifyPack(bytes: ByteArray, pack: RegionPack): Boolean =
    bytes.size.toLong() == pack.sizeBytes && sha256Hex(bytes).equals(pack.sha256, ignoreCase = true)
