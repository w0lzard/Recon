package dev.wolzard.recon.feature.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.core.model.EntryLink
import dev.wolzard.recon.core.research.LinkType
import dev.wolzard.recon.core.research.Maturity
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomEntryStore
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.feature.home.HomeRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).allowMainThreadQueries().build()
    private val jobs = RoomJobStore(db)
    private val entries = RoomEntryStore(db)
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun close() { db.close(); Dispatchers.resetMain() }

    private fun entry(id: Long, name: String, type: EntityType = EntityType.REPO, videos: Int = 2, stars: Int? = 4100, status: String = "Verified") =
        Entry(id, type, "k$id", name, "Does a thing.", "https://github.com/x/y", stars, status, videos, 1, 2)

    private fun fileJob(vararg f: Finding): String = runBlocking {
        val id = jobs.create("https://youtu.be/${f.hashCode()}", null, normalizedUrl = "n${f.hashCode()}")
        val r = JobResult(AnalysisResult(summary = "s", confidence = 1.0), f.toList(), emptyList(), null, "A")
        jobs.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), r)) }
        entries.linkJob(jobs.get(id)!!)
        id
    }

    private fun finding(type: EntityType, name: String, title: String = name) =
        Finding(Entity(type, name, null, Evidence.SPOKEN, "said"), "web", Verdict.CONFIRMED, title, "About $name", emptyList(), "", "", "https://github.com/x/$name", 10, Basis.GITHUB_API)

    // --- screens

    @Test fun cardShowsTypeStatusStarsAndVideoCount() {
        compose.setContent { LibraryScreen(LibraryState(entries = listOf(entry(1, "kmori/stowly"), entry(2, "Obsidian", EntityType.APP, videos = 1, stars = null, status = "Unverified")), loaded = true)) {} }
        compose.onNodeWithText("kmori/stowly").assertExists()
        compose.onNodeWithText("REPO").assertExists()
        compose.onNodeWithText("VERIFIED").assertExists()
        compose.onNodeWithText("★ 4.1k").assertExists()
        compose.onNodeWithText("2 videos").assertExists()
        compose.onNodeWithText("1 video").assertExists()
        compose.onNodeWithText("UNVERIFIED").assertExists()
    }

    @Test fun emptyLibrarySaysNothingSavedYet() {
        compose.setContent { LibraryScreen(LibraryState(loaded = true)) {} }
        compose.onNodeWithText("Nothing saved yet").assertExists()
    }

    @Test fun emptySearchSaysNoMatches() {
        compose.setContent { LibraryScreen(LibraryState(query = "zzz", loaded = true)) {} }
        compose.onNodeWithText("No matches").assertExists()
    }

    @Test fun showMoreAppearsOnAFullPageOnly() {
        val page = (1L..LIBRARY_PAGE).map { entry(it, "E$it") }
        compose.setContent { LibraryScreen(LibraryState(entries = page, loaded = true)) {} }
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_more"))
        compose.onAllNodesWithTag("library_more").assertCountEquals(1)
    }

    @Test fun typingFilterAndOpeningSendIntents() {
        val sent = mutableListOf<LibraryIntent>()
        compose.setContent { LibraryScreen(LibraryState(entries = listOf(entry(7, "kmori/stowly")), loaded = true)) { sent += it } }
        compose.onNodeWithTag("library_search").performTextInput("stow")
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("kmori/stowly").performClick()
        assertTrue(LibraryIntent.QueryChanged("stow") in sent)
        assertTrue(LibraryIntent.FilterChanged(LibraryFilter.APPS) in sent)
        assertTrue(LibraryIntent.OpenEntry(7) in sent)
    }

    @Test fun entryScreenShowsLinkAliasesAttentionAndVideos() {
        val e = entry(1, "kmori/stowly").copy(aliases = listOf("kmori-stowly", "stowly"), needsAttention = true, attentionReason = "Possible duplicate of Tern")
        val row = HomeRow.from(dev.wolzard.recon.core.model.Job("j1", "x", url = "https://youtu.be/a", state = JobState.DONE, videoTitle = "My desk setup", createdAt = 1, updatedAt = 1), 1)
        val sent = mutableListOf<EntryIntent>()
        compose.setContent { EntryScreen(EntryState(e, listOf(row), loaded = true)) { sent += it } }
        compose.onNodeWithText("Also known as kmori-stowly, stowly").assertExists()
        compose.onNodeWithText("Possible duplicate of Tern").assertExists()
        compose.onNodeWithText("VIDEOS · 1").assertExists()
        compose.onNodeWithTag("entry_link").performClick()
        compose.onNodeWithText("My desk setup").performClick()
        assertEquals(listOf(EntryIntent.OpenLink("https://github.com/x/y"), EntryIntent.OpenVideo("j1")), sent)
    }

    @Test fun entryScreenShowsMaturityChipFactsAndTypedLinks() {
        val now = System.currentTimeMillis()
        val e = entry(1, "kmori/stowly").copy(
            maturity = Maturity.ESTABLISHED, license = "MIT", language = "Rust", pushedAt = now - 3 * 86_400_000L, latestReleaseTag = "v1.2", latestReleaseAt = now - 40 * 86_400_000L,
            links = listOf(EntryLink("https://github.com/kmori/stowly", LinkType.REPO), EntryLink("https://docs.stowly.dev/start", LinkType.DOCS)),
        )
        val sent = mutableListOf<EntryIntent>()
        compose.setContent { EntryScreen(EntryState(e, emptyList(), loaded = true)) { sent += it } }
        compose.onNodeWithTag("entry_maturity").assertExists()
        compose.onNodeWithText("ESTABLISHED").assertExists()
        compose.onNodeWithTag("entry_facts").assertExists()
        compose.onNodeWithText("MIT · Rust · pushed 3 days ago · v1.2, 1 month ago").assertExists()
        compose.onNodeWithText("LINKS").assertExists()
        compose.onNodeWithText("Code · github.com").assertExists()
        compose.onNodeWithTag("entry_link_DOCS").performClick()
        assertEquals(listOf(EntryIntent.OpenLink("https://docs.stowly.dev/start")), sent)
    }

    @Test fun entryScreenWithoutFactsShowsNoChipFactsOrLinks() {
        compose.setContent { EntryScreen(EntryState(entry(1, "kmori/stowly"), emptyList(), loaded = true)) {} }
        compose.onAllNodesWithTag("entry_maturity").assertCountEquals(0)
        compose.onAllNodesWithTag("entry_facts").assertCountEquals(0)
        compose.onAllNodesWithText("LINKS").assertCountEquals(0)
    }

    @Test fun maturityChipMapsEveryTierAndHidesUnknown() {
        assertEquals("Established" to ChipKind.Ok, maturityChip(Maturity.ESTABLISHED))
        assertEquals("Growing" to ChipKind.Neutral, maturityChip(Maturity.GROWING))
        assertEquals("Experimental" to ChipKind.Neutral, maturityChip(Maturity.EXPERIMENTAL))
        assertEquals("Inactive" to ChipKind.Bad, maturityChip(Maturity.INACTIVE))
        assertEquals(null, maturityChip(Maturity.UNKNOWN))
        assertEquals(null, maturityChip(null))
    }

    @Test fun agoLabelAndFactsLine() {
        val day = 86_400_000L
        assertEquals("today", agoLabel(0, day - 1)); assertEquals("1 day ago", agoLabel(0, day)); assertEquals("29 days ago", agoLabel(0, 29 * day))
        assertEquals("1 month ago", agoLabel(0, 30 * day)); assertEquals("11 months ago", agoLabel(0, 359 * day))
        assertEquals("1 year ago", agoLabel(0, 365 * day)); assertEquals("3 years ago", agoLabel(0, 1100 * day))
        assertEquals("today", agoLabel(5 * day, 0))   // a clock a little behind GitHub's
        assertEquals(null, factsLine(entry(1, "a/b"), 0))
        assertEquals("MIT", factsLine(entry(1, "a/b").copy(license = "MIT"), 0))
        assertEquals("v2", factsLine(entry(1, "a/b").copy(latestReleaseTag = "v2"), 0))
    }

    @Test fun goneEntryIsSaid() {
        compose.setContent { EntryScreen(EntryState(loaded = true)) {} }
        compose.onNodeWithText("This entry is gone.").assertExists()
    }

    // --- view models

    private fun <T> eventually(block: () -> T?): T = runBlocking {
        withTimeout(5_000) { var v = block(); while (v == null) { kotlinx.coroutines.delay(10); v = block() }; v }
    }

    @Test fun viewModelListsSearchesFiltersAndPages() {
        fileJob(finding(EntityType.REPO, "stowly", "kmori/stowly"))
        fileJob(finding(EntityType.APP, "Obsidian"))
        val vm = LibraryViewModel(entries)
        assertEquals(2, eventually { vm.state.value.entries.takeIf { it.size == 2 } }.size)
        vm.onIntent(LibraryIntent.QueryChanged("stow"))
        assertEquals(listOf("kmori/stowly"), eventually { vm.state.value.entries.takeIf { it.size == 1 } }.map { it.name })
        vm.onIntent(LibraryIntent.QueryChanged(""))
        vm.onIntent(LibraryIntent.FilterChanged(LibraryFilter.APPS))
        assertEquals(listOf("Obsidian"), eventually { vm.state.value.entries.takeIf { it.size == 1 && it[0].name == "Obsidian" } }.map { it.name })
        vm.onIntent(LibraryIntent.ShowMore)
        assertEquals(2 * LIBRARY_PAGE, vm.state.value.limit)
        vm.onIntent(LibraryIntent.QueryChanged("x"))
        assertEquals(LIBRARY_PAGE, vm.state.value.limit)   // a new search starts at page one
        eventually { vm.state.value.takeIf { it.entries.isEmpty() } }   // let the last query finish before the database closes
    }

    @Test fun viewModelRunsTheBackfillAndOpensEntries() = runBlocking {
        var ran = 0
        val vm = LibraryViewModel(entries) { ran++ }
        eventually { vm.state.value.takeIf { it.loaded } }
        assertEquals(1, ran)
        vm.onIntent(LibraryIntent.OpenEntry(3)); assertEquals(LibraryEffect.NavigateToEntry(3), withTimeout(5_000) { vm.effects.first() })
        vm.onIntent(LibraryIntent.Back); assertEquals(LibraryEffect.NavigateBack, withTimeout(5_000) { vm.effects.first() })
    }

    @Test fun entryViewModelShowsTheEntryWithBothVideos() {
        val a = fileJob(finding(EntityType.REPO, "stowly", "kmori/stowly"))
        val b = fileJob(finding(EntityType.REPO, "stowly", "kmori/stowly").copy(whatItIs = "More"))
        val id = runBlocking { entries.observeList("", null, 10).first().single().id }
        val vm = EntryViewModel(id, entries)
        val s = eventually { vm.state.value.takeIf { it.loaded && it.videos.size == 2 } }
        assertEquals("kmori/stowly", s.entry!!.name)
        assertEquals(setOf(a, b), s.videos.map { it.id }.toSet())
        vm.onIntent(EntryIntent.OpenVideo(a))
        assertEquals(EntryEffect.NavigateToJob(a), runBlocking { withTimeout(5_000) { vm.effects.first() } })
    }
}
