package dev.wolzard.recon.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.data.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyState() {
        compose.setContent { HomeScreen(HomeState()) {} }
        compose.onNodeWithText("Share a video link to Recon from any app").assertExists()
    }

    @Test fun updateBannerOpensLinksAndDismisses() {
        val intents = mutableListOf<HomeIntent>()
        val info = UpdateInfo("0.2.0", "https://github.com/w0lzard/recon/releases/download/v0.2.0/a.apk", "https://github.com/w0lzard/recon/releases/tag/v0.2.0")
        compose.setContent { HomeScreen(HomeState(update = info)) { intents += it } }
        compose.onNodeWithText("Recon 0.2.0 is available").assertExists()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("What's new").performClick()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf(HomeIntent.OpenLink(info.apkUrl), HomeIntent.OpenLink(info.releasePageUrl), HomeIntent.DismissUpdate("0.2.0")), intents)
    }

    @Test fun rowsShowStateHostAndSummaryAndOpen() {
        val intents = mutableListOf<HomeIntent>()
        val job = Job("j1", "look https://www.instagram.com/reel/abc/", state = JobState.DONE,
            resultJson = """{"analysis":{"entities":[],"summary":"A slime video.\nMore.","confidence":0.0},"findings":[],"sources":[],"searchSuggestionsHtml":null,"path":"A"}""",
            createdAt = 0, updatedAt = 0)
        val shared = Job("j2", "", localUri = "/files/shared/x/video.mp4", state = JobState.WAITING_QUOTA, createdAt = 0, updatedAt = 0)
        compose.setContent { HomeScreen(HomeState(listOf(HomeRow.from(job), HomeRow.from(shared)))) { intents += it } }
        compose.onNodeWithText("instagram.com").assertExists()
        compose.onNodeWithText("A slime video.").assertExists()
        compose.onNodeWithText("DONE").assertExists()
        compose.onNodeWithText("Shared video").assertExists()
        compose.onNodeWithText("WAITING FOR QUOTA").assertExists()
        compose.onNodeWithText("A slime video.").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.OpenJob("j1")), intents)
    }

    @Test fun pasteButtonSendsClipboardTextAndShowsTheError() {
        val intents = mutableListOf<HomeIntent>()
        compose.setContent { HomeScreen(HomeState(pasteError = "No video link on the clipboard. Copy one first.")) { intents += it } }
        compose.onNodeWithText("No video link on the clipboard. Copy one first.").assertExists()
        compose.onNodeWithText("Paste a video link").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.PasteLink("")), intents) // empty clipboard in the test
    }
}
