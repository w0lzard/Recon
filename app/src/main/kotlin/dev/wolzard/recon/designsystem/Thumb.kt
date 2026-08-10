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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** D26: loads a link thumbnail (ThumbnailLoader in the app; null in previews and tests = always the fallback). */
val LocalThumbnails = staticCompositionLocalOf<(suspend (String) -> ImageBitmap?)?> { null }

/** A link thumbnail, or the link glyph on a filled tile until (and unless) the image arrives. */
@Composable
fun Thumb(url: String?, width: Dp, height: Dp, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.extraSmall) {
    val load = LocalThumbnails.current
    val image by produceState<ImageBitmap?>(null, url, load) { value = url?.let { load?.invoke(it) } }
    Box(modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) Image(img, contentDescription = null, Modifier.size(width, height).testTag("thumb_image"), contentScale = ContentScale.Crop)
        else Icon(StatusIcons.Link, contentDescription = null, Modifier.size(20.dp).testTag("thumb_fallback"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
