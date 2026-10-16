package dev.wolzard.recon.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** D26: loads a link thumbnail (ThumbnailLoader in the app; null in previews and tests = always the fallback). */
val LocalThumbnails = staticCompositionLocalOf<(suspend (String) -> ImageBitmap?)?> { null }

/**
 * A link thumbnail. Until (and unless) the image arrives: [fallback] on a filled tile, or with [fallback] null the
 * design's diagonal-striped video placeholder (Recon UI 1d).
 */
@Composable
fun Thumb(url: String?, width: Dp, height: Dp, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.extraSmall, fallback: ImageVector? = StatusIcons.Link) {
    val load = LocalThumbnails.current
    val image by produceState<ImageBitmap?>(null, url, load) { value = url?.let { load?.invoke(it) } }
    Box(modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape), contentAlignment = Alignment.Center) {
        val img = image
        when {
            img != null -> Image(img, contentDescription = null, Modifier.size(width, height).testTag("thumb_image"), contentScale = ContentScale.Crop)
            fallback != null -> Icon(fallback, contentDescription = null, Modifier.size(20.dp).testTag("thumb_fallback"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Box(Modifier.size(width, height).stripes(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("thumb_fallback"))
        }
    }
}

/** 45° stripes over the tile: lighter on the dark theme, white on the light one, as in the design. */
private fun Modifier.stripes(tile: Color) = drawBehind {
    val ink = if (tile.luminance() > 0.5f) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.035f)
    val step = 12.dp.toPx(); val w = 6.dp.toPx()
    var x = -size.height
    while (x < size.width) { drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), w); x += step }
}
