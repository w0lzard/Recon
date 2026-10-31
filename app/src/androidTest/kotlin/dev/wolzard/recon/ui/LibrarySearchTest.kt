package dev.wolzard.recon.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import dev.wolzard.recon.feature.library.LibraryRoute
import dev.wolzard.recon.feature.library.LibraryViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D39: searching by what you need finds the saved tool on the device's own SQLite (FTS4 over search phrases), and a typo still finds a name. */
@RunWith(AndroidJUnit4::class)
class LibrarySearchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, ReconDb::class.java).allowMainThreadQueries().build()
    private val jobs = RoomJobStore(db)
    private val entries = RoomEntryStore(db)
    @After fun close() = db.close()

    private fun video(link: String, name: String, owner: String?, type: EntityType, phrases: List<String>) = runBlocking {
        val entity = Entity(type, name, owner, Evidence.SPOKEN, "said", searchPhrases = phrases)
        val finding = Finding(entity, "github", Verdict.CONFIRMED, if (owner != null) "$owner/$name" else name, "A tool.", emptyList(), "", "", null, null, Basis.GITHUB_API)
        val id = jobs.create(link, null, normalizedUrl = link)
        val r = JobResult(AnalysisResult(summary = "s", confidence = 1.0), listOf(finding), emptyList(), null, "A")
        jobs.update(id) { it.copy(state = JobState.DONE, videoTitle = name, resultJson = Json.encodeToString(JobResult.serializer(), r)) }
        entries.linkJob(jobs.get(id)!!)
    }

    @Test fun aNeedFindsTheToolAndATypoFindsAName() {
        video("https://youtu.be/one", "yt-dlp", "yt-dlp", EntityType.REPO, listOf("video download tool", "save videos from websites", "download audio from videos"))
        video("https://youtu.be/two", "Tern", null, EntityType.APP, listOf("terminal emulator for android"))
        compose.setContent { ReconTheme { LibraryRoute(LibraryViewModel(entries), onOpenEntry = {}, onBack = {}) } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("yt-dlp/yt-dlp").assertExists() }.isSuccess }
        compose.onNodeWithTag("library_search").performTextInput("video download")   // in none of the names or summaries
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Tern").assertDoesNotExist() }.isSuccess }
        compose.onNodeWithText("yt-dlp/yt-dlp").assertExists()
    }

    @Test fun aTypoedNameStillFindsTheEntry() {
        video("https://youtu.be/two", "Tern", null, EntityType.APP, emptyList())
        video("https://youtu.be/three", "Obsidian", null, EntityType.APP, emptyList())
        compose.setContent { ReconTheme { LibraryRoute(LibraryViewModel(entries), onOpenEntry = {}, onBack = {}) } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Obsidian").assertExists() }.isSuccess }
        compose.onNodeWithTag("library_search").performTextInput("tren")          // no full-text hit: the edit-distance pass finds "Tern" and only it
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Obsidian").assertDoesNotExist() }.isSuccess }
        compose.onNodeWithText("Tern").assertExists()
    }
}
