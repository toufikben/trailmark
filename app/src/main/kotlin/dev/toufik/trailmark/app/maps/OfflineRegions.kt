package dev.toufik.trailmark.app.maps

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A downloaded offline area on disk. */
data class SavedRegion(val id: Long, val name: String, val tiles: Long, val bytes: Long)

/**
 * On-device offline areas for the global basemap: the user downloads any place
 * on Earth from the same tile source the online map uses, so nothing needs to
 * be pre-built per country.
 */
class OfflineRegions(private val context: Context) {

    private val manager: OfflineManager
        get() = OfflineManager.getInstance(context).also {
            it.setOfflineMapboxTileCountLimit(2_000_000)
        }

    suspend fun list(): List<SavedRegion> = suspendCancellableCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                if (regions.isNullOrEmpty()) {
                    cont.resume(emptyList())
                    return
                }
                val out = ArrayList<SavedRegion>(regions.size)
                var remaining = regions.size
                regions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            out.add(
                                SavedRegion(
                                    region.id,
                                    nameOf(region),
                                    status?.completedResourceCount ?: 0L,
                                    status?.completedResourceSize ?: 0L,
                                ),
                            )
                            remaining -= 1
                            if (remaining == 0) cont.resume(out)
                        }

                        override fun onError(error: String?) {
                            out.add(SavedRegion(region.id, nameOf(region), 0, 0))
                            remaining -= 1
                            if (remaining == 0) cont.resume(out)
                        }
                    })
                }
            }

            override fun onError(error: String) {
                cont.resume(emptyList())
            }
        })
    }
    suspend fun download(
        name: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        onProgress: (Int) -> Unit,
    ): Unit = suspendCancellableCoroutine { cont ->
        val definition = OfflineTilePyramidRegionDefinition(
            ONLINE_STYLE_URL,
            bounds,
            minZoom,
            maxZoom,
            context.resources.displayMetrics.density,
        )
        val metadata = JSONObject().put("name", name).toString().toByteArray()
        manager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val pct = if (status.requiredResourceCount > 0) {
                                (100L * status.completedResourceCount / status.requiredResourceCount).toInt()
                            } else {
                                0
                            }
                            onProgress(pct)
                            if (status.isComplete && cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(error: OfflineRegionError) {
                            if (cont.isActive) {
                                cont.resumeWithException(IllegalStateException(error.message))
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IllegalStateException("tile limit exceeded: $limit"),
                                )
                            }
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                }
            },
        )
    }
    suspend fun delete(id: Long): Unit = suspendCancellableCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val region = regions?.firstOrNull { it.id == id }
                if (region == null) {
                    cont.resume(Unit)
                    return
                }
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(error: String) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                    }
                })
            }

            override fun onError(error: String) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
            }
        })
    }

    private fun nameOf(region: OfflineRegion): String = runCatching {
        JSONObject(region.metadata.decodeToString()).optString("name", "?")
    }.getOrDefault("?")
}
