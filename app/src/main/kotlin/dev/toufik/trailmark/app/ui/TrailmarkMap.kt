package dev.toufik.trailmark.app.ui

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.toufik.trailmark.app.LocalLang
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL_LIGHT
import dev.toufik.trailmark.core.geo.RouteChunk
import dev.toufik.trailmark.core.geo.douglasPeucker
import dev.toufik.trailmark.core.geo.splitRuns
import dev.toufik.trailmark.core.geo.haversineMeters
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
import org.maplibre.android.style.layers.Property
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
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** `["coalesce",["get","name:xx"],["get","name"]]` — labels follow the app language. */
private fun nameField(lang: String): String =
    """["coalesce",["get","name:$lang"],["get","name"]]"""

/** A richer OpenMapTiles style with SDF labels localized to [lang], falling back to the local name. */
private fun vectorStyle(packPath: String, glyphsBase: String, dark: Boolean, lang: String): String {
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
         "layout":{"text-field":${nameField(lang)},
           "text-font":["TrailmarkSans"],"text-size":12,"text-max-width":8},
          "paint":{"text-color":"${waterName}","text-halo-color":"${halo}","text-halo-width":1.0}},
        {"id":"place-name","type":"symbol","source":"openmaptiles","source-layer":"place",
         "filter":["in","class","city","town"],
         "layout":{"text-field":${nameField(lang)},
           "text-font":["TrailmarkSans"],
           "text-size":["interpolate",["linear"],["zoom"],6,11,12,15],"text-max-width":8},
          "paint":{"text-color":"${label}","text-halo-color":"${halo}","text-halo-width":1.4}},
        {"id":"road-name","type":"symbol","source":"openmaptiles","source-layer":"transportation_name",
         "minzoom":13,
         "layout":{"text-field":${nameField(lang)},
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

/** `["step",["get","frame"],"me-RUN-0",1,"me-RUN-1",…]` — picks the cycle frame image. */
private fun frameIconExpr(sport: Sport): Expression {
    val sb = StringBuilder()
    sb.append("""["step",["get","frame"],"${spriteImageId(sport, 0)}"""")
    for (f in 1 until SPRITE_FRAMES) sb.append(""",$f,"${spriteImageId(sport, f)}"""")
    sb.append("]")
    return Expression.raw(sb.toString())
}

/** A media capture pinned to the map; idx points back into the caller's media list. */
data class MediaMark(val idx: Int, val lat: Double?, val lon: Double?, val kind: String)

@Composable
fun TrailmarkMap(
    points: List<StoredPoint>,
    modifier: Modifier = Modifier,
    packPath: String? = null,
    current: StoredPoint? = null,
    attributionBottom: Int = 0,
    controlsBottomInset: Dp = 0.dp,
    sport: Sport = Sport.RUN,
    ghostRoute: List<StoredPoint> = emptyList(),
    ghostCurrent: StoredPoint? = null,
    speedMps: Double? = null,
    planChunks: List<RouteChunk> = emptyList(),
    planDest: Pair<Double, Double>? = null,
    planStops: List<Pair<Double, Double>> = emptyList(),
    mediaMarks: List<MediaMark> = emptyList(),
    onMediaTap: ((Int) -> Unit)? = null,
    onMapClick: ((Double, Double) -> Unit)? = null,
    onUserFix: ((StoredPoint) -> Unit)? = null,
    onNeedPermission: () -> Unit = {},
    dark: Boolean = true,
) {
    val context = LocalContext.current
    val t = LocalTexts.current
    val lang = LocalLang.current
    val prefs = remember { context.getSharedPreferences("trailmark", Context.MODE_PRIVATE) }
    var pois by remember { mutableStateOf(prefs.getBoolean("map_pois", false)) }
    val route = remember(points) { points.filter { it.type != WaypointType.REJECTED } }
    val ghost = remember(ghostRoute) { ghostRoute.filter { it.type != WaypointType.REJECTED } }
    val glyphsBase = remember { GlyphStore.ensure(context).absolutePath }
    val online = remember { isOnline(context) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var worldShown by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    var lastBearing by remember { mutableStateOf(0f) }
    var smoothBearing by remember { mutableStateOf(0f) }
    var tiltDeg by remember { mutableStateOf(0.0) }
    var userFix by remember { mutableStateOf<StoredPoint?>(null) }
    var pendingLocate by remember { mutableStateOf(false) }
    var frameIndex by remember { mutableStateOf(0) }
    val onMapClickRef = rememberUpdatedState(onMapClick)
    val onUserFixRef = rememberUpdatedState(onUserFix)
    val onMediaTapRef = rememberUpdatedState(onMediaTap)
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
            map.addOnMapClickListener { latLng ->
                val mk = onMediaTapRef.value
                if (mk != null) {
                    val pt = map.projection.toScreenLocation(latLng)
                    val hit = map.queryRenderedFeatures(pt, "media-dots").firstOrNull()
                    val idx = hit?.getNumberProperty("idx")?.toInt()
                    if (idx != null) {
                        mk(idx)
                        return@addOnMapClickListener true
                    }
                }
                val cb = onMapClickRef.value
                if (cb != null) {
                    cb(latLng.latitude, latLng.longitude)
                    true
                } else {
                    false
                }
            }
        }
    }

    LaunchedEffect(current != null, speedMps) {
        if (current == null) {
            frameIndex = 0
            return@LaunchedEffect
        }
        while (true) {
            val kmh = (speedMps ?: 0.0) * 3.6
            val ms = if (kmh < 1.0) 400L else (1100.0 / kmh).toLong().coerceIn(80L, 170L)
            delay(ms)
            if (kmh >= 1.0) frameIndex = (frameIndex + 1) % SPRITE_FRAMES
            val size = 0.90f + (kmh.coerceIn(0.0, 30.0) / 30.0 * 0.18).toFloat()
            style?.getLayerAs<SymbolLayer>("current-layer")?.setProperties(iconSize(size))
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
            if (last != null) {
                val sp = last.toStoredPoint()
                userFix = sp
                onUserFixRef.value?.invoke(sp)
            }
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val sp = location.toStoredPoint()
                    userFix = sp
                    onUserFixRef.value?.invoke(sp)
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
                smoothBearing = (fix.bearingDeg ?: 0.0).toFloat()
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(fix.lat, fix.lon))
                    .zoom(16.0)
                    .bearing(smoothBearing.toDouble())
                    .tilt(tiltDeg)
                    .build()
                fitted = true
            }
        }
    }

    LaunchedEffect(packPath, dark, lang.code) {
        mapView.getMapAsync { map ->
            val builder = when {
                packPath != null && !online ->
                    Style.Builder().fromJson(vectorStyle(packPath, glyphsBase, dark, lang.code))
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
                    b3d.withSourceLayer("building")
                    val below = s.layers.firstOrNull { it is SymbolLayer }?.id
                    if (below != null) s.addLayerBelow(b3d, below) else s.addLayer(b3d)
                }
                val offlineVector = packPath != null && !online
                val poiFilter = Expression.raw(
                    """["in","class","shop","restaurant","cafe","fast_food","bakery","bar","bank","pharmacy","hospital","fuel","supermarket","grocery","convenience","marketplace","hotel","station","bus","railway","transit","attraction","museum","theatre","cinema"]""",
                )
                val poiVis = visibility(if (pois) Property.VISIBLE else Property.NONE)
                val poiDot = CircleLayer("poi-dot", "openmaptiles").withProperties(
                    circleColor(if (dark) "#AEB6BF" else "#8A929B"),
                    circleRadius(3.5f),
                    circleStrokeColor(if (dark) "#0E1113" else "#FFFFFF"),
                    circleStrokeWidth(1.2f),
                    poiVis,
                )
                poiDot.withSourceLayer("poi")
                poiDot.setFilter(poiFilter)
                poiDot.minZoom = 14f
                s.addLayer(poiDot)
                val poiLabel = SymbolLayer("poi-label", "openmaptiles").withProperties(
                    textField(
                        Expression.coalesce(
                            Expression.get("name:" + lang.code),
                            Expression.get("name"),
                        ),
                    ),
                    textFont(arrayOf(if (offlineVector) "TrailmarkSans" else "Noto Sans Regular")),
                    textSize(10f),
                    textMaxWidth(8f),
                    textColor(if (dark) "#C9D2CC" else "#3F464E"),
                    textHaloColor(if (dark) "#0E1113" else "#FFFFFF"),
                    textHaloWidth(1.0f),
                    poiVis,
                )
                poiLabel.withSourceLayer("poi")
                poiLabel.setFilter(poiFilter)
                poiLabel.minZoom = 14f
                s.addLayer(poiLabel)
                s.addSource(GeoJsonSource("plan-source", EMPTY))
                s.addLayer(
                    LineLayer("plan-casing", "plan-source").withProperties(
                        lineColor(if (dark) "#0E1113" else "#FFFFFF"),
                        lineWidth(8f),
                        lineOpacity(0.45f),
                    ),
                )
                s.addLayer(
                    LineLayer("plan-line", "plan-source").withProperties(
                        lineColor(Expression.get("color")),
                        lineWidth(5f),
                    ),
                )
                s.addSource(GeoJsonSource("plan-dest-source", EMPTY))
                s.addLayer(
                    CircleLayer("plan-dest", "plan-dest-source").withProperties(
                        circleColor("#C6FF3D"),
                        circleRadius(9f),
                        circleStrokeColor("#0E1113"),
                        circleStrokeWidth(3f),
                    ),
                )
                s.addLayer(
                    SymbolLayer("plan-stop-num", "plan-dest-source").withProperties(
                        textField(Expression.get("n")),
                        textFont(arrayOf(if (offlineVector) "TrailmarkSans" else "Noto Sans Regular")),
                        textSize(11f),
                        textColor("#0E1113"),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                    ),
                )
                s.addSource(GeoJsonSource("route-source", EMPTY))
                s.addLayer(
                    LineLayer("route-layer", "route-source").withProperties(
                        lineColor("#C6FF3D"),
                        lineWidth(5f),
                    ),
                )
                s.addSource(GeoJsonSource("current-source", EMPTY))
                Sport.entries.forEach { sp ->
                    for (f in 0 until SPRITE_FRAMES) {
                        s.addImage(spriteImageId(sp, f), spriteBitmap(context, sp, f))
                    }
                    s.addImage("me-ghost-" + sp.name, ghostSpriteBitmapFor(context, sp))
                }
                s.addLayer(
                    SymbolLayer("current-layer", "current-source").withProperties(
                        iconImage(frameIconExpr(sport)),
                        iconSize(0.92f),
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
                s.addSource(GeoJsonSource("media-source", EMPTY))
                s.addLayer(
                    CircleLayer("media-dots", "media-source").withProperties(
                        circleColor("#E5484D"),
                        circleRadius(6.5f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2.5f),
                    ),
                )
                style = s
            }
        }
    }

    LaunchedEffect(pois, style) {
        val s = style ?: return@LaunchedEffect
        val v = visibility(if (pois) Property.VISIBLE else Property.NONE)
        s.getLayerAs<CircleLayer>("poi-dot")?.setProperties(v)
        s.getLayerAs<SymbolLayer>("poi-label")?.setProperties(v)
    }

    LaunchedEffect(sport) {
        val ghostImg = "me-ghost-" + sport.name
        style?.getLayerAs<SymbolLayer>("current-layer")?.setProperties(iconImage(frameIconExpr(sport)))
        style?.getLayerAs<SymbolLayer>("ghost-current-layer")?.setProperties(iconImage(ghostImg))
    }

    LaunchedEffect(planChunks, style) {
        val s = style ?: return@LaunchedEffect
        val features = planChunks.map { chunk ->
            val f = Feature.fromGeometry(
                LineString.fromLngLats(chunk.points.map { Point.fromLngLat(it.lon, it.lat) }),
            )
            f.addStringProperty("color", gradeColorHex(chunk.gradePercent))
            f
        }
        s.getSourceAs<GeoJsonSource>("plan-source")?.setGeoJson(
            if (features.isEmpty()) EMPTY else FeatureCollection.fromFeatures(features.toTypedArray()),
        )
    }

    LaunchedEffect(planStops, planDest, style) {
        val s = style ?: return@LaunchedEffect
        val stops = if (planStops.isNotEmpty()) {
            planStops
        } else {
            planDest?.let { listOf(it) } ?: emptyList()
        }
        val features = stops.mapIndexed { i, st ->
            Feature.fromGeometry(Point.fromLngLat(st.second, st.first)).apply {
                addStringProperty("n", (i + 1).toString())
            }
        }
        s.getSourceAs<GeoJsonSource>("plan-dest-source")?.setGeoJson(
            if (features.isEmpty()) EMPTY else FeatureCollection.fromFeatures(features.toTypedArray()),
        )
    }

    LaunchedEffect(mediaMarks, style) {
        val s = style ?: return@LaunchedEffect
        val features = mediaMarks.mapNotNull { m ->
            val la = m.lat
            val lo = m.lon
            if (la == null || lo == null) {
                null
            } else {
                Feature.fromGeometry(Point.fromLngLat(lo, la)).apply {
                    addNumberProperty("idx", m.idx.toDouble())
                }
            }
        }
        s.getSourceAs<GeoJsonSource>("media-source")?.setGeoJson(
            if (features.isEmpty()) EMPTY else FeatureCollection.fromFeatures(features.toTypedArray()),
        )
    }

    val routeRuns = remember(route) {
        splitRuns(route) { it.type == WaypointType.IDLE }
            .map { douglasPeucker(it, 4.0, { it.lat }, { it.lon }) }
            .filter { it.size >= 2 }
    }
    val ghostRuns = remember(ghost) {
        splitRuns(ghost) { it.type == WaypointType.IDLE }
            .map { douglasPeucker(it, 4.0, { it.lat }, { it.lon }) }
            .filter { it.size >= 2 }
    }
    LaunchedEffect(routeRuns, ghostRuns, style) {
        val s = style ?: return@LaunchedEffect
        val routeFc = if (routeRuns.isNotEmpty()) {
            FeatureCollection.fromFeatures(
                routeRuns.map { run ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(run.map { Point.fromLngLat(it.lon, it.lat) }),
                    )
                }.toTypedArray(),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson(routeFc)
        val ghostFc = if (ghostRuns.isNotEmpty()) {
            FeatureCollection.fromFeatures(
                ghostRuns.map { run ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(run.map { Point.fromLngLat(it.lon, it.lat) }),
                    )
                }.toTypedArray(),
            )
        } else {
            EMPTY
        }
        s.getSourceAs<GeoJsonSource>("ghost-source")?.setGeoJson(ghostFc)
    }

    LaunchedEffect(current, ghostCurrent, userFix, style, frameIndex) {
        val s = style ?: return@LaunchedEffect
        val curFc = current?.let {
            val f = Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))
            f.addNumberProperty("frame", (frameIndex % SPRITE_FRAMES).toDouble())
            FeatureCollection.fromFeatures(arrayOf(f))
        } ?: EMPTY
        s.getSourceAs<GeoJsonSource>("current-source")?.setGeoJson(curFc)
        current?.bearingDeg?.let { b ->
            val target = b.toFloat()
            val delta = ((target - lastBearing + 540f) % 360f) - 180f
            lastBearing = (lastBearing + delta * 0.4f + 360f) % 360f
        }
        s.getLayerAs<SymbolLayer>("current-layer")?.setProperties(iconRotate(lastBearing))
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

    LaunchedEffect(route.size, current, following, userFix, tiltDeg) {
        mapView.getMapAsync { map ->
            val c = current ?: run {
                if (!worldShown) {
                    val fix = userFix
                    map.cameraPosition = if (fix != null) {
                        CameraPosition.Builder().target(LatLng(fix.lat, fix.lon)).zoom(14.0).tilt(tiltDeg).build()
                    } else {
                        CameraPosition.Builder().target(LatLng(20.0, 10.0)).zoom(1.6).build()
                    }
                    worldShown = true
                }
                return@getMapAsync
            }
            if (!fitted && route.size >= 2) {
                val first = route.first()
                val last = route.last()
                if (haversineMeters(first.lat, first.lon, last.lat, last.lon) < 150.0) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(first.lat, first.lon))
                        .zoom(16.0)
                        .tilt(tiltDeg)
                        .build()
                } else {
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(first.lat, first.lon))
                        .include(LatLng(last.lat, last.lon))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
                fitted = true
                return@getMapAsync
            }
            if (following) {
                val target = c.bearingDeg?.toFloat() ?: smoothBearing
                val delta = ((target - smoothBearing + 540f) % 360f) - 180f
                smoothBearing = (smoothBearing + delta * 0.3f + 360f) % 360f
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(c.lat, c.lon))
                    .zoom(if (fitted) map.cameraPosition.zoom else 16.0)
                    .bearing(smoothBearing.toDouble())
                    .tilt(tiltDeg)
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
                .padding(end = 12.dp, bottom = bottomPad + controlsBottomInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = {
                    following = true
                    val c = current
                    val fix = userFix
                    when {
                        c != null -> mapView.getMapAsync { map ->
                            smoothBearing = (c.bearingDeg ?: 0.0).toFloat()
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(c.lat, c.lon))
                                .zoom(16.0)
                                .bearing(smoothBearing.toDouble())
                                .tilt(tiltDeg)
                                .build()
                            fitted = true
                        }
                        !locationGranted() -> onNeedPermission()
                        fix != null -> mapView.getMapAsync { map ->
                            smoothBearing = (fix.bearingDeg ?: 0.0).toFloat()
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(fix.lat, fix.lon))
                                .zoom(16.0)
                                .bearing(smoothBearing.toDouble())
                                .tilt(tiltDeg)
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
            Surface(
                onClick = {
                    val next = if (tiltDeg > 1.0) 0.0 else 45.0
                    tiltDeg = next
                    mapView.getMapAsync { map ->
                        map.animateCamera(CameraUpdateFactory.tiltTo(next))
                    }
                },
                color = Palette.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tiltDeg > 1.0) "2D" else "3D",
                        color = if (tiltDeg > 1.0) Palette.text else Palette.accent,
                        fontSize = 13.sp,
                    )
                }
            }
            Surface(
                onClick = {
                    pois = !pois
                    prefs.edit().putBoolean("map_pois", pois).apply()
                },
                color = Palette.surface.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "POI",
                        color = if (pois) Palette.accent else Palette.text,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

