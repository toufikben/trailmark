package dev.toufik.trailmark.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.engine.Sport

/** Compact sport selector pill with a dropdown; shared by Record and Plan screens. */
@Composable
internal fun SportPicker(
    t: Texts,
    sport: Sport,
    enabled: Boolean,
    onPick: (Sport) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menu = true },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            color = Palette.surface.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Palette.textSecondary.copy(alpha = 0.3f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = sportLabelOf(t, sport),
                    color = if (enabled) Palette.text else Palette.textSecondary,
                    fontSize = 13.sp,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = Palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Sport.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(sportLabelOf(t, s)) },
                    onClick = {
                        onPick(s)
                        menu = false
                    },
                    trailingIcon = if (s == sport) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

internal fun sportLabelOf(t: Texts, s: Sport): String = when (s) {
    Sport.WALK -> t.walk
    Sport.RUN -> t.run
    Sport.BIKE -> t.bike
    Sport.HIKE -> t.hike
}
