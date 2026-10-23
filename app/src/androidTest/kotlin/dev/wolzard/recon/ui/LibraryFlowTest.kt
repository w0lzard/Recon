package dev.wolzard.recon.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomEntryStore
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.library.EntryRoute
import dev.wolzard.recon.feature.library.EntryViewModel
import dev.wolzard.recon.feature.library.LibraryRoute
import dev.wolzard.recon.feature.library.LibraryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D37: two videos about the same repo become one library entry, found by search on the device's own SQLite (FTS4), showing both videos. */
@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, ReconDb::class.java).allowMainThreadQueries().build()
    private val jobs = RoomJobStore(db)
    private val entries = RoomEntryStore(db)
    @After fun close() = db.close()

    private fun video(link: String, title: String) = runBlocking {
        val repo = Finding(Entity(EntityType.REPO, "stowly", "kmori", Evidence.SPOKEN, "said"), "github", Verdict.CONFIRMED, "kmori/stowly", "Symlink farm for dotfiles", emptyList(), "", "", "https://github.com/kmori/stowly", 4100, Basis.GITHUB_API)
        val id = jobs.create(link, null, normalizedUrl = link)
        val r = JobResult(AnalysisResult(summary = "s", confidence = 1.0), listOf(repo), emptyList(), null, "A")
        jobs.update(id) { it.copy(state = JobState.DONE, videoTitle = title, resultJson = Json.encodeToString(JobResult.serializer(), r)) }
        entries.linkJob(jobs.get(id)!!)
    }

    @Test fun twoVideosOnTheSameRepoShowAsOneEntryWithBothVideos() {
        video("https://youtu.be/one", "My desk setup")
        video("https://www.instagram.com/reel/two/", "Dotfiles in 60 seconds")
        var open by mutableStateOf<Long?>(null)
        compose.setContent {
            ReconTheme {
                val id = open
                if (id == null) LibraryRoute(LibraryViewModel(entries), onOpenEntry = { open = it }, onBack = {})
                else EntryRoute(EntryViewModel(id, entries), onOpenJob = {}, onBack = {})
            }
        }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("2 videos").assertExists() }.isSuccess }
        compose.onNodeWithTag("library_search").performTextInput("symlink")   // matched by summary through FTS4
        compose.onNodeWithText("kmori/stowly").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("VIDEOS · 2").assertExists() }.isSuccess }
        compose.onNodeWithText("My desk setup").assertExists()
        compose.onNodeWithText("Dotfiles in 60 seconds").assertExists()
        assertEquals(1, runBlocking { entries.observeList("", null, 10).let { it.first() }.size })
    }
}
