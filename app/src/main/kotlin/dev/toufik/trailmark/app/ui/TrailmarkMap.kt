package dev.toufik.trailmark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import dev.toufik.trailmark.app.LocalTexts
import dev.toufik.trailmark.app.Palette
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.Sport
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
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

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** A richer OpenMapTiles style with SDF labels (Arabic first, then French/English). */
private fun vectorStyle(packPath: String, glyphsBase: String): String = """
    {"version":8,"name":"trailmark-omt",
     "glyphs":"file://$glyphsBase/{fontstack}/{range}.pbf",
     "sources":{"openmaptiles":{"type":"vector","url":"pmtiles://file://$packPath"}},
     "layers":[
       {"id":"background","type":"background","paint":{"background-color":"#0E1113"}},
       {"id":"landcover","type":"fill","source":"openmaptiles","source-layer":"landcover",
        "paint":{"fill-color":"#141A16"}},
       {"id":"landuse","type":"fill","source":"openmaptiles","source-layer":"landuse",
        "paint":{"fill-color":"#161C17"}},
       {"id":"park","type":"fill","source":"openmaptiles","source-layer":"park",
        "paint":{"fill-color":"#16241A"}},
       {"id":"water","type":"fill","source":"openmaptiles","source-layer":"water",
        "paint":{"fill-color":"#12232E"}},
       {"id":"waterway","type":"line","source":"openmaptiles","source-layer":"waterway",
        "paint":{"line-color":"#12232E","line-width":1.0}},
       {"id":"building","type":"fill","source":"openmaptiles","source-layer":"building","minzoom":13,
        "paint":{"fill-color":"#1E2429"}},
       {"id":"road-minor","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["in","class","tertiary","minor","service","residential"],
        "paint":{"line-color":"#333E48","line-width":["interpolate",["linear"],["zoom"],12,0.6,14,2.4]}},
       {"id":"road-major","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["in","class","motorway","trunk","primary","secondary"],
        "paint":{"line-color":"#4A5560","line-width":["interpolate",["linear"],["zoom"],8,1.2,14,5.0]}},
       {"id":"rail","type":"line","source":"openmaptiles","source-layer":"transportation",
        "filter":["==","class","rail"],
        "paint":{"line-color":"#2A3138","line-width":1.0}},
       {"id":"boundary","type":"line","source":"openmaptiles","source-layer":"boundary",
        "paint":{"line-color":"#3A444E","line-width":1.0}},
       {"id":"water-name","type":"symbol","source":"openmaptiles","source-layer":"water_name",
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name"]],
          "text-font":["TrailmarkSans"],"text-size":12,"text-max-width":8},
        "paint":{"text-color":"#8FA3B0","text-halo-color":"#0E1113","text-halo-width":1.0}},
       {"id":"place-name","type":"symbol","source":"openmaptiles","source-layer":"place",
        "filter":["in","class","city","town"],
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name:fr"],["get","name"]],
          "text-font":["TrailmarkSans"],
          "text-size":["interpolate",["linear"],["zoom"],6,11,12,15],"text-max-width":8},
        "paint":{"text-color":"#E8EEEA","text-halo-color":"#0E1113","text-halo-width":1.4}},
       {"id":"road-name","type":"symbol","source":"openmaptiles","source-layer":"transportation_name",
        "minzoom":13,
        "layout":{"text-field":["coalesce",["get","name:ar"],["get","name"]],
          "text-font":["TrailmarkSans"],"text-size":10,
          "symbol-placement":"line","text-rotation-alignment":"map"},
        "paint":{"text-color":"#9DA4A1","text-halo-color":"#0E1113","text-halo-width":1.0}}
     ]}
""".trimIndent()

private val EMPTY: FeatureCollection = FeatureCollection.fromFeatures(emptyArray())

