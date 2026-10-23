package dev.wolzard.recon.feature.library

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

