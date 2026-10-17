package dev.toufik.trailmark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.view.Gravity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.Sport
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** A richer OpenMapTiles style with SDF labels (Arabic first, then French/English). */
private fun vectorStyle(packPath: String, glyphsBase: String, dark: Boolean): String {
    val bg = if (dark) "#0E1113" else "#F5F6F4"
    val landcover = if (dark) "#141A16" else "#EDEFE9"
    val landuse = if (dark) "#161C17" else "#EEF0EA"
    val park = if (dark) "#16241A" else "#DCE7D6"
    val water = if (dark) "#12232E" else "#C8DCEC"
    val building = if (dark) "#1E2429" else "#E1E4E0"
    val roadMinor = if (dark) "#333E48" else "#FFFFFF"
    val roadMajor = if (dark) "#4A5560" else "#FFE2A0"
    val rail = if (dark) "#2A3138" else "#D5D8D4"
    val boundary = if (dark) "#3A444E" else "#C6CAC5"
    val waterName = if (dark) "#8FA3B0" else "#5A7887"
    val label = if (dark) "#E8EEEA" else "#1F2429"
    val labelDim = if (dark) "#9DA4A1" else "#5C636B"
    val halo = if (dark) "#0E1113" else "#FFFFFF"
    return """
    {"version":8,"name":"trailmark-omt",
     "glyphs":"file://$glyphsBase/{fontstack}/{range}.pbf",
     "sources":{"openmaptiles":{"type":"vector","url":"pmtiles://file://$packPath"}},
     "layers":[
       {"id":"background","type":"background","paint":{"background-color":"${bg}"}},
       {"id":"landcover","type":"fill","source":"openmaptiles","source-layer":"landcover",
        "paint":{"fill-color":"${landcover}"}},
       {"id":"landuse","type":"fill","source":"openmaptiles","source-layer":"landuse",
        "paint":{"fill-color":"${landuse}"}},
       {"id":"park","type":"fill","source":"openmaptiles","source-layer":"park",
        "paint":{"fill-color":"${park}"}},
       {"id":"water","type":"fill","source":"openmaptiles","source-layer":"water",
        "paint":{"fill-color":"${water}"}},
       {"id":"waterway","type":"line","source":"openmaptiles","source-layer":"waterway",
        "paint":{"line-color":"${water}","line-width":1.0}},
       {"id":"building","type":"fill-extrusion","source":"openmaptiles","source-layer":"building","minzoom":14,
        "paint":{"fill-extrusion-color":"${building}",
         "fill-extrusion-height":["coalesce",["get","render_height"],8],
         "fill-extrusion-base":["coalesce",["get","render_min_height"],0],
         "fill-extrusion-opacity":0.85}},
       {"id":"road-minor","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["in","class","tertiary","minor","service","residential"],
         "paint":{"line-color":"${roadMinor}","line-width":["interpolate",["linear"],["zoom"],12,0.6,14,2.4]}},
       {"id":"road-major","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["in","class","motorway","trunk","primary","secondary"],
         "paint":{"line-color":"${roadMajor}","line-width":["interpolate",["linear"],["zoom"],8,1.2,14,5.0]}},
       {"id":"rail","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["==","class","rail"],
         "paint":{"line-color":"${rail}","line-width":1.0}},
       {"id":"boundary","type":"line","source":"openmaptiles","source-layer":"boundary",
         "paint":{"line-color":"${boundary}","line-width":1.0}},
       {"id":"water-name","type":"symbol","source":"openmaptiles","source-layer":"water_name",
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name"]],
          "text-font":["TrailmarkSans"],"text-size":12,"text-max-width":8},
         "paint":{"text-color":"${waterName}","text-halo-color":"${halo}","text-halo-width":1.0}},
       {"id":"place-name","type":"symbol","source":"openmaptiles","source-layer":"place",
        "filter":["in","class","city","town"],
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name:fr"],["get","name"]],
          "text-font":["TrailmarkSans"],
          "text-size":["interpolate",["linear"],["zoom"],6,11,12,15],"text-max-width":8},
         "paint":{"text-color":"${label}","text-halo-color":"${halo}","text-halo-width":1.4}},
       {"id":"road-name","type":"symbol","source":"openmaptiles","source-layer":"transportation_name",
        "minzoom":13,
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name"]],
          "text-font":["TrailmarkSans"],"text-size":10,
          "symbol-placement":"line","text-rotation-alignment":"map"},
         "paint":{"text-color":"${labelDim}","text-halo-color":"${halo}","text-halo-width":1.0}}
     ]}
    """.trimIndent()
}

private val EMPTY: FeatureCollection = FeatureCollection.fromFeatures(emptyArray())

