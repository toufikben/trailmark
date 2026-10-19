package dev.toufik.trailmark.app.plan

import android.content.Context
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.util.GHUtility
import com.graphhopper.util.shapes.GHPoint
import dev.toufik.trailmark.core.geo.ElevPoint
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

    /** A directory counts as a graph when GraphHopper's properties file is in it. */
    private fun isGraph(dir: File): Boolean = File(dir, "properties").isFile

    fun hasGraphs(context: Context): Boolean =
        graphsDir(context).listFiles()?.any { it.isDirectory && isGraph(it) } == true

    @Synchronized
    fun route(
        context: Context,
        waypoints: List<Pair<Double, Double>>,
        sport: Sport,
    ): List<ElevPoint>? {
        if (waypoints.size < 2) return null
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
