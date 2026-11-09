package dev.toufik.trailmark.app.plan

import android.content.Context
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.util.GHUtility
import com.graphhopper.util.shapes.GHPoint
import com.graphhopper.routing.util.EdgeFilter
import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.engine.Sport
import java.io.File
import java.util.Locale

/**
 * Offline road routing on downloaded GraphHopper graphs (built by the
 * trailmark-maps `build-routing` workflow, profile names `foot` and `bike`).
 * Graphs are unpacked under `filesDir/routing/<region>/`.
 *
 * GraphHopper keeps heavy state and is not thread-safe; calls are serialized
 * and the caller runs this on a background dispatcher.
 */
object GhRouter {

    private const val ENCODED_VALUES =
        "foot_access, foot_priority, foot_average_speed, hike_rating, mtb_rating, " +
            "bike_access, bike_priority, bike_average_speed, roundabout"

    private var hopper: GraphHopper? = null
    private var loadedDir: File? = null

    private fun graphsDir(context: Context): File =
        File(context.filesDir, "routing").apply { mkdirs() }

    private fun packsDir(context: Context): File =
        File(context.filesDir, "routing-packs").apply { mkdirs() }

    /** A directory counts as a graph when GraphHopper's properties file is in it. */
    private fun isGraph(dir: File): Boolean = File(dir, "properties").isFile

    fun hasGraphs(context: Context): Boolean =
        graphsDir(context).listFiles()?.any { it.isDirectory && isGraph(it) } == true

    /** The graph folder a downloaded `<id>.routing.pack` zip unpacks into. */
    fun graphDirFor(context: Context, id: String): File = File(graphsDir(context), "$id-gh")

    fun isUnpacked(context: Context, id: String): Boolean = isGraph(graphDirFor(context, id))

    /**
     * Extracts every downloaded routing zip whose graph folder is missing yet.
     * Runs on a background thread; returns the number of graphs unpacked.
     */
    @Synchronized
    fun unpackInstalled(context: Context): Int {
        val out = graphsDir(context).canonicalFile
        var done = 0
        val zips = packsDir(context).listFiles()?.filter { it.name.endsWith(".routing.pack") }
            ?: return 0
        for (zip in zips) {
            val id = zip.name.removeSuffix(".routing.pack")
            if (isGraph(File(out, "$id-gh"))) continue
            val ok = runCatching {
                java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val file = File(out, entry.name)
                        if (!file.canonicalPath.startsWith(out.canonicalPath)) {
                            error("zip entry escapes the routing folder")
                        }
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { zin.copyTo(it) }
                        }
                        entry = zin.nextEntry
                    }
                }
            }.isSuccess
            if (ok) done++
        }
        return done
    }

    /** Removes the unpacked graph and unloads it when it is the open one. */
    @Synchronized
    fun removeGraph(context: Context, id: String): Boolean {
        val dir = graphDirFor(context, id)
        if (loadedDir == dir) {
            runCatching { hopper?.close() }
            hopper = null
            loadedDir = null
        }
        return dir.deleteRecursively()
    }

    @Synchronized
    fun route(
        context: Context,
        waypoints: List<Pair<Double, Double>>,
        sport: Sport,
    ): List<ElevPoint>? {
        if (waypoints.size < 2) return null
        if (!hasGraphs(context)) unpackInstalled(context)
        val dir = graphsDir(context).listFiles()
            ?.firstOrNull { it.isDirectory && isGraph(it) }
            ?: return null
        val h = open(dir) ?: return null
        val profile = if (sport == Sport.BIKE) "bike" else "foot"
        val req = GHRequest().setProfile(profile).setLocale(Locale.US)
        waypoints.forEach { (lat, lon) -> req.addPoint(GHPoint(lat, lon)) }
        val rsp = runCatching { h.route(req) }.getOrNull() ?: return null
        if (rsp.hasErrors()) return null
        val points = rsp.best.points
        val out = ArrayList<ElevPoint>(points.size())
        for (i in 0 until points.size()) {
            out.add(ElevPoint(lat = points.getLat(i), lon = points.getLon(i), eleM = 0.0))
        }
        return if (out.size >= 2) out else null
    }

    /**
     * Pulls a recorded trace onto the road graph: every fix is projected onto the
     * nearest edge when that edge is within [maxOffsetM], so the line follows the
     * street instead of cutting through buildings.
     *
     * A projection that would teleport sideways while the raw fix barely moved is
     * rejected — that is the wrong edge, not a correction. Returns null when no
     * graph is installed or almost nothing could be matched (the caller then
     * keeps drawing the raw trace).
     */
    @Synchronized
    fun snapToRoad(
        context: Context,
        latLons: List<Pair<Double, Double>>,
        maxOffsetM: Double = 35.0,
    ): Pair<List<Pair<Double, Double>>, Double>? {
        if (latLons.size < 4) return null
        if (!hasGraphs(context)) unpackInstalled(context)
        val dir = graphsDir(context).listFiles()
            ?.firstOrNull { it.isDirectory && isGraph(it) }
            ?: return null
        val h = open(dir) ?: return null
        val index = runCatching { h.locationIndex }.getOrNull() ?: return null

        val out = ArrayList<Pair<Double, Double>>(latLons.size)
        var hits = 0
        var lastRaw: Pair<Double, Double>? = null
        var lastOut: Pair<Double, Double>? = null
        for ((lat, lon) in latLons) {
            val snap = runCatching { index.findClosest(lat, lon, EdgeFilter.ALL_EDGES) }.getOrNull()
            var take: Pair<Double, Double>? = null
            if (snap != null && snap.isValid && snap.queryDistance <= maxOffsetM) {
                val p = snap.snappedPoint
                take = p.lat to p.lon
            }
            val prevOut = lastOut
            val prevRaw = lastRaw
            if (take != null && prevOut != null && prevRaw != null) {
                val jump = haversineMeters(prevOut.first, prevOut.second, take.first, take.second)
                val rawJump = haversineMeters(prevRaw.first, prevRaw.second, lat, lon)
                if (jump > rawJump + 25.0 && jump > 35.0) take = null
            }
            if (take != null) {
                hits++
                out += take
                lastOut = take
            } else {
                val raw = lat to lon
                out += raw
                lastOut = raw
            }
            lastRaw = lat to lon
        }
        if (hits < 3) return null
        return out to (hits.toDouble() / latLons.size)
    }

    @Synchronized
    private fun open(dir: File): GraphHopper? {
        hopper?.let { if (loadedDir == dir) return it }
        runCatching { hopper?.close() }
        val h = GraphHopper()
        h.setGraphHopperLocation(dir.absolutePath)
        h.setEncodedValuesString(ENCODED_VALUES)
        h.setProfiles(
            Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")),
            Profile("bike").setCustomModel(GHUtility.loadCustomModelFromJar("bike.json")),
        )
        h.chPreparationHandler.setCHProfiles(CHProfile("foot"), CHProfile("bike"))
        val loaded = runCatching { h.importOrLoad() }.isSuccess
        if (!loaded) {
            runCatching { h.close() }
            return null
        }
        hopper = h
        loadedDir = dir
        return h
    }
}
