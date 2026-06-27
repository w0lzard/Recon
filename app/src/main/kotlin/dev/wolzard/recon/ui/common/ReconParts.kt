package dev.wolzard.recon.ui.common

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
import dev.wolzard.recon.ui.theme.Mono
import dev.wolzard.recon.ui.theme.status

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
