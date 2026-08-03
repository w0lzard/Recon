package dev.wolzard.recon.feature.job

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Before
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class JobViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).allowMainThreadQueries().build()
    private val store = RoomJobStore(db)
    private val queued = mutableListOf<Pair<String, UserAction>>()
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun close() { db.close(); Dispatchers.resetMain() }

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
        val id = jobWithPicker()
        JobActions(store) { i, a -> queued += i to a }.pick(id, 0, null)
        val f = result(store.get(id)!!.resultJson).findings.single()
        assertEquals(Verdict.NOT_FOUND, f.verdict)
        assertNull(f.primaryLink)
    }

    @Test fun retryRequeuesAndClearsFailure() = runBlocking {
        val id = store.create("https://youtu.be/x", null)
        store.update(id) { it.copy(state = JobState.FAILED, failure = FailureReason.NETWORK, retryAtEpochMs = 5) }
        JobActions(store) { i, a -> queued += i to a }.requeue(id, UserAction.RUN)
        val j = store.get(id)!!
        assertEquals(JobState.QUEUED, j.state)
        assertNull(j.failure)
        assertEquals(listOf(id to UserAction.RUN), queued)
    }

    @Test fun cancelRemovesOnlyWaitingJobs() = runBlocking {
        val cancelled = mutableListOf<String>()
        val actions = JobActions(store, { cancelled += it.id }) { i, a -> queued += i to a }
        val waiting = store.create("https://youtu.be/w", null)
        store.update(waiting) { it.copy(state = JobState.WAITING_QUOTA, retryAtEpochMs = 5) }
        val running = store.create("https://youtu.be/r", null)
        store.update(running) { it.copy(state = JobState.ANALYSING) }

        assertTrue(actions.cancel(waiting))
        assertNull(store.get(waiting))
        assertEquals(false, actions.cancel(running))
        assertEquals(JobState.ANALYSING, store.get(running)!!.state)
        assertEquals(listOf(waiting), cancelled)
    }

    // --- MVI: intents become state or one-shot effects

    private fun vm(id: String) = JobViewModel(id, store, JobActions(store) { i, a -> queued += i to a })

    @Test fun stateFollowsTheStoredJobAndItsResult() = runBlocking {
        val id = jobWithPicker()
        val state = withTimeout(5_000) { vm(id).state.first { it.job != null } }
        assertEquals(JobState.DONE, state.job?.state)
        assertEquals(1, state.result?.findings?.size)
    }

    @Test fun navigationIntentsBecomeEffects() = runBlocking {
        val vm = vm(jobWithPicker())
        vm.onIntent(JobIntent.SignIn)
        vm.onIntent(JobIntent.OpenSettings)
        vm.onIntent(JobIntent.Back)
        vm.onIntent(JobIntent.Open("https://github.com/x/y"))
        assertEquals(JobEffect.NavigateToSignIn, vm.effects.first())
        assertEquals(JobEffect.NavigateToSettings, vm.effects.first())
        assertEquals(JobEffect.NavigateBack, vm.effects.first())
        assertEquals(JobEffect.OpenUrl("https://github.com/x/y"), vm.effects.first())
    }

    @Test fun retryRequeuesTheJob() = runBlocking {
        val id = jobWithPicker()
        vm(id).onIntent(JobIntent.Retry)
        withTimeout(5_000) { while (queued.isEmpty()) delay(10) } // Room runs the update on its own thread
        assertEquals(listOf(id to UserAction.RUN), queued)
    }

    @Test fun cancellingAWaitingJobDeletesItAndGoesBack() = runBlocking {
        val id = store.create("https://youtu.be/yeNPwz5QcPw", null)
        store.update(id) { it.copy(state = JobState.WAITING_QUOTA) }
        val vm = vm(id)
        vm.onIntent(JobIntent.Cancel)
        assertEquals(JobEffect.NavigateBack, vm.effects.first())
        assertNull(store.get(id))
    }
}