private fun Location.toStoredPoint(): StoredPoint = StoredPoint(
    lat = latitude,
    lon = longitude,
    timeNanos = elapsedRealtimeNanos,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else 5.0,
    type = WaypointType.NORMAL,
    bearingDeg = if (hasBearing()) bearing.toDouble() else null,
)

private fun accuracyPolygon(lat: Double, lon: Double, meters: Double): Feature {
    val pts = (0 until 32).map { i ->
        val a = Math.toRadians(i * 360.0 / 32.0)
        val dLat = meters * cos(a) / 111320.0
        val dLon = meters * sin(a) / (111320.0 * maxOf(0.2, cos(Math.toRadians(lat))))
        Point.fromLngLat(lon + dLon, lat + dLat)
    }
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(pts + pts.first())))
}

private const val ICON_BIKE =
    "M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 " +
        "5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 " +
        "3.5-3.5 3.5zm5.8-10l2.4-2.4.8.8c1.3 1.3 3 2.1 5.1 2.1V9c-1.5 0-2.7-.6-3.6-1.5l-1.9-1.9" +
        "c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 1.4 0 .6.2 1.1.6 1.4L11 14v5h2" +
        "v-6.2l-2.2-2.3zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3" +
        ".5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5z"

private const val ICON_RUN =
    "M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9l1-4.4 2.1 2v6h2v-7.5" +
        "l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1" +
        "-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z"

private const val ICON_WALK =
    "M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2" +
        "v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1." +
        "7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7"

private const val ICON_HIKE =
    "M13.5,5.5c1.1,0,2-0.9,2-2s-0.9-2-2-2s-2,0.9-2,2S12.4,5.5,13.5,5.5z M17.5,10.78c-1.23-0.37" +
        "-2.22-1.17-2.8-2.18l-1-1.6 c-0.41-0.65-1.11-1-1.84-1c-0.78,0-1.59,0.5-1.78,1.44S7,23," +
        "7,23h2.1l1.8-8l2.1,2v6h2v-7.5l-2.1-2l0.6-3c1,1.15,2.41,2.01,4,2.34V23 H19V9h-1.5L17.5," +
        "10.78z M7.43,13.13l-2.12-0.41c-0.54-0.11-0.9-0.63-0.79-1.17l0.76-3.93c0.21-1.08,1.26-" +
        "1.79,2.34-1.58l1.16,0.23 L7.43,13.13z"

private fun glyphPath(sport: Sport): Path = PathParser.createPathFromPathData(
    when (sport) {
        Sport.BIKE -> ICON_BIKE
        Sport.RUN -> ICON_RUN
        Sport.WALK -> ICON_WALK
        Sport.HIKE -> ICON_HIKE
    },
)

private fun drawSportGlyph(c: Canvas, sport: Sport, s: Float, fill: Int, halo: Int?) {
    val path = glyphPath(sport)
    val scale = (s * 0.60f) / 24f
    c.save()
    c.translate(s / 2f - 12f * scale, s / 2f - 12f * scale)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (halo != null) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.7f
        p.strokeJoin = Paint.Join.ROUND
        p.color = halo
        c.drawPath(path, p)
    }
    p.style = Paint.Style.FILL
    p.color = fill
    c.drawPath(path, p)
    c.restore()
}

