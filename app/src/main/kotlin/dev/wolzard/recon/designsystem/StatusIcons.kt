package dev.wolzard.recon.designsystem

import androidx.compose.material.icons.materialIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

/** Status and media glyphs by meaning, mapped onto the design's outlined [Symbols]. Meaning is the icon plus the label, never colour alone. */
object StatusIcons {
    val Check get() = Symbols.CheckCircle       // verified / supported
    val Info get() = Symbols.ContactSupport     // unverified
    val Help get() = Symbols.Help               // unclear
    val Cancel get() = Symbols.Cancel           // contradicted
    val Error get() = Symbols.Error             // failed
    val Done get() = Symbols.Check
    val Waiting get() = Symbols.HourglassEmpty
    val Running get() = Symbols.Radar
    val Link get() = Symbols.Link
    val OpenInNew get() = Symbols.OpenInNew
    val LinkOff get() = Symbols.LinkOff         // "No source"
    // Material "download" (Apache 2.0): save video action.
    val Download: ImageVector by lazy { fromPath("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z") }

    // Material "search" (Apache 2.0): the Library's search field.
    val Search: ImageVector by lazy { fromPath("Search", "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z") }

    // D26 media sub-types
    val Movie get() = Symbols.Theaters
    val Series get() = Symbols.LiveTv
    val Song get() = Symbols.MusicNote
    val Game get() = Symbols.SportsEsports
    val Anime get() = Symbols.Animation

    /** One 24 dp Material icon from its SVG path data, filled like [materialPath] (the tint replaces the colour). */
    internal fun fromPath(name: String, d: String): ImageVector = materialIcon(name) { addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }
}
