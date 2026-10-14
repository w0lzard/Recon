package dev.wolzard.recon.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.data.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyState() {
        compose.setContent { HomeScreen(HomeState()) {} }
        compose.onNodeWithText("No jobs yet").assertExists()
        compose.onNodeWithText("Share a video from any app, or paste a link above.").assertExists()
    }

    @Test fun updateBannerIsNotOnHome() { // design 1l: it lives in Settings
        val info = UpdateInfo("0.2.0", "https://github.com/w0lzard/recon/releases/download/v0.2.0/a.apk", "https://github.com/w0lzard/recon/releases/tag/v0.2.0")
        compose.setContent { HomeScreen(HomeState(update = info)) {} }
        compose.onNodeWithText("Recon 0.2.0 is available").assertDoesNotExist()
    }

    @Test fun rowsShowStateHostAndSummaryAndOpen() {
        val intents = mutableListOf<HomeIntent>()
        val now = 10_000_000L
        val job = Job("j1", "look https://www.instagram.com/reel/abc/", state = JobState.DONE,
            resultJson = """{"analysis":{"entities":[],"summary":"A slime video.\nMore.","confidence":0.0},"findings":[],"sources":[],"searchSuggestionsHtml":null,"path":"A"}""",
            createdAt = now - 120_000, updatedAt = 0)
        val shared = Job("j2", "", localUri = "/files/shared/x/video.mp4", state = JobState.WAITING_QUOTA, createdAt = now, updatedAt = 0)
        compose.setContent { HomeScreen(HomeState(listOf(HomeRow.from(job, now), HomeRow.from(shared, now)))) { intents += it } }
        compose.onNodeWithText("A slime video.").assertExists()
        compose.onNodeWithText("instagram.com · 2 min").assertExists()
        compose.onNodeWithText("DONE").assertExists()
        compose.onNodeWithText("Shared video").assertExists() // no title: the host leads
        compose.onNodeWithText("Usage limit · now").assertExists()
        compose.onNodeWithText("WAITING").assertExists()
        compose.onNodeWithText("RECENT · 2").assertExists()
        compose.onNodeWithText("A slime video.").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.OpenJob("j1")), intents)
    }

    @Test fun startReconSendsTheFieldAndShowsTheError() {
        val intents = mutableListOf<HomeIntent>()
        compose.setContent { HomeScreen(HomeState(pasteError = "That isn't a video link.")) { intents += it } }
        compose.onNodeWithText("That isn't a video link.").assertExists()
        compose.onNodeWithText("Paste a video link").performTextInput("https://youtu.be/x")
        compose.onNodeWithText("Start recon").performClick()
        compose.onNodeWithContentDescription("Paste from clipboard").performClick() // empty clipboard in the test: clears the field
        compose.onNodeWithText("Start recon").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.PasteLink("https://youtu.be/x"), HomeIntent.PasteLink("")), intents)
    }

    @Test fun relativeTimes() {
        val z = ZoneOffset.UTC
        val now = LocalDateTime.of(2026, 10, 14, 14, 2).toInstant(z).toEpochMilli()
        fun at(min: Long) = HomeRow.relative(now - min * 60_000, now, z)
        assertEquals("now", at(0))
        assertEquals("2 min", at(2))
        assertEquals("59 min", at(59))
        assertEquals("1 h", at(60))
        assertEquals("Yesterday", at(15 * 60))
        assertEquals("12 Oct", at(2 * 24 * 60))
    }
}
