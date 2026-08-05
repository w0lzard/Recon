package dev.wolzard.recon.work

import kotlinx.coroutines.flow.first
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class JobWorkerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ReconDb
    private lateinit var store: JobStore
    private val seenStates = mutableListOf<JobState>()
    private var behaviour: suspend (String) -> Unit = { id -> store.update(id) { it.copy(state = JobState.DONE) } }
    private val now = 1_000_000L

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        db = Room.inMemoryDatabaseBuilder(ctx, ReconDb::class.java).allowMainThreadQueries().build()
        store = RoomJobStore(db, clock = { now })
        startKoin {
            modules(module {
                single { store }
                single<JobRunner> { JobRunner { id, _ -> seenStates += store.get(id)!!.state; behaviour(id) } }
                single { Notifier(ctx) }
                single<() -> Long> { { now } }
            })
        }
    }

    @After fun tearDown() { stopKoin(); db.close() }

    private fun worker(id: String, action: UserAction = UserAction.RUN) =
        TestListenableWorkerBuilder<JobWorker>(ctx).setInputData(JobQueue.input(id, action)).build()

    @Test fun jobLeftRunningIsResetToQueuedThenRunToDone() = runBlocking {
        val id = store.create("https://youtu.be/x", null)
        store.update(id) { it.copy(state = JobState.ANALYSING) }
        val result = worker(id).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(JobState.QUEUED), seenStates)
        assertEquals(JobState.DONE, store.get(id)!!.state)
    }

    @Test fun runnerExceptionIsFailedUnknownAndWorkerFailure() = runBlocking {
        val id = store.create("https://youtu.be/x", null)
        behaviour = { throw IllegalStateException("boom") }
        assertEquals(ListenableWorker.Result.failure(), worker(id).doWork())
        val job = store.get(id)!!
        assertEquals(JobState.FAILED, job.state)
        assertEquals(FailureReason.UNKNOWN, job.failure)
    }

    @Test fun startupSweepDeletesOrphanDirsAndKeepsThisJobsDir() = runBlocking {
        val id = store.create("https://youtu.be/x", null)
        val orphan = File(ctx.cacheDir, "jobs/orphan-job").apply { mkdirs(); File(this, "video.mp4").writeText("x") }
        val own = File(ctx.cacheDir, "jobs/$id").apply { mkdirs() }
        behaviour = { assertTrue(own.exists()); store.update(it) { j -> j.copy(state = JobState.DONE) } }
        worker(id).doWork()
        assertFalse(orphan.exists())
    }

    @Test fun waitingQuotaJobIsReenqueuedWithDelayUntilRetryAt() = runBlocking {
        val id = store.create("https://youtu.be/x", null)
        behaviour = { store.update(it) { j -> j.copy(state = JobState.WAITING_QUOTA, retryAtEpochMs = now + 60_000) } }
        worker(id).doWork()
        val info = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(JobQueue.waitName(id)).get().single()
        assertEquals(60_000L, info.initialDelayMillis)
    }

    @Test fun textShareWithoutLinkCreatesFailingJobAndEnqueuesWork() = runBlocking {
        val intake = ShareIntake(ctx, store, ContentUriFetcher(ctx))
        val id = (intake.accept(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "no link in here")) as ShareOutcome.Started).jobId
        assertEquals("no link in here", store.get(id)!!.sharedText)
        val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(JobQueue.NAME).get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(JobQueue.tag(id)))
    }

    @Test fun textShareWithValidLinkWaitsForContextAndCreatesNothing() = runBlocking {
        val intake = ShareIntake(ctx, store, ContentUriFetcher(ctx))
        val out = intake.accept(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "look https://youtu.be/abc!"))
        assertEquals(ShareOutcome.NeedsContext("https://youtu.be/abc"), out)
        assertTrue(store.observeAll().first().isEmpty())
        assertTrue(WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(JobQueue.NAME).get().isEmpty())
    }

    @Test fun nonShareIntentIsIgnored() = runBlocking {
        assertEquals(null, ShareIntake(ctx, store, ContentUriFetcher(ctx)).accept(Intent(Intent.ACTION_MAIN)))
    }

    @Test @Config(qualifiers = "hi") fun languageIsTheDeviceLocale() {
        val lang = deviceLanguage()
        assertEquals("hi", lang.tag)
        assertEquals(java.util.Locale.forLanguageTag("hi").displayLanguage, lang.display)
    }
}
