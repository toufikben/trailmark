package dev.toufik.trailmark.app.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The fingerprint is what turns "the archive held a file with this name" into
 * "the archive held these exact bytes", so the vectors are pinned and the
 * streamed digest has to agree with the in-memory one.
 */
class FileHashTest {

    @Test
    fun theDigestMatchesThePublishedVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun aTruncatedFileNoLongerMatches() {
        val good = "the bytes a photo was saved with".toByteArray()
        val truncated = good.copyOf(good.size - 3)
        assertNotEquals(sha256Hex(good), sha256Hex(truncated))
    }

    @Test
    fun theStreamedDigestAgreesWithTheInMemoryOne() {
        val bytes = ByteArray(200_000) { (it * 31 and 0xFF).toByte() }
        val file = File.createTempFile("trailmark-hash", ".bin")
        try {
            file.writeBytes(bytes)
            assertEquals(sha256Hex(bytes), sha256Hex(file))
        } finally {
            file.delete()
        }
    }
}
