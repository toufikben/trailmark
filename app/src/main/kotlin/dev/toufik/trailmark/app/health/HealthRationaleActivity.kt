package dev.toufik.trailmark.app.health

import android.app.Activity
import android.os.Bundle
import dev.toufik.trailmark.R

/** Permission rationale shown by Health Connect on Android 13 and below. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_rationale)
    }
}
