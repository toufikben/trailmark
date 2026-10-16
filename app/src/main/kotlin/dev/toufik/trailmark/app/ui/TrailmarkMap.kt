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

/**
 * A MapLibre map that draws the recorded route as a GeoJSON line over the
 * bundled dark style. Offline vector packs are installed separately (:regions)
 * and will be wired as the style's tile source; until then the base is the
 * flat background and the route is the content.
 */
@Composable
fun TrailmarkMap(points: List<StoredPoint>, modifier: Modifier = Modifier) {
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

    LaunchedEffect(route) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("asset://style_trailmark_dark.json")) { style ->
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
