package dev.toufik.trailmark.regions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeFiles : RegionFiles {
    val map = HashMap<String, ByteArray>()
    override fun exists(name: String) = map.containsKey(name)
    override fun size(name: String) = map[name]?.size?.toLong() ?: 0L
    override fun write(name: String, bytes: ByteArray) { map[name] = bytes }
    override fun delete(name: String) { map.remove(name) }
}

class RegionManifestTest {
    @Test
    fun parsesAPacksManifest() {
        val text = """
            {"version":1,"packs":[
              {"id":"dz-alger","name":"الجزائر","kind":"map","url":"https://x/dz.pmtiles","sizeBytes":3,"sha256":"abc"}
            ]}
        """.trimIndent()
        val m = parseManifest(text)
        assertEquals(1, m.version)
        assertEquals("dz-alger", m.packs.single().id)
        assertEquals(3L, m.packs.single().sizeBytes)
    }

    @Test
    fun sha256MatchesTheKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun verifyRejectsWrongSizeOrHash() {
        val bytes = "abc".toByteArray()
        val good = RegionPack("p", "n", "map", "u", 3, sha256Hex(bytes))
        assertTrue(verifyPack(bytes, good))
        assertFalse(verifyPack(bytes, good.copy(sizeBytes = 4)))
        assertFalse(verifyPack(bytes, good.copy(sha256 = "deadbeef")))
    }
}

class RegionManagerTest {
    private val bytes = "abc".toByteArray()
    private val pack = RegionPack("p", "n", "map", "u", 3, sha256Hex(bytes))

    @Test
    fun installVerifiesThenStoresAndIsIdempotent() {
        val files = FakeFiles()
        var fetches = 0
        val mgr = RegionManager(files) { fetches++; bytes }
        assertFalse(mgr.isInstalled(pack))
        mgr.install(pack)
        assertTrue(mgr.isInstalled(pack))
        mgr.install(pack) // already installed: no second fetch
        assertEquals(1, fetches)
        mgr.remove(pack)
        assertFalse(mgr.isInstalled(pack))
    }

    @Test
    fun installRejectsACorruptDownload() {
        val files = FakeFiles()
        val mgr = RegionManager(files) { "bad".toByteArray() }
        assertThrows(IllegalArgumentException::class.java) { mgr.install(pack) }
        assertFalse(mgr.isInstalled(pack))
    }
}
