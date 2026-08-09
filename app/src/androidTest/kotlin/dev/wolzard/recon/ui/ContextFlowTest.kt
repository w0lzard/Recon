package dev.wolzard.recon.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.context.ContextCopy
import dev.wolzard.recon.feature.context.ContextRoute
import dev.wolzard.recon.feature.context.ContextViewModel
import dev.wolzard.recon.feature.job.JobActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D23 on a device: the real screen, ViewModel, Room and JobActions; only WorkManager is replaced by a recorder. */
@RunWith(AndroidJUnit4::class)
class ContextFlowTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).build()
    private val store = RoomJobStore(db)
    private val queued = mutableListOf<Pair<String, UserAction>>()
    private var opened: String? = null

    @After fun close() = db.close()

    private fun show(link: String = "https://www.instagram.com/reel/abc/") {
        val vm = ContextViewModel(link, JobActions(store) { id, a -> queued += id to a })
        compose.setContent { ReconTheme { ContextRoute(vm, onOpenJob = { opened = it }, onBack = {}) } }
    }

    private fun jobs(): List<Job> = runBlocking { store.observeAll().first() }

    @Test fun skipStartsTheJobAtOnceWithNoContext() {
        show()
        compose.onNodeWithText("instagram.com").assertExists()
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { opened != null }
        val job = jobs().single()
        assertEquals(job.id, opened)
        assertNull(job.userContext)
        assertEquals(listOf(job.id to UserAction.RUN), queued)
    }

    @Test fun startStoresTheTrimmedContextAndQueuesTheJob() {
        show()
        compose.onNodeWithTag("context_field").performTextInput("  Who is this?  ")
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil(5_000) { opened != null }
        val job = jobs().single()
        assertEquals("Who is this?", job.userContext)
        assertEquals(1, queued.size)
    }

    @Test fun chipFillsTheFieldAndStartKeepsIt() {
        show()
        compose.onNodeWithText("What song is this?").performScrollTo().performClick()
        compose.onNodeWithText("What song is this?".length.toString() + "/300").assertExists()
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil(5_000) { opened != null }
        assertEquals("What song is this?", jobs().single().userContext)
    }

    @Test fun invalidLinkAtStartShowsAnErrorAndCreatesNoJob() {
        show(link = "javascript:alert(1)")
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(ContextCopy.INVALID_LINK) > 0 }
        assertTrue(jobs().isEmpty())
        assertTrue(queued.isEmpty())
        assertNull(opened)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().size
}
