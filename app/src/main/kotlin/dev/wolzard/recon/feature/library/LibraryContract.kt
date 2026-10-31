package dev.wolzard.recon.feature.library

import dev.wolzard.recon.core.research.LinkType
import dev.wolzard.recon.core.research.Maturity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.feature.home.HomeRow

/** The chips under the search field. Everything else (books, places, recipes...) shows under All. */
enum class LibraryFilter(val label: String, val types: Set<EntityType>?) {
    ALL("All", null),
    REPOS("Repos", setOf(EntityType.REPO)),
    APPS("Apps", setOf(EntityType.APP, EntityType.PRODUCT)),
    MEDIA("Media", setOf(EntityType.MEDIA)),
}

const val LIBRARY_PAGE = 30

data class LibraryState(
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val entries: List<Entry> = emptyList(),
    val limit: Int = LIBRARY_PAGE,
    val loaded: Boolean = false,
) {
    /** A full page came back, so there may be more. */
    val hasMore get() = entries.size >= limit
}

sealed interface LibraryIntent {
    data class QueryChanged(val text: String) : LibraryIntent
    data class FilterChanged(val filter: LibraryFilter) : LibraryIntent
    data object ShowMore : LibraryIntent
    data class OpenEntry(val id: Long) : LibraryIntent
    data object Back : LibraryIntent
}

sealed interface LibraryEffect {
    data class NavigateToEntry(val id: Long) : LibraryEffect
    data object NavigateBack : LibraryEffect
}

data class EntryState(val entry: Entry? = null, val videos: List<HomeRow> = emptyList(), val loaded: Boolean = false)

sealed interface EntryIntent {
    data class OpenVideo(val id: String) : EntryIntent
    data class OpenLink(val url: String) : EntryIntent
    data object Back : EntryIntent
}

sealed interface EntryEffect {
    data class NavigateToJob(val id: String) : EntryEffect
    data class OpenInBrowser(val url: String) : EntryEffect
    data object NavigateBack : EntryEffect
}

/** The chip word the job card used, as a chip colour. */
fun statusKind(status: String) = when (status) {
    "Verified", "Supported" -> ChipKind.Ok
    "Unverified" -> ChipKind.Warn
    "Unclear" -> ChipKind.Unclear
    "Contradicted" -> ChipKind.Bad
    else -> ChipKind.Neutral
}

/** "REPO · 3 videos"-style type word for the card. */
fun typeLabel(t: EntityType) = if (t == EntityType.MEDIA) "MEDIA" else t.wire.uppercase()


/** The maturity chip: a label and colour, or null when there is nothing to say (never fetched, or GitHub gave too little). */
fun maturityChip(m: Maturity?): Pair<String, ChipKind>? = when (m) {
    Maturity.ESTABLISHED -> "Established" to ChipKind.Ok
    Maturity.GROWING -> "Growing" to ChipKind.Neutral        // the plan wanted a blue chip; the design system has no such kind yet
    Maturity.EXPERIMENTAL -> "Experimental" to ChipKind.Neutral
    Maturity.INACTIVE -> "Inactive" to ChipKind.Bad
    Maturity.UNKNOWN, null -> null
}

/** "3 days ago" style age of [then] at [now]; "today" under a day. */
fun agoLabel(then: Long, now: Long): String {
    val days = ((now - then) / 86_400_000L).coerceAtLeast(0)
    return when {
        days < 1 -> "today"
        days < 30 -> if (days == 1L) "1 day ago" else "$days days ago"
        days < 365 -> "${days / 30} ${if (days / 30 == 1L) "month" else "months"} ago"
        else -> "${days / 365} ${if (days / 365 == 1L) "year" else "years"} ago"
    }
}

/** License, language, last push and latest release on one line; null when the entry has none of them. */
fun factsLine(e: Entry, now: Long): String? = listOfNotNull(
    e.license, e.language,
    e.pushedAt?.let { "pushed ${agoLabel(it, now)}" },
    e.latestReleaseTag?.let { tag -> e.latestReleaseAt?.let { "$tag, ${agoLabel(it, now)}" } ?: tag },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

fun linkLabel(t: LinkType) = when (t) {
    LinkType.REPO -> "Code"
    LinkType.DOCS -> "Docs"
    LinkType.PACKAGE -> "Package"
    LinkType.PAPER -> "Paper"
    LinkType.WEBSITE -> "Web"
}

fun hasStructuredFields(e: Entry) = e.problemSolved != null || e.keyFeatures.isNotEmpty() || e.useCases.isNotEmpty() || e.whenToUse != null || e.whenNotToUse != null
