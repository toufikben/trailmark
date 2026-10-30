package dev.toufik.trailmark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toufik.trailmark.app.LocalPremium
import dev.toufik.trailmark.app.Palette

private val GoldDeep = Color(0xFFE8A200)
private val OnGold = Color(0xFF241A00)

/**
 * Gold purchase call-to-action. Sits outside Settings so a plan is always one tap
 * away, and disappears as soon as a purchase is active.
 */
@Composable
fun ProCta(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val purchased = LocalPremium.current?.purchasedProduct != null
    if (purchased) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Palette.gold, GoldDeep)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("★", color = OnGold, fontSize = 12.sp)
        Text("PRO", color = OnGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
