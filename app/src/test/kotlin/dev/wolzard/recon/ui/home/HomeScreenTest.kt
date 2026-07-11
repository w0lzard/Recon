package dev.wolzard.recon.ui.home

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
        compose.setContent { HomeScreen(emptyList(), {}, {}) }
        compose.onNodeWithText("Share a video link to Recon from any app").assertExists()
    }

    @Test fun updateBannerOpensLinksAndDismisses() {
        val opened = mutableListOf<String>()
        var dismissed = false
        val info = UpdateInfo("0.2.0", "https://github.com/w0lzard/recon/releases/download/v0.2.0/a.apk", "https://github.com/w0lzard/recon/releases/tag/v0.2.0")
        compose.setContent { HomeScreen(emptyList(), {}, {}) { UpdateBanner(info, { opened += it }, { dismissed = true }) } }
        compose.onNodeWithText("Recon 0.2.0 is available").assertExists()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("What's new").performClick()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf(info.apkUrl, info.releasePageUrl), opened)
        assertEquals(true, dismissed)
    }

    @Test fun rowsShowStateHostAndSummaryAndOpen() {
        val opened = mutableListOf<String>()
        val job = Job("j1", "look https://www.instagram.com/reel/abc/", state = JobState.DONE,
            resultJson = """{"analysis":{"entities":[],"summary":"A slime video.\nMore.","confidence":0.0},"findings":[],"sources":[],"searchSuggestionsHtml":null,"path":"A"}""",
            createdAt = 0, updatedAt = 0)
        val shared = Job("j2", "", localUri = "/files/shared/x/video.mp4", state = JobState.WAITING_QUOTA, createdAt = 0, updatedAt = 0)
        compose.setContent { HomeScreen(listOf(HomeRow.from(job), HomeRow.from(shared)), { opened += it }, {}) }
        compose.onNodeWithText("instagram.com").assertExists()
        compose.onNodeWithText("A slime video.").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Shared video").assertExists()
        compose.onNodeWithText("Waiting for quota").assertExists()
        compose.onNodeWithText("A slime video.").performClick()
        assertEquals(listOf("j1"), opened)
    }
}
