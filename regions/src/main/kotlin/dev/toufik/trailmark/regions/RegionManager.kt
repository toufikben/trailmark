package dev.toufik.trailmark.regions

/** Fetches the raw bytes of a URL. Injected so the manager is testable offline. */
fun interface BytesFetcher {
    fun fetch(url: String): ByteArray
}

/** The flat file store the packs live in (a directory on Android, a map in tests). */
interface RegionFiles {
    fun exists(name: String): Boolean
    fun size(name: String): Long
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}

class RegionManager(private val files: RegionFiles, private val fetch: BytesFetcher) {

    fun fileNameFor(pack: RegionPack): String = "${pack.id}.${pack.kind}.pack"

    fun isInstalled(pack: RegionPack): Boolean =
        files.exists(fileNameFor(pack)) && files.size(fileNameFor(pack)) == pack.sizeBytes

    /** Fetch, verify against the manifest, then write. Throws on a corrupt download. */
    fun install(pack: RegionPack) {
        val name = fileNameFor(pack)
        if (isInstalled(pack)) return
        val bytes = fetch.fetch(pack.url)
        require(verifyPack(bytes, pack)) { "pack ${pack.id} failed verification" }
        files.write(name, bytes)
    }

    fun remove(pack: RegionPack) {
        files.delete(fileNameFor(pack))
    }
}
