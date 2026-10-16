package dev.toufik.trailmark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val FLAT_STYLE =
    """{"version":8,"name":"trailmark-dark","sources":{},"layers":[
       {"id":"background","type":"background","paint":{"background-color":"#0E1113"}}]}"""

/** A richer OpenMapTiles style (no glyphs, so labels are omitted for now). */
private fun vectorStyle(packPath: String): String = """
    {"version":8,"name":"trailmark-omt",
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
        "paint":{"line-color":"#3A444E","line-width":1.0}}
     ]}
""".trimIndent()

/**
 * A MapLibre map that draws the recorded route as a GeoJSON line. When an
 * offline MBTiles pack is installed it is used as the vector basemap; otherwise
 * the base is a flat dark background with the route over it.
 */
@Composable
fun TrailmarkMap(
    points: List<StoredPoint>,
    modifier: Modifier = Modifier,
    packPath: String? = null,
) {
    val context = LocalContext.current
    val route = remember(points) { points.filter { it.type != WaypointType.REJECTED } }

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

    LaunchedEffect(route, packPath) {
        mapView.getMapAsync { map ->
            val builder =
                if (packPath != null) Style.Builder().fromJson(vectorStyle(packPath))
                else Style.Builder().fromJson(FLAT_STYLE)
            map.setStyle(builder) { style ->
                if (style.getLayer("route-layer") != null) style.removeLayer("route-layer")
                if (style.getSource("route-source") != null) style.removeSource("route-source")

                if (route.size >= 2) {
                    val lngLats = route.map { Point.fromLngLat(it.lon, it.lat) }
                    val feature = Feature.fromGeometry(LineString.fromLngLats(lngLats))
                    style.addSource(
                        GeoJsonSource("route-source", FeatureCollection.fromFeatures(arrayOf(feature))),
                    )
                    style.addLayer(
                        LineLayer("route-layer", "route-source").withProperties(
                            lineColor("#C6FF3D"),
                            lineWidth(5f),
                        ),
                    )
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(route.first().lat, route.first().lon))
                        .include(LatLng(route.last().lat, route.last().lon))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                } else if (route.size == 1) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(route[0].lat, route[0].lon))
                        .zoom(15.0)
                        .build()
                }
            }
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
