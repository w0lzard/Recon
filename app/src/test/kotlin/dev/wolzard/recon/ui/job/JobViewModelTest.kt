package dev.wolzard.recon.ui.job

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.data.db.ReconDb
import dev.wolzard.recon.data.db.RoomJobStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).allowMainThreadQueries().build()
    private val store = RoomJobStore(db)
    private val queued = mutableListOf<Pair<String, UserAction>>()
    @After fun close() = db.close()

    private val repos = listOf(
        GhRepo("HEJustinSun/my-girlfriend-jingtian-latex", "https://github.com/HEJustinSun/my-girlfriend-jingtian-latex", "LaTeX for her", 3),
        GhRepo("fuzzland/girlfriend", "https://github.com/fuzzland/girlfriend", null, 1),
    )

    private suspend fun jobWithPicker(): String {
        val e = Entity(EntityType.REPO, "he-justinsun-s-girlfriend-jinchin-latex", null, Evidence.SPOKEN, "A LaTeX repo.")
        val f = Finding(e, "github", Verdict.BEST_MATCH, e.name, e.saidInVideo, emptyList(), "", "", null, null, Basis.GITHUB_API, repos)
        val r = JobResult(AnalysisResult(listOf(e), "s", 0.5), listOf(f), emptyList(), null, "youtube-url")
        val id = store.create("https://youtu.be/yeNPwz5QcPw", null)
        store.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), r)) }
        return id
    }

    private fun result(json: String?) = Json.decodeFromString(JobResult.serializer(), json!!)

    @Test fun pickingACandidateFillsLinkWithoutAnyCall() = runBlocking {
        val id = jobWithPicker()
        JobActions(store) { i, a -> queued += i to a }.pick(id, 0, repos[0])
        val f = result(store.get(id)!!.resultJson).findings.single()
        assertEquals("HEJustinSun/my-girlfriend-jingtian-latex", f.title)
        assertEquals("https://github.com/HEJustinSun/my-girlfriend-jingtian-latex", f.primaryLink)
        assertEquals(3, f.stars)
        assertEquals("LaTeX for her", f.whatItIs)
        assertTrue(f.candidates.isEmpty())
        assertTrue(queued.isEmpty())
    }

    @Test fun noneOfTheseIsNotFound() = runBlocking {
