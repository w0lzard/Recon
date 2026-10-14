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

    // D26 media sub-types
    val Movie get() = Symbols.Theaters
    val Series get() = Symbols.LiveTv
    val Song get() = Symbols.MusicNote
    val Game get() = Symbols.SportsEsports
    val Anime get() = Symbols.Animation

    /** One 24 dp Material icon from its SVG path data, filled like [materialPath] (the tint replaces the colour). */
    internal fun fromPath(name: String, d: String): ImageVector = materialIcon(name) { addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }
}
