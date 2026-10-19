package dev.toufik.trailmark.app.health

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Permission rationale shown by Health Connect on Android 13 and below. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Trailmark saves your finished workouts (type, distance and " +
            "estimated calories) to Health Connect so other health apps can read them."
        tv.setPadding(48, 96, 48, 48)
        setContentView(tv)
    }
}
