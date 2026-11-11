package dev.toufik.trailmark.app.backup

import java.io.File
import java.security.MessageDigest

/**
 * The fingerprint that ties a media entry in the archive to the bytes it claims
 * to ship. Computed over the exact bytes, so a truncated download, a partially
 * written file or a swapped entry is caught at restore time instead of showing
 * up later as a broken photo.
 */
internal fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

/** Same digest, streamed, so a large clip never has to sit in memory twice. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return hex(digest.digest())
}

private fun hex(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return out.toString()
}

private const val HEX = "0123456789abcdef"
