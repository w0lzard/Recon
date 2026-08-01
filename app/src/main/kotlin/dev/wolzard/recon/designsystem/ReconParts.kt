package dev.wolzard.recon.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.designsystem.Mono
import dev.wolzard.recon.designsystem.status

/** Colour role of a [StatusChip]; Neutral is the plain hairline chip (Done, Running). */
enum class ChipKind { Neutral, Ok, Warn, Unclear, Bad }

/** Non-clickable status pill: 22dp, 1dp border, coloured ink (design 1a). */
@Composable
fun StatusChip(label: String, kind: ChipKind, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme.status
    val ink = when (kind) {
        ChipKind.Neutral -> MaterialTheme.colorScheme.onSurface
        ChipKind.Ok -> s.verified
        ChipKind.Warn -> s.unverified
        ChipKind.Unclear -> s.unclear
        ChipKind.Bad -> s.bad
    }
    val line = if (kind == ChipKind.Neutral) MaterialTheme.colorScheme.outlineVariant else ink
    Text(
        label, modifier.border(1.dp, line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        color = ink, style = MaterialTheme.typography.labelSmall,
    )
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
        drawRect(dot, Offset(size.width * .42f, size.height * .42f), Size(size.width / 6, size.height / 6))
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Reticle()
        Spacer(Modifier.width(10.dp))
        Text("Recon", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono))
    }
}

/** Static dot-grid motif, one draw pass. */
fun Modifier.dotGrid(c: Color) = drawBehind {
    val step = 16.dp.toPx()
    var y = step / 2
    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(c, 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
}
