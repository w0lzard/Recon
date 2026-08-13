package dev.wolzard.recon.feature.context

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.feature.job.JobActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** D23: share → Start/Skip. The link is validated again at Start, and Start is the only thing that creates the job. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ContextViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).allowMainThreadQueries().build()
    private val store = RoomJobStore(db)
    private val queued = mutableListOf<Pair<String, UserAction>>()
    private val actions = JobActions(store) { id, a -> queued += id to a }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun close() { db.close(); Dispatchers.resetMain() }

    private fun vm(link: String = "https://www.instagram.com/reel/abc/") = ContextViewModel(link, actions)
    private fun jobs() = runBlocking { store.observeAll().first() }

    @Test fun startsWithTheHostAndEmptyText() {
        val s = vm().state.value
        assertEquals("instagram.com", s.host)
        assertEquals("", s.text)
        assertEquals(300, s.remaining)
    }

    @Test fun startCreatesTheJobWithTrimmedContextAndQueuesIt() {
        val v = vm()
        v.onIntent(ContextIntent.TextChanged("  What song is this?  "))
        v.onIntent(ContextIntent.Start)
        val job = jobs().single()
        assertEquals("What song is this?", job.userContext)
        assertEquals("https://www.instagram.com/reel/abc/", job.sharedText)
        assertEquals(listOf(job.id to UserAction.RUN), queued)
    }

    @Test fun skipStartsAtOnceWithNoContext() {
        val v = vm()
        v.onIntent(ContextIntent.TextChanged("typed but skipped"))
        v.onIntent(ContextIntent.Skip)
        val job = jobs().single()
        assertNull(job.userContext)
        assertEquals(1, queued.size)
    }

    @Test fun blankTextAtStartMeansNoContext() {
        val v = vm()
        v.onIntent(ContextIntent.TextChanged("   \n "))
        v.onIntent(ContextIntent.Start)
        assertNull(jobs().single().userContext)
    }

    @Test fun overLongTextIsCutToThreeHundredWhileTyping() {
        val v = vm()
        v.onIntent(ContextIntent.TextChanged("x".repeat(400)))
        assertEquals(300, v.state.value.text.length)
        v.onIntent(ContextIntent.Start)
        assertEquals(300, jobs().single().userContext!!.length)
    }

    @Test fun invalidLinkAtStartCreatesNothingAndShowsTheError() {
        for (bad in listOf("javascript:alert(1)", "not a link", "file:///data/x", "https://localhost/x")) {
            val v = vm(bad)
            v.onIntent(ContextIntent.Start)
            assertEquals(ContextCopy.INVALID_LINK, v.state.value.error)
            assertTrue(!v.state.value.starting)
        }
        assertTrue(jobs().isEmpty())
        assertTrue(queued.isEmpty())
    }

    @Test fun invalidLinkAlsoBlocksSkip() {
        vm("ftp://example.com/a").onIntent(ContextIntent.Skip)
        assertTrue(jobs().isEmpty())
    }

    @Test fun chipsFillThenAppendWithinTheLimit() {
        val v = vm()
        v.onIntent(ContextIntent.Suggest("What is this?"))
        assertEquals("What is this?", v.state.value.text)
        v.onIntent(ContextIntent.Suggest("Who is this?"))
        assertEquals("What is this? Who is this?", v.state.value.text)
    }

    @Test fun backSendsNavigateBackAndCreatesNothing() = runBlocking {
        val v = vm()
        v.onIntent(ContextIntent.Back)
        assertEquals(ContextEffect.NavigateBack, v.effects.first())
        assertTrue(jobs().isEmpty())
    }

    @Test fun startOpensTheNewJob() = runBlocking {
        val v = vm()
        v.onIntent(ContextIntent.Skip)
        assertEquals(ContextEffect.OpenJob(jobs().single().id), v.effects.first())
    }

    // --- D26: link preview

    @Test fun previewFillsTheStateWhenItArrives() {
        val p = dev.wolzard.recon.core.model.LinkPreview("Glass Harbour", 38, "https://i.ytimg.com/vi/x/hq.jpg")
        val v = ContextViewModel("https://www.instagram.com/reel/abc/", actions) { p }
        assertEquals(p, v.state.value.preview)
    }

    @Test fun startDoesNotWaitForASlowPreview() {
        val never = kotlinx.coroutines.CompletableDeferred<dev.wolzard.recon.core.model.LinkPreview?>()
        val v = ContextViewModel("https://www.instagram.com/reel/abc/", actions) { never.await() }
        v.onIntent(ContextIntent.Start)
        val job = runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (jobs().isEmpty()) kotlinx.coroutines.delay(10); jobs().single() } }
        assertNull(job.videoTitle); assertNull(job.thumbnailUrl)
        assertEquals(1, queued.size)
    }

    @Test fun startKeepsThePreviewOnTheJobButNeverAnHttpThumbnail() {
        val v = ContextViewModel("https://www.instagram.com/reel/abc/", actions) {
            dev.wolzard.recon.core.model.LinkPreview("Glass Harbour", 38, "http://cdn.example/x.jpg")
        }
        v.onIntent(ContextIntent.Skip)
        val job = runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (jobs().isEmpty() || jobs().single().videoTitle == null) kotlinx.coroutines.delay(10); jobs().single() } }
        assertEquals("Glass Harbour", job.videoTitle)
        assertEquals(38L, job.videoDurationSec)
        assertNull(job.thumbnailUrl)
    }

    // --- Share sheet: a shared video file gets the context step too

    @Test fun sharedFileStartsAJobWithItsCopyAndContext() {
        val v = ContextViewModel("look at this", actions, localUri = "/data/shared/abc/video.mp4")
        assertEquals("Shared video", v.state.value.host)
        v.onIntent(ContextIntent.TextChanged("  What song is this?  "))
        v.onIntent(ContextIntent.Start)
        val job = runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (jobs().isEmpty()) kotlinx.coroutines.delay(10); jobs().single() } }
        assertEquals("/data/shared/abc/video.mp4", job.localUri)
        assertEquals("look at this", job.sharedText)
        assertEquals("What song is this?", job.userContext)
        assertEquals(1, queued.size)
    }

    @Test fun sharedFileNeverFetchesALinkPreview() {
        var fetched = false
        ContextViewModel("", actions, localUri = "/data/shared/abc/video.mp4") { fetched = true; null }
        assertEquals(false, fetched)
    }
}