/** Position marker per sport — Material silhouette on a dark disc with an accent ring. */
private fun markerBitmap(context: Context, sport: Sport): Bitmap {
    val s = (36 * context.resources.displayMetrics.density).toInt().coerceAtLeast(80).toFloat()
    val bmp = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xE60E1113.toInt()
    c.drawCircle(s / 2f, s / 2f, s / 2f - 2f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = s * 0.04f
    p.color = 0xFFC6FF3D.toInt()
    c.drawCircle(s / 2f, s / 2f, s / 2f - 2f - p.strokeWidth / 2f, p)
    drawSportGlyph(c, sport, s, 0xFFC6FF3D.toInt(), null)
    return bmp
}

/** Ghost: the same silhouette, soft and translucent, with a dark halo so it reads on any map. */
private fun ghostBitmap(context: Context, sport: Sport): Bitmap {
    val s = (30 * context.resources.displayMetrics.density).toInt().coerceAtLeast(72).toFloat()
    val bmp = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
    drawSportGlyph(Canvas(bmp), sport, s, 0xBFE8EEEA.toInt(), 0x730E1113.toInt())
    return bmp
}

@Composable
fun TrailmarkMap(
    points: List<StoredPoint>,
    modifier: Modifier = Modifier,
    packPath: String? = null,
    current: StoredPoint? = null,
    attributionBottom: Int = 0,
    sport: Sport = Sport.RUN,
    ghostRoute: List<StoredPoint> = emptyList(),
    ghostCurrent: StoredPoint? = null,
    onNeedPermission: () -> Unit = {},
    dark: Boolean = true,
) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val route = remember(points) { points.filter { it.type != WaypointType.REJECTED } }
    val ghost = remember(ghostRoute) { ghostRoute.filter { it.type != WaypointType.REJECTED } }
    val glyphsBase = remember { GlyphStore.ensure(context).absolutePath }
    val online = remember { isOnline(context) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var worldShown by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    var lastBearing by remember { mutableStateOf(0f) }
    var userFix by remember { mutableStateOf<StoredPoint?>(null) }
    var pendingLocate by remember { mutableStateOf(false) }
    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    fun locationGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(attributionBottom) {
        mapView.getMapAsync { map ->
            val d = context.resources.displayMetrics.density
            val ui = map.uiSettings
            ui.setAttributionMargins(
                (92 * d).toInt(),
                (4 * d).toInt(),
                (92 * d).toInt(),
                attributionBottom + (8 * d).toInt(),
            )
            ui.isCompassEnabled = true
            ui.setCompassGravity(Gravity.TOP or Gravity.END)
            ui.setCompassMargins(0, (128 * d).toInt(), (12 * d).toInt(), 0)
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == 1) {
                    following = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        var listener: LocationListener? = null
        if (lm != null && locationGranted()) {
            val last = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) userFix = last.toStoredPoint()
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    userFix = location.toStoredPoint()
                }
            }
            listener = l
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, l) }
            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, l) }
        }
        onDispose {
            val l = listener
            if (l != null && lm != null) runCatching { lm.removeUpdates(l) }
        }
    }

    LaunchedEffect(userFix, pendingLocate) {
        val fix = userFix
        if (pendingLocate && fix != null) {
            pendingLocate = false
            mapView.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(fix.lat, fix.lon))
                    .zoom(16.0)
                    .bearing(fix.bearingDeg ?: 0.0)
                    .tilt(45.0)
                    .build()
                fitted = true
            }
        }
    }

    LaunchedEffect(packPath, dark) {
        mapView.getMapAsync { map ->
            val builder = when {
                packPath != null && !online ->
                    Style.Builder().fromJson(vectorStyle(packPath, glyphsBase, dark))
                else -> Style.Builder().fromUri(if (dark) ONLINE_STYLE_URL else ONLINE_STYLE_URL_LIGHT)
            }
            map.setStyle(builder) { s ->
                if (online && s.getLayer("3d-buildings") == null) {
                    val b3d = FillExtrusionLayer("3d-buildings", "openmaptiles")
                        .withProperties(
                            fillExtrusionColor(if (dark) "#1E2429" else "#D8DBD6"),
                            fillExtrusionHeight(
                                Expression.coalesce(Expression.get("render_height"), Expression.literal(8f)),
                            ),
                            fillExtrusionBase(
                                Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0f)),
                            ),
                            fillExtrusionOpacity(0.85f),
                        )
                        .apply { minZoom = 14f }
                    val below = s.layers.firstOrNull { it is SymbolLayer }?.id
                    if (below != null) s.addLayerBelow(b3d, below) else s.addLayer(b3d)
                }
                s.addSource(GeoJsonSource("route-source", EMPTY))
                s.addLayer(
                    LineLayer("route-layer", "route-source").withProperties(
                        lineColor("#C6FF3D"),
                        lineWidth(5f),
                    ),
                )
                s.addSource(GeoJsonSource("current-source", EMPTY))
                Sport.entries.forEach { sp -> s.addImage("me-" + sp.name, markerBitmap(context, sp)) }
                Sport.entries.forEach { sp -> s.addImage("me-ghost-" + sp.name, ghostBitmap(context, sp)) }
                s.addLayer(
                    SymbolLayer("current-layer", "current-source").withProperties(
                        iconImage("me-" + sport.name),
                        iconSize(1f),
                        iconRotate(lastBearing),
                        iconRotationAlignment("map"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
                )
                s.addSource(GeoJsonSource("user-accuracy-source", EMPTY))
                s.addLayer(
                    FillLayer("user-accuracy-layer", "user-accuracy-source").withProperties(
                        fillColor("#C6FF3D"),
                        fillOpacity(0.12f),
                    ),
                )
                s.addSource(GeoJsonSource("user-source", EMPTY))
                s.addLayer(
                    CircleLayer("user-layer", "user-source").withProperties(
                        circleColor("#C6FF3D"),
                        circleRadius(8f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(3f),
                    ),
                )
                s.addSource(GeoJsonSource("ghost-source", EMPTY))
                s.addLayer(
                    LineLayer("ghost-line", "ghost-source").withProperties(
                        lineColor("#8A93A6"),
                        lineWidth(4f),
                        lineOpacity(0.55f),
                    ),
                )
                s.addSource(GeoJsonSource("ghost-current-source", EMPTY))
                s.addLayer(
                    SymbolLayer("ghost-current-layer", "ghost-current-source").withProperties(
                        iconImage("me-ghost-" + sport.name),
                        iconSize(1f),
                        iconRotate(0f),
                        iconRotationAlignment("map"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
                )
                style = s
            }
        }
    }

    LaunchedEffect(sport) {
        val img = "me-" + sport.name
        val ghostImg = "me-ghost-" + sport.name
        style?.getLayerAs<SymbolLayer>("current-layer")?.setProperties(iconImage(img))
        style?.getLayerAs<SymbolLayer>("ghost-current-layer")?.setProperties(iconImage(ghostImg))
    }

    LaunchedEffect(route, current, style, ghost, ghostCurrent, userFix) {
        val s = style ?: return@LaunchedEffect
        val routeFc = if (route.size >= 2) {
            FeatureCollection.fromFeatures(
                arrayOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) }),
                    ),
                ),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson(routeFc)
        val curFc = current?.let {
            FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))),
            )
        } ?: EMPTY
        s.getSourceAs<GeoJsonSource>("current-source")?.setGeoJson(curFc)
        current?.bearingDeg?.let { lastBearing = it.toFloat() }
        s.getLayerAs<SymbolLayer>("current-layer")?.setProperties(iconRotate(lastBearing))
        val ghostFc = if (ghost.size >= 2) {
            FeatureCollection.fromFeatures(
                arrayOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(ghost.map { Point.fromLngLat(it.lon, it.lat) }),
                    ),
                ),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("ghost-source")?.setGeoJson(ghostFc)
        val ghostCurFc = ghostCurrent?.let {
            FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))),
            )
        } ?: EMPTY
        s.getSourceAs<GeoJsonSource>("ghost-current-source")?.setGeoJson(ghostCurFc)
        s.getLayerAs<SymbolLayer>("ghost-current-layer")?.setProperties(
            iconRotate(ghostCurrent?.bearingDeg?.toFloat() ?: 0f),
        )
        val fix = userFix
        val userFc = if (current == null && fix != null) {
            FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(Point.fromLngLat(fix.lon, fix.lat))),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("user-source")?.setGeoJson(userFc)
        val accFc = if (current == null && fix != null) {
            FeatureCollection.fromFeatures(
                arrayOf(accuracyPolygon(fix.lat, fix.lon, fix.accuracyM.coerceIn(5.0, 400.0))),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("user-accuracy-source")?.setGeoJson(accFc)
    }

    LaunchedEffect(route.size, current, following, userFix) {
        mapView.getMapAsync { map ->
            val c = current ?: run {
                if (!worldShown) {
                    val fix = userFix
                    map.cameraPosition = if (fix != null) {
                        CameraPosition.Builder().target(LatLng(fix.lat, fix.lon)).zoom(13.5).build()
                    } else {
                        CameraPosition.Builder().target(LatLng(20.0, 10.0)).zoom(1.6).build()
                    }
                    worldShown = true
                }
                return@getMapAsync
            }
            if (!fitted && route.size >= 2) {
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(route.first().lat, route.first().lon))
                    .include(LatLng(route.last().lat, route.last().lon))
                    .build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                fitted = true
                return@getMapAsync
            }
            if (following) {
                val b = c.bearingDeg?.toDouble() ?: map.cameraPosition.bearing
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(c.lat, c.lon))
                    .zoom(if (fitted) map.cameraPosition.zoom else 16.0)
                    .bearing(b)
                    .tilt(45.0)
                    .build()
            }
        }
    }

    val bottomPad = with(LocalDensity.current) { attributionBottom.toDp() } + 12.dp
    Box(modifier = modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = {
                    following = true
                    val c = current
                    val fix = userFix
                    when {
                        c != null -> mapView.getMapAsync { map ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(c.lat, c.lon))
                                .zoom(16.0)
                                .bearing(c.bearingDeg ?: 0.0)
                                .tilt(45.0)
                                .build()
                            fitted = true
                        }
                        !locationGranted() -> onNeedPermission()
                        fix != null -> mapView.getMapAsync { map ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(fix.lat, fix.lon))
                                .zoom(16.0)
                                .bearing(fix.bearingDeg ?: 0.0)
                                .tilt(45.0)
                                .build()
                            fitted = true
                        }
                        else -> pendingLocate = true
                    }
                },
                color = Palette.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = t.mapRecenter,
                        tint = if (following) Palette.accent else Palette.textSecondary,
                    )
                }
            }
            Surface(
                onClick = { mapView.getMapAsync { map -> map.animateCamera(CameraUpdateFactory.zoomIn()) } },
                color = Palette.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "+", tint = Palette.text)
                }
            }
            Surface(
                onClick = { mapView.getMapAsync { map -> map.animateCamera(CameraUpdateFactory.zoomOut()) } },
                color = Palette.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("-", color = Palette.text, fontSize = 24.sp)
                }
            }
        }
    }
}