/** Position marker: a heading arrow, or a bicycle for rides. Accent on a dark disc. */
private fun markerBitmap(context: Context, bike: Boolean): Bitmap {
    val s = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96).toFloat()
    val bmp = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xD90E1113.toInt()
    c.drawCircle(s / 2f, s / 2f, s / 2f - 2f, p)
    p.color = 0xFFC6FF3D.toInt()
    p.strokeWidth = s * 0.055f
    p.strokeCap = Paint.Cap.ROUND
    p.strokeJoin = Paint.Join.ROUND
    if (bike) {
        p.style = Paint.Style.STROKE
        val r = s * 0.155f
        val cy = s * 0.62f
        c.drawCircle(s * 0.30f, cy, r, p)
        c.drawCircle(s * 0.70f, cy, r, p)
        c.drawLine(s * 0.30f, cy, s * 0.46f, cy - s * 0.24f, p)
        c.drawLine(s * 0.46f, cy - s * 0.24f, s * 0.64f, cy - s * 0.02f, p)
        c.drawLine(s * 0.64f, cy - s * 0.02f, s * 0.30f, cy, p)
        c.drawLine(s * 0.46f, cy - s * 0.24f, s * 0.42f, cy - s * 0.38f, p)
        c.drawLine(s * 0.36f, cy - s * 0.38f, s * 0.52f, cy - s * 0.38f, p)
    } else {
        p.style = Paint.Style.FILL
        val arrow = Path()
        arrow.moveTo(s * 0.50f, s * 0.16f)
        arrow.lineTo(s * 0.78f, s * 0.82f)
        arrow.lineTo(s * 0.50f, s * 0.66f)
        arrow.lineTo(s * 0.22f, s * 0.82f)
        arrow.close()
        c.drawPath(arrow, p)
    }
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

    LaunchedEffect(packPath) {
        mapView.getMapAsync { map ->
            val builder = when {
                packPath != null && !online ->
                    Style.Builder().fromJson(vectorStyle(packPath, glyphsBase))
                else -> Style.Builder().fromUri(ONLINE_STYLE_URL)
            }
            map.setStyle(builder) { s ->
                s.addSource(GeoJsonSource("route-source", EMPTY))
                s.addLayer(
                    LineLayer("route-layer", "route-source").withProperties(
                        lineColor("#C6FF3D"),
                        lineWidth(5f),
                    ),
                )
                s.addSource(GeoJsonSource("current-source", EMPTY))
                s.addImage("me-arrow", markerBitmap(context, false))
                s.addImage("me-bike", markerBitmap(context, true))
                s.addLayer(
                    SymbolLayer("current-layer", "current-source").withProperties(
                        iconImage(if (sport == Sport.BIKE) "me-bike" else "me-arrow"),
                        iconSize(1f),
                        iconRotate(lastBearing),
                        iconRotationAlignment("map"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
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
                    CircleLayer("ghost-current-layer", "ghost-current-source").withProperties(
                        circleColor("#AAB4C8"),
                        circleRadius(7f),
                        circleStrokeColor("#0E1113"),
                        circleStrokeWidth(3f),
                    ),
                )
                style = s
            }
        }
    }

    LaunchedEffect(sport) {
        style?.getLayerAs<SymbolLayer>("current-layer")?.setProperties(
            iconImage(if (sport == Sport.BIKE) "me-bike" else "me-arrow"),
        )
    }

    LaunchedEffect(route, current, style, ghost, ghostCurrent) {
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
    }

    LaunchedEffect(route.size, current, following) {
        mapView.getMapAsync { map ->
            val c = current ?: run {
                if (!worldShown) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(20.0, 10.0))
                        .zoom(1.6)
                        .build()
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
                    mapView.getMapAsync { map ->
                        if (c != null) {
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(c.lat, c.lon))
                                .zoom(16.0)
                                .bearing(c.bearingDeg?.toDouble() ?: 0.0)
                                .tilt(45.0)
                                .build()
                            fitted = true
                        }
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

