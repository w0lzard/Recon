package dev.wolzard.recon.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.link.UrlNormalizer
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.LinkResolver
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.context.ContextRoute
import dev.wolzard.recon.feature.context.ContextViewModel
import dev.wolzard.recon.feature.job.JobActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D31: Duplicate check by normalised link.
 * Covers:
 * - DONE shows previous result directly when no context was typed.
 * - DONE shows choice dialog when context was typed (View previous vs Re-analyse).
 * - in-flight (ANALYSING, QUEUED) and WAITING_QUOTA open the live job without duplicate queueing.
 * - FAILED starts a fresh attempt without prompting and keeps old job in history.
 */
@RunWith(AndroidJUnit4::class)
class DuplicateCaptureFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, ReconDb::class.java).allowMainThreadQueries().build()
    private val store = RoomJobStore(db)
    private val normalizer = UrlNormalizer(object : LinkResolver { override suspend fun resolve(url: String) = url })
    private val queued = mutableListOf<Pair<String, UserAction>>()
    private val actions = JobActions(
        store = store,
        cancelWork = {},
        enqueue = { id, a -> queued += id to a },
        normalizer = normalizer,
    )
    private var openedId: String? = null

    @Before fun setUp() {
        openedId = null
        queued.clear()
    }

    @After fun tearDown() {
        db.close()
    }

    private fun show(link: String) {
        val vm = ContextViewModel(link, actions, store, normalizer)
        compose.setContent { ReconTheme { ContextRoute(vm, onOpenJob = { openedId = it }, onBack = {}) } }
    }

    @Test fun doneJobWithoutContextShowsPreviousResultImmediately() {
        val prevId = runBlocking { store.create("https://www.youtube.com/watch?v=Psy_4mEWNDI", null, null, normalizedUrl = "youtube:Psy_4mEWNDI") }
        runBlocking { store.update(prevId) { it.copy(state = JobState.DONE) } }

        show("https://youtu.be/Psy_4mEWNDI?feature=share")
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { openedId != null }

        assertEquals("Must navigate to the previous completed job", prevId, openedId)
        assertEquals("No new job queued", 0, queued.size)
    }

    @Test fun doneJobWithContextChoosingViewPreviousOpensExisting() {
        val prevId = runBlocking { store.create("https://www.youtube.com/watch?v=Psy_4mEWNDI", null, null, normalizedUrl = "youtube:Psy_4mEWNDI") }
        runBlocking { store.update(prevId) { it.copy(state = JobState.DONE) } }

        show("https://youtu.be/Psy_4mEWNDI?si=tracking")
        compose.onNodeWithText("What song is this?").performClick()
        compose.onNodeWithText("Start").performClick()

        // Verify choice dialog appears
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Previous analysis exists").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("View previous").performClick()

        compose.waitUntil(5_000) { openedId != null }
        assertEquals("Choosing View previous must open existing job", prevId, openedId)
        assertEquals("No new job queued", 0, queued.size)
    }

    @Test fun doneJobWithContextChoosingReanalyseStartsFreshJob() {
        val prevId = runBlocking { store.create("https://www.youtube.com/watch?v=Psy_4mEWNDI", null, null, normalizedUrl = "youtube:Psy_4mEWNDI") }
        runBlocking { store.update(prevId) { it.copy(state = JobState.DONE) } }

        show("https://youtu.be/Psy_4mEWNDI")
        compose.onNodeWithText("What song is this?").performClick()
        compose.onNodeWithText("Start").performClick()

        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Previous analysis exists").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Re-analyse").performClick()

        compose.waitUntil(5_000) { openedId != null }
        assertNotNull(openedId)
        assertNotEquals("Fresh job id must be different from previous job", prevId, openedId)
        assertEquals(listOf(openedId to UserAction.RUN), queued)

        val newJob = runBlocking { store.get(openedId!!) }!!
        assertEquals("What song is this?", newJob.userContext)
        assertEquals("youtube:Psy_4mEWNDI", newJob.normalizedUrl)

        // Both jobs exist in database
        val all = runBlocking { store.observeAll().first() }
        assertEquals(2, all.size)
    }

    @Test fun inFlightJobNavigatesDirectlyToLiveJob() {
        val inFlightId = runBlocking { store.create("https://www.instagram.com/reel/C9xQ2kLmN0p/", null, null, normalizedUrl = "instagram:C9xQ2kLmN0p") }
        runBlocking { store.update(inFlightId) { it.copy(state = JobState.ANALYSING) } }

        show("https://instagram.com/reel/C9xQ2kLmN0p/?igsh=MWx5eDds")
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { openedId != null }

        assertEquals("Must navigate directly to in-flight job", inFlightId, openedId)
        assertEquals("No duplicate job queued", 0, queued.size)
    }

    @Test fun waitingQuotaJobNavigatesDirectlyToWaitingJob() {
        val waitingId = runBlocking { store.create("https://x.com/user/status/123456", null, null, normalizedUrl = "x:123456") }
        runBlocking { store.update(waitingId) { it.copy(state = JobState.WAITING_QUOTA, retryAtEpochMs = System.currentTimeMillis() + 60_000) } }

        show("https://twitter.com/user/status/123456?s=20")
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { openedId != null }

        assertEquals("Must navigate directly to WAITING_QUOTA job", waitingId, openedId)
        assertEquals("No duplicate job queued", 0, queued.size)
    }

    @Test fun failedJobStartsFreshAttemptWithoutPrompting() {
        val failedId = runBlocking { store.create("https://www.tiktok.com/@user/video/71234567890", null, null, normalizedUrl = "tiktok:71234567890") }
        runBlocking { store.update(failedId) { it.copy(state = JobState.FAILED, failure = dev.wolzard.recon.core.model.FailureReason.NETWORK) } }

        show("https://www.tiktok.com/@user/video/71234567890")
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { openedId != null }

        assertNotNull(openedId)
        assertNotEquals("Fresh job id must be created for failed duplicate", failedId, openedId)
        assertEquals(listOf(openedId to UserAction.RUN), queued)

        // Both failed old job and new job kept in history
        val all = runBlocking { store.observeAll().first() }
        assertEquals(2, all.size)
    }
}
