package dev.toufik.trailmark.app.wear

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.toufik.trailmark.app.ui.importTrackBytes

/**
 * Receives finished GPX tracks from the Wear OS app over a Wearable channel
 * and imports them into the normal activity history.
 */
class WearGpxListener : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != "/trailmark/gpx") return
        val client = Wearable.getChannelClient(this)
        client.getInputStream(channel).addOnSuccessListener { input ->
            Thread {
                runCatching { input.use { it.readBytes() } }
                    .onSuccess { bytes ->
                        if (bytes.size > 2) importTrackBytes(this, bytes)
                    }
                runCatching { client.close(channel) }
            }.start()
        }
    }
}
