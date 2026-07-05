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
