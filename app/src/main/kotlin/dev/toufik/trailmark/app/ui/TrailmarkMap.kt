package dev.toufik.trailmark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.toufik.trailmark.app.maps.GlyphStore
import dev.toufik.trailmark.app.maps.ONLINE_STYLE_URL
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
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
@Composable
fun TrailmarkMap(
    points: List<StoredPoint>,
    modifier: Modifier = Modifier,
    packPath: String? = null,
    current: StoredPoint? = null,
    attributionBottom: Int = 0,
) {
    val context = LocalContext.current
    val route = remember(points) { points.filter { it.type != WaypointType.REJECTED } }
    val glyphsBase = remember { GlyphStore.ensure(context).absolutePath }
    val online = remember { isOnline(context) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var worldShown by remember { mutableStateOf(false) }

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
            map.uiSettings.setAttributionMargins(
                (92 * d).toInt(),
                (4 * d).toInt(),
                (92 * d).toInt(),
                attributionBottom + (8 * d).toInt(),
            )
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
                s.addLayer(
                    CircleLayer("current-layer", "current-source").withProperties(
                        circleColor("#C6FF3D"),
                        circleRadius(7f),
                        circleStrokeColor("#0E1113"),
                        circleStrokeWidth(3f),
                    ),
                )
                style = s
            }
        }
    }

    LaunchedEffect(route, current, style) {
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
    }

    LaunchedEffect(route.size, current) {
        mapView.getMapAsync { map ->
            val c = current
            if (c == null) {
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
            }
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(c.lat, c.lon))
                .zoom(if (fitted) map.cameraPosition.zoom else 16.0)
                .build()
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })
}

