package dev.wolzard.recon.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.JobState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJobStoreTest {
    private lateinit var db: ReconDb
    private lateinit var store: RoomJobStore
    private var now = 1_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java)
            .allowMainThreadQueries().build()
        store = RoomJobStore(db, clock = { now })
    }

    @After fun tearDown() = db.close()

    @Test fun createdJobsObservedNewestFirst() = runTest {
        val a = store.create("first https://youtu.be/a", localUri = null)
        now = 2_000
        val b = store.create("", localUri = "file:///cache/shared/b.mp4")
        val all = store.observeAll().first()
        assertEquals(listOf(b, a), all.map { it.id })
        assertEquals(JobState.QUEUED, all[0].state)
        assertEquals("file:///cache/shared/b.mp4", all[0].localUri)
        assertEquals(2_000L, all[0].createdAt)
    }

    @Test fun updateChangesStateAndUpdatedAt() = runTest {
        val id = store.create("x", null)
        now = 5_000
        store.update(id) { it.copy(state = JobState.FAILED, failure = FailureReason.NETWORK, failureDetail = "timeout", retryAtEpochMs = 9L) }
        val job = store.get(id)!!
        assertEquals(JobState.FAILED, job.state)
        assertEquals(FailureReason.NETWORK, job.failure)
        assertEquals("timeout", job.failureDetail)
        assertEquals(9L, job.retryAtEpochMs)
        assertEquals(5_000L, job.updatedAt)
        assertEquals(1_000L, job.createdAt)
        assertEquals(JobState.FAILED, store.observe(id).first()!!.state)
    }

    @Test fun jsonColumnsRoundTrip() = runTest {
        val id = store.create("x", null)
        val analysis = """{"entities":[],"summary":"s","confidence":0.0,"onScreenText":[]}"""
        store.update(id) { it.copy(analysisJson = analysis, resultJson = """{"r":1}""", progress = 0.5f) }
        val job = store.get(id)!!
        assertEquals(analysis, job.analysisJson)
        assertEquals("""{"r":1}""", job.resultJson)
        assertEquals(0.5f, job.progress!!, 0f)
    }

    @Test fun findRunningReturnsOnlyRunningStates() = runTest {
        val ids = JobState.entries.associateWith { s -> store.create(s.name, null).also { id -> store.update(id) { it.copy(state = s) } } }
        val running = store.findRunning().map { it.state }.toSet()
        assertEquals(setOf(JobState.DOWNLOADING, JobState.ANALYSING, JobState.RESEARCHING), running)
        assertNull(store.get("missing"))
        assertEquals(JobState.entries.size, ids.size)
    }
}
