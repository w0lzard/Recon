package dev.wolzard.recon.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Recon reticle frame: four corner ticks, 1.5 dp stroke (design link card, TARGET and result header thumbs). */
fun Modifier.cornerTicks(c: Color, length: Dp = 10.dp) = drawBehind {
    val t = length.toPx(); val s = 1.5.dp.toPx(); val h = s / 2
    val (w, ht) = size.width to size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * t, y), s)
        drawLine(c, Offset(x, y), Offset(x, y + dy * t), s)
    }
    corner(h, h, 1f, 1f); corner(w - h, h, -1f, 1f); corner(h, ht - h, 1f, -1f); corner(w - h, ht - h, -1f, -1f)
}
