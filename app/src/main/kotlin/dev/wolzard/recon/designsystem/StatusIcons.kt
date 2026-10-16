package dev.wolzard.recon.designsystem

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/**
 * The few Material Symbols the design's status chips use, as vectors (Material path data, Apache 2.0), so the
 * extended icon library (several MB of dex before R8) isn't pulled in. Meaning is the icon plus the label, never colour alone.
 */
object StatusIcons {
    val Check: ImageVector by lazy {
        materialIcon("Check") { materialPath { moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close(); moveTo(10f, 17f); lineToRelative(-5f, -5f); lineToRelative(1.41f, -1.41f); lineTo(10f, 14.17f); lineToRelative(7.59f, -7.59f); lineTo(19f, 8f); lineToRelative(-9f, 9f); close() } }
    }
    val Info: ImageVector by lazy {
        materialIcon("Info") { materialPath { moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close(); moveTo(13f, 17f); horizontalLineToRelative(-2f); verticalLineToRelative(-6f); horizontalLineToRelative(2f); verticalLineToRelative(6f); close(); moveTo(13f, 9f); horizontalLineToRelative(-2f); verticalLineTo(7f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close() } }
    }
    val Help: ImageVector by lazy {
        materialIcon("Help") { materialPath { moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close(); moveTo(13f, 19f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close(); moveTo(15.07f, 11.25f); lineToRelative(-0.9f, 0.92f); curveTo(13.45f, 12.9f, 13f, 13.5f, 13f, 15f); horizontalLineToRelative(-2f); verticalLineToRelative(-0.5f); curveToRelative(0f, -1.1f, 0.45f, -2.1f, 1.17f, -2.83f); lineToRelative(1.24f, -1.26f); curveToRelative(0.37f, -0.36f, 0.59f, -0.86f, 0.59f, -1.41f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f); horizontalLineTo(8f); curveToRelative(0f, -2.21f, 1.79f, -4f, 4f, -4f); reflectiveCurveToRelative(4f, 1.79f, 4f, 4f); curveToRelative(0f, 0.88f, -0.36f, 1.68f, -0.93f, 2.25f); close() } }
    }
    val Cancel: ImageVector by lazy {
        materialIcon("Cancel") { materialPath { moveTo(12f, 2f); curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f); reflectiveCurveToRelative(4.47f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.47f, 10f, -10f); reflectiveCurveTo(17.53f, 2f, 12f, 2f); close(); moveTo(17f, 15.59f); lineTo(15.59f, 17f); lineTo(12f, 13.41f); lineTo(8.41f, 17f); lineTo(7f, 15.59f); lineTo(10.59f, 12f); lineTo(7f, 8.41f); lineTo(8.41f, 7f); lineTo(12f, 10.59f); lineTo(15.59f, 7f); lineTo(17f, 8.41f); lineTo(13.41f, 12f); lineTo(17f, 15.59f); close() } }
    }
    val Error: ImageVector by lazy {
        materialIcon("Error") { materialPath { moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close(); moveTo(13f, 17f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close(); moveTo(13f, 13f); horizontalLineToRelative(-2f); lineTo(11f, 7f); horizontalLineToRelative(2f); verticalLineToRelative(6f); close() } }
    }
    val Done: ImageVector by lazy {
        materialIcon("Done") { materialPath { moveTo(9f, 16.2f); lineTo(4.8f, 12f); lineToRelative(-1.4f, 1.4f); lineTo(9f, 19f); lineTo(21f, 7f); lineToRelative(-1.4f, -1.4f); lineTo(9f, 16.2f); close() } }
    }
    val Waiting: ImageVector by lazy {
        materialIcon("Waiting") { materialPath { moveTo(6f, 2f); verticalLineToRelative(6f); horizontalLineToRelative(0.01f); lineTo(6f, 8.01f); lineTo(10f, 12f); lineToRelative(-4f, 4f); lineToRelative(0.01f, 0.01f); horizontalLineTo(6f); verticalLineTo(22f); horizontalLineToRelative(12f); verticalLineToRelative(-5.99f); horizontalLineToRelative(-0.01f); lineTo(18f, 16f); lineToRelative(-4f, -4f); lineToRelative(4f, -3.99f); lineToRelative(-0.01f, -0.01f); horizontalLineTo(18f); verticalLineTo(2f); horizontalLineTo(6f); close(); moveTo(16f, 16.5f); verticalLineTo(20f); horizontalLineTo(8f); verticalLineToRelative(-3.5f); lineToRelative(4f, -4f); lineToRelative(4f, 4f); close(); moveTo(12f, 11.5f); lineToRelative(-4f, -4f); verticalLineTo(4f); horizontalLineToRelative(8f); verticalLineToRelative(3.5f); lineToRelative(-4f, 4f); close() } }
    }
    // Radar: two rings and a dot, drawn as even-odd-free filled shapes (ring = outer circle then inner circle reversed).
    val Running: ImageVector by lazy {
        materialIcon("Running") { materialPath {
            moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, true, 0f, 18f); arcToRelative(9f, 9f, 0f, true, true, 0f, -18f); close()
            moveTo(12f, 5f); arcToRelative(7f, 7f, 0f, true, false, 0f, 14f); arcToRelative(7f, 7f, 0f, true, false, 0f, -14f); close()
            moveTo(12f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0f, -8f); close()
            moveTo(12f, 10f); arcToRelative(2f, 2f, 0f, true, false, 0f, 4f); arcToRelative(2f, 2f, 0f, true, false, 0f, -4f); close()
        } }
    }
    val Link: ImageVector by lazy {
        materialIcon("Link") { materialPath { moveTo(3.9f, 12f); curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f); horizontalLineToRelative(4f); verticalLineTo(7f); horizontalLineTo(7f); curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f); reflectiveCurveToRelative(2.24f, 5f, 5f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(-1.9f); horizontalLineTo(7f); curveToRelative(-1.71f, 0f, -3.1f, -1.39f, -3.1f, -3.1f); close(); moveTo(8f, 13f); horizontalLineToRelative(8f); verticalLineToRelative(-2f); horizontalLineTo(8f); verticalLineToRelative(2f); close(); moveTo(17f, 7f); horizontalLineToRelative(-4f); verticalLineToRelative(1.9f); horizontalLineToRelative(4f); curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f); reflectiveCurveToRelative(-1.39f, 3.1f, -3.1f, 3.1f); horizontalLineToRelative(-4f); verticalLineTo(17f); horizontalLineToRelative(4f); curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f); reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f); close() } }
    }
    val OpenInNew: ImageVector by lazy {
        materialIcon("OpenInNew") { materialPath { moveTo(19f, 19f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineToRelative(7f); verticalLineTo(3f); horizontalLineTo(5f); curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineToRelative(-7f); horizontalLineToRelative(-2f); verticalLineToRelative(7f); close(); moveTo(14f, 3f); verticalLineToRelative(2f); horizontalLineToRelative(3.59f); lineToRelative(-9.83f, 9.83f); lineToRelative(1.41f, 1.41f); lineTo(19f, 6.41f); verticalLineTo(10f); horizontalLineToRelative(2f); verticalLineTo(3f); horizontalLineToRelative(-7f); close() } }
    }
    // Material "link_off" (Apache 2.0): the design's "No source" glyph.
    val LinkOff: ImageVector by lazy { fromPath("LinkOff", "M17 7h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1 0 1.43-.98 2.63-2.31 2.98l1.46 1.46C20.88 15.61 22 13.95 22 12c0-2.76-2.24-5-5-5zm-1 4h-2.19l2 2H16zM2 4.27l3.11 3.11C3.29 8.12 2 9.91 2 12c0 2.76 2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1 0-1.59 1.21-2.9 2.76-3.07L8.73 11H8v2h2.73L13 15.27V17h1.73l4.01 4L20 19.74 3.27 3 2 4.27z") }
    // Material "download" (Apache 2.0): save video action.
    val Download: ImageVector by lazy { fromPath("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z") }

    // D26 media sub-types (Material path data, Apache 2.0): theaters, live_tv, music_note, videogame_asset, animation.
    val Movie: ImageVector by lazy { fromPath("Movie", "M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z") }
    val Series: ImageVector by lazy { fromPath("Series", "M21 6h-7.59l3.29-3.29L16 2l-4 4-4-4-.71.71L10.59 6H3c-1.1 0-2 .89-2 2v12c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.11-.9-2-2-2zm0 14H3V8h18v12zM9 10v8l7-4z") }
    val Song: ImageVector by lazy { fromPath("Song", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z") }
    val Game: ImageVector by lazy { fromPath("Game", "M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-10 7H8v3H6v-3H3v-2h3V8h2v3h3v2zm4.5 2c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4-3c-.83 0-1.5-.67-1.5-1.5S18.67 9 19.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z") }
    val Anime: ImageVector by lazy { fromPath("Anime", "M15 2c-2.71 0-5.05 1.54-6.22 3.78-1.28.67-2.34 1.72-3 3C3.54 9.95 2 12.29 2 15c0 3.87 3.13 7 7 7 2.71 0 5.05-1.54 6.22-3.78 1.28-.67 2.34-1.72 3-3C20.46 14.05 22 11.71 22 9c0-3.87-3.13-7-7-7zM9 20c-2.76 0-5-2.24-5-5 0-1.12.37-2.16 1-3 0 3.87 3.13 7 7 7-.84.63-1.88 1-3 1zm3-3c-2.76 0-5-2.24-5-5 0-1.12.37-2.16 1-3 0 3.86 3.13 6.99 7 7-.84.63-1.88 1-3 1zm4.7-3.3c-.53.19-1.1.3-1.7.3-2.76 0-5-2.24-5-5 0-.6.11-1.17.3-1.7.53-.19 1.1-.3 1.7-.3 2.76 0 5 2.24 5 5 0 .6-.11 1.17-.3 1.7zM19 12c0-3.86-3.13-6.99-7-7 .84-.63 1.87-1 3-1 2.76 0 5 2.24 5 5 0 1.12-.37 2.16-1 3z") }

    /** One 24 dp Material icon from its SVG path data, filled like [materialPath] (the tint replaces the colour). */
    internal fun fromPath(name: String, d: String): ImageVector = materialIcon(name) { addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }
}
