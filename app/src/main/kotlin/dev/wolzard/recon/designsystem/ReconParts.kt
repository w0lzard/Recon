package dev.wolzard.recon.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wolzard.recon.designsystem.Mono
import dev.wolzard.recon.designsystem.status

/** Colour role of a [StatusChip]; Neutral is the plain hairline chip (Done, Running). */
enum class ChipKind { Neutral, Ok, Warn, Unclear, Bad }

/**
 * Non-clickable status pill: 22dp, 1dp border, coloured ink, uppercase mono label (design 1a). Non-neutral chips carry an icon next to
 * the label ([icon] overrides the one the kind implies), so the status never rests on colour alone.
 */
@Composable
fun StatusChip(label: String, kind: ChipKind, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val s = MaterialTheme.colorScheme.status
    val ink = when (kind) {
        ChipKind.Neutral -> MaterialTheme.colorScheme.onSurface
        ChipKind.Ok -> s.verified
        ChipKind.Warn -> s.unverified
        ChipKind.Unclear -> s.unclear
        ChipKind.Bad -> s.bad
    }
    val line = if (kind == ChipKind.Neutral) MaterialTheme.colorScheme.outlineVariant else ink
    val glyph = icon ?: when (kind) {
        ChipKind.Neutral -> null
        ChipKind.Ok -> StatusIcons.Check
        ChipKind.Warn -> StatusIcons.Info
        ChipKind.Unclear -> StatusIcons.Help
        ChipKind.Bad -> StatusIcons.Cancel
    }
    Row(
        modifier.border(1.dp, line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        glyph?.let { Icon(it, contentDescription = null, Modifier.size(14.dp), tint = ink) }
        Text(label.uppercase(), color = ink, style = MaterialTheme.typography.labelSmall)
    }
}

/** Four corner ticks plus an accent dot: the Recon reticle. */
@Composable
fun Reticle(modifier: Modifier = Modifier.size(18.dp), tick: Color = MaterialTheme.colorScheme.onSurface, dot: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val t = size.width / 3; val w = 1.5.dp.toPx();
        val (x, y) = size.width - w / 2 to size.height - w / 2
        fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
            drawLine(tick, Offset(cx, cy), Offset(cx + dx, cy), w); drawLine(tick, Offset(cx, cy), Offset(cx, cy + dy), w)
        }
        corner(w / 2, w / 2, t, t); corner(x, w / 2, -t, t); corner(w / 2, y, t, -t); corner(x, y, -t, -t)
        val d = minOf(size.width / 6, 4.dp.toPx()) // design: a small signal dot, even on the 48 dp empty-state mark
        drawRect(dot, Offset((size.width - d) / 2, (size.height - d) / 2), Size(d, d))
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Reticle(Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Recon", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp))
    }
}

/** Static dot-grid motif, one draw pass. */
fun Modifier.dotGrid(c: Color) = drawBehind {
    val step = 16.dp.toPx()
    var y = step / 2
    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(c, 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
}
