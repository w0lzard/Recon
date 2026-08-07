package dev.wolzard.recon.core.job

import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.AiResearcher
import dev.wolzard.recon.core.port.Analysis
import dev.wolzard.recon.core.port.FetchQuality
import dev.wolzard.recon.core.port.FetchedMedia
import dev.wolzard.recon.core.port.FrameSampler
import dev.wolzard.recon.core.port.GroundingCapability
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.LinkResolver
import dev.wolzard.recon.core.port.MediaFetcher
import dev.wolzard.recon.core.port.ResearchOutput
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.port.Researcher
import dev.wolzard.recon.core.port.VideoAnalyzer
import dev.wolzard.recon.core.research.ResearchChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

class RunJobTest {
    @get:Rule val tmp = TemporaryFolder()

    // --- fakes

    private class Store : JobStore {
        val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
        val states = mutableListOf<JobState>()
        fun put(job: Job) { jobs.value = jobs.value + (job.id to job); states += job.state }
        override fun observeAll() = jobs.map { it.values.toList() }
        override fun observe(id: String) = jobs.map { it[id] }
        override suspend fun get(id: String) = jobs.value[id]
        override suspend fun create(sharedText: String, localUri: String?, userContext: String?) = error("unused")
        override suspend fun update(id: String, transform: (Job) -> Job) {
            val old = jobs.value.getValue(id)
            val new = transform(old)
            jobs.value = jobs.value + (id to new)
            if (new.state != old.state) states += new.state
        }
        override suspend fun delete(id: String) { jobs.value = jobs.value - id }
        override suspend fun findRunning() = emptyList<Job>()
    }

    private val entities = listOf(Entity(EntityType.APP, "Codeium", null, Evidence.ON_SCREEN, "An AI code tool."))
    private fun analysis(e: List<Entity> = entities) = Analysis(AnalysisResult(e, "A demo.", 0.9), "m1")

    private inner class Analyzer(vararg outcomes: Any) : VideoAnalyzer {
        private val queue = ArrayDeque(outcomes.toList())
        val inputs = mutableListOf<AnalysisInput>()
        val contexts = mutableListOf<String?>()
        override val supportsYouTubeUrl = true
        override suspend fun analyze(input: AnalysisInput, language: String, userContext: String?): Analysis {
            inputs += input
            contexts += userContext
            return when (val o = queue.removeFirstOrNull() ?: analysis()) {
                is Analysis -> o
                is Exception -> throw o
                is Long -> { delay(o); analysis() }
                else -> error(o)
            }
        }
    }

    private inner class Fetcher(private val audio: Boolean = false, private val bytes: Int = 100, private val block: CompletableDeferred<Unit>? = null) : MediaFetcher {
        val calls = mutableListOf<Pair<FetchQuality, String?>>()
        var lastDir: File? = null
        override suspend fun fetch(source: String, destDir: File, quality: FetchQuality, avoidFormatId: String?, onProgress: (Float) -> Unit): FetchedMedia {
            calls += quality to avoidFormatId
            lastDir = destDir
            destDir.mkdirs()
            val v = File(destDir, "video.mp4").apply { writeBytes(ByteArray(bytes)) }
            onProgress(0.5f)
            block?.await()
            return FetchedMedia(v, if (audio) File(destDir, "audio.m4a").apply { writeBytes(ByteArray(4)) } else null, "f${calls.size}")
        }
    }

    private class Sampler(vararg frameCounts: Int) : FrameSampler {
        private val counts = ArrayDeque(frameCounts.toList())
        var calls = 0
        override suspend fun sample(video: File, outDir: File): AnalysisInput.FramesAndAudio {
            calls++
            outDir.mkdirs()
            val n = counts.removeFirstOrNull() ?: 3
            return AnalysisInput.FramesAndAudio((0 until n).map { AnalysisInput.Frame(File(outDir, "f$it.jpg").apply { writeBytes(ByteArray(1)) }, it * 2000L) }, null)
        }
    }

    private class Keyless(override val id: String) : Researcher {
        override fun accepts(type: EntityType) = false
        override suspend fun research(entities: List<Entity>, ctx: ResearchContext) = ResearchOutput(emptyList())
    }

    private inner class Ai(var unavailable: Boolean = false) : AiResearcher {
        override val id = "knowledge"
        var calls = 0
        override suspend fun research(items: List<ResearchItem>, ctx: ResearchContext): ResearchOutput {
            calls++
            if (unavailable) throw ResearchUnavailable(123L)
            return ResearchOutput(items.map { dev.wolzard.recon.core.model.Finding(it.entity, "knowledge", Verdict.BEST_MATCH, it.entity.name, "w", emptyList(), "", "", null, null, dev.wolzard.recon.core.model.Basis.AI_KNOWLEDGE) }, model = "m2")
        }
    }

    private val store = Store()
    private val ai = Ai()
    private var waitUntil: Long? = null

    private fun runJob(
        analyzer: VideoAnalyzer,
        link: MediaFetcher = Fetcher(),
        shared: MediaFetcher = Fetcher(),
        sampler: FrameSampler = Sampler(),
        timeouts: Timeouts = Timeouts(pathA = 150.seconds, pathB = 120.seconds, maxPathABytes = 1_000),
    ) = RunJob(
        store = store,
        resolver = object : LinkResolver { override suspend fun resolve(url: String) = url },
        fetchers = Fetchers(link, shared),
        sampler = sampler,
        analyzer = analyzer,
        research = ResearchChain(Keyless("github"), Keyless("wikipedia"), ai, null) { GroundingCapability.UNAVAILABLE },
        language = { Language("English", "en") },
        quotaWaitUntil = { waitUntil },
        workRoot = tmp.root,
        clock = { 1_000L },
        timeouts = timeouts,
    )

    private fun job(text: String = "look https://www.instagram.com/reel/abc/", localUri: String? = null, analysisJson: String? = null, userContext: String? = null) =
        Job("j1", text, localUri = localUri, analysisJson = analysisJson, userContext = userContext, createdAt = 0, updatedAt = 0).also { store.put(it) }

    private suspend fun result(): JobResult = Json.decodeFromString(JobResult.serializer(), store.get("j1")!!.resultJson!!)
    private suspend fun state() = store.get("j1")!!.state
    private fun jobDir() = File(tmp.root, "jobs/j1")

    // --- routes and fallbacks

    @Test fun youtubeGoesStraightToAnalysisWithoutDownload() = runTest {
        job("https://youtu.be/Psy_4mEWNDI")
        val link = Fetcher()
        val a = Analyzer()
        runJob(a, link = link)("j1")
        assertEquals(listOf(JobState.QUEUED, JobState.ANALYSING, JobState.RESEARCHING, JobState.DONE), store.states)
        assertTrue(link.calls.isEmpty())
        assertTrue(a.inputs.single() is AnalysisInput.YouTubeUrl)
        assertEquals("youtube-url", result().path)
        assertEquals(mapOf("analyse" to "m1", "research" to "m2"), result().models)
    }

    @Test fun youtubeNetworkFailureFallsBackToDownloadAndPathB() = runTest {
        job("https://youtu.be/Psy_4mEWNDI")
        val link = Fetcher(audio = true)
        val a = Analyzer(AnalyzerException(FailureReason.NETWORK))
        runJob(a, link = link)("j1")
        assertEquals(1, link.calls.size)
        val b = a.inputs[1] as AnalysisInput.FramesAndAudio
        assertEquals("audio.m4a", b.audio!!.name)                         // YouTube's separate m4a feeds Path B
        assertEquals(JobState.DONE, state())
        assertEquals("B", result().path)
    }

    @Test fun pathAFailureFallsBackToPathBAndSkipsResearchForBudget() = runTest {
        job()
        val a = Analyzer(AnalyzerException(FailureReason.UNKNOWN))
        runJob(a)("j1")
        assertTrue(a.inputs[0] is AnalysisInput.VideoFile)
        assertTrue(a.inputs[1] is AnalysisInput.FramesAndAudio)
        assertEquals("B", result().path)
        assertEquals(0, ai.calls)                                          // budget: 2 analysis calls used
        assertTrue(result().degraded.containsKey("budget"))
    }

    @Test fun pathATimeoutFallsBackToPathB() = runTest {
        job()
        val a = Analyzer(200_000L)
        runJob(a)("j1")
        assertEquals("B", result().path)
    }

    @Test fun pathAQuotaFailsWithoutTryingPathB() = runTest {
        job()
        val a = Analyzer(AnalyzerException(FailureReason.QUOTA_EXCEEDED, retryAtEpochMs = 5_000))
        runJob(a)("j1")
        assertEquals(1, a.inputs.size)
        val j = store.get("j1")!!
        assertEquals(JobState.FAILED, j.state)
        assertEquals(FailureReason.QUOTA_EXCEEDED, j.failure)
        assertEquals(5_000L, j.retryAtEpochMs)
    }

    @Test fun noQuotaWaitBackendNeverWritesWaitingQuota() = runTest {
        // The web backend wires quotaWaitUntil = { null } (AppModule): a usage-limit error must end in FAILED, never WAITING_QUOTA.
        job()
        waitUntil = null
        runJob(Analyzer(AnalyzerException(FailureReason.QUOTA_EXCEEDED)))("j1")
        assertEquals(JobState.FAILED, state())
        assertEquals(FailureReason.QUOTA_EXCEEDED, store.get("j1")!!.failure)
        assertTrue("states were ${store.states}", JobState.WAITING_QUOTA !in store.states)
    }

    @Test fun allModelsWithoutQuotaWaitWithoutAnyCall() = runTest {
        job()
        waitUntil = 9_000L
        val a = Analyzer()
        runJob(a)("j1")
        assertEquals(0, a.inputs.size)
        assertEquals(JobState.WAITING_QUOTA, state())
        assertEquals(9_000L, store.get("j1")!!.retryAtEpochMs)
    }

    @Test fun largeFileSkipsPathA() = runTest {
        job()
        val a = Analyzer()
        runJob(a, link = Fetcher(bytes = 5_000))("j1")
        assertTrue(a.inputs.single() is AnalysisInput.FramesAndAudio)
        assertEquals(1, ai.calls)
    }

    @Test fun zeroFramesRetriesOnceInAnotherFormatThenFails() = runTest {
        job()
        val link = Fetcher(bytes = 5_000)
        val sampler = Sampler(0, 0)
        val a = Analyzer()
        runJob(a, link = link, sampler = sampler)("j1")
        assertEquals(listOf(FetchQuality.DEFAULT to null, FetchQuality.OTHER_FORMAT to "f1"), link.calls)
        assertEquals(2, sampler.calls)
        assertEquals(0, a.inputs.size)
        assertEquals(FailureReason.VIDEO_UNREADABLE, store.get("j1")!!.failure)
        assertFalse(JobState.DONE in store.states)
    }

    @Test fun zeroFramesThenFramesAfterRetrySucceeds() = runTest {
        job()
        runJob(Analyzer(), link = Fetcher(bytes = 5_000), sampler = Sampler(0, 4))("j1")
        assertEquals(JobState.DONE, state())
    }

    @Test fun sharedFileWithZeroFramesFailsWithoutRetry() = runTest {
        val sharedRoot = tmp.newFolder("shared")
        val copy = File(sharedRoot, "j1.mp4").apply { writeBytes(ByteArray(5_000)) }
        job(text = "", localUri = copy.path)
        val shared = Fetcher(bytes = 5_000)
        runJob(Analyzer(), shared = shared, sampler = Sampler(0))("j1")
        assertEquals(1, shared.calls.size)
        assertEquals(FailureReason.VIDEO_UNREADABLE, store.get("j1")!!.failure)
        assertFalse(copy.exists())                                         // the shared copy is gone after FAILED
    }

    @Test fun trySharperDownloadsAt720p() = runTest {
        job()
        val link = Fetcher()
        runJob(Analyzer(), link = link)("j1", UserAction.TRY_SHARPER)
        assertEquals(FetchQuality.SHARPER, link.calls.single().first)
    }

    @Test fun noUrlIsUnsupportedLink() = runTest {
        job("just text, no link")
        runJob(Analyzer())("j1")
        assertEquals(FailureReason.UNSUPPORTED_LINK, store.get("j1")!!.failure)
    }

    // --- research handling

    @Test fun r10NoEntitiesIsDoneWithOneAiCallInTotal() = runTest {
        job("https://youtu.be/U-34B7DL95s")
        val a = Analyzer(analysis(emptyList()))
        runJob(a)("j1")
        assertEquals(JobState.DONE, state())
        assertEquals(1, a.inputs.size)
        assertEquals(0, ai.calls)
        assertTrue(result().findings.isEmpty())
        assertFalse(JobState.RESEARCHING in store.states)
    }

    @Test fun degradedResearchIsStillDone() = runTest {
        job("https://youtu.be/x")
        ai.unavailable = true
        runJob(Analyzer())("j1")
        assertEquals(JobState.DONE, state())
        assertEquals(123L, result().degraded["knowledge"])
        assertEquals(Verdict.NOT_CHECKED, result().findings.single().verdict)
    }

    @Test fun savedAnalysisSkipsAnalysisAndStartsAtResearching() = runTest {
        val saved = Json.encodeToString(SavedAnalysis.serializer(), SavedAnalysis(analysis().result, "m1", "A"))
        job(analysisJson = saved)
        val a = Analyzer()
        runJob(a)("j1", UserAction.RESEARCH_ONLY)
        assertEquals(0, a.inputs.size)
        assertEquals(listOf(JobState.QUEUED, JobState.RESEARCHING, JobState.DONE), store.states)
        assertEquals(1, ai.calls)
        assertEquals("A", result().path)
    }

    @Test fun budgetNeverExceedsTwoAiCalls() = runTest {
        val cases: List<Pair<String, Array<Any>>> = listOf(
            "https://youtu.be/a" to arrayOf(),
            "https://youtu.be/a" to arrayOf(AnalyzerException(FailureReason.NETWORK), AnalyzerException(FailureReason.UNKNOWN)),
            "https://www.instagram.com/reel/a/" to arrayOf(AnalyzerException(FailureReason.UNKNOWN)),
            "https://www.instagram.com/reel/a/" to arrayOf(200_000L),
        )
        for ((text, outcomes) in cases) {
            ai.calls = 0
            job(text)
            val a = Analyzer(*outcomes)
            runJob(a, link = Fetcher(audio = text.contains("youtu")))("j1")
            assertTrue("$text ${outcomes.toList()}: ${a.inputs.size} + ${ai.calls}", a.inputs.size + ai.calls <= 2)
        }
    }

    // --- errors and cleanup

    @Test fun unexpectedExceptionIsUnknownWithClassName() = runTest {
        job("https://youtu.be/x")
        runJob(Analyzer(IllegalStateException("boom")))("j1")
        val j = store.get("j1")!!
        assertEquals(FailureReason.UNKNOWN, j.failure)
        assertTrue(j.failureDetail!!.contains("IllegalStateException"))
    }

    @Test fun workDirDeletedOnDoneAndFailed() = runTest {
        job()
        val link = Fetcher()
        runJob(Analyzer(), link = link)("j1")
        assertEquals(jobDir(), link.lastDir)
        assertFalse(jobDir().exists())
        job()
        runJob(Analyzer(AnalyzerException(FailureReason.API_KEY_INVALID)), link = link)("j1")
        assertEquals(JobState.FAILED, state())
        assertFalse(jobDir().exists())
    }

    @Test fun workDirDeletedWhenCancelledMidDownload() = runTest {
        job()
        val gate = CompletableDeferred<Unit>()
        val link = Fetcher(block = gate)
        val run = launch { runJob(Analyzer(), link = link)("j1") }
        testScheduler.advanceUntilIdle()
        assertTrue(jobDir().exists())
        assertEquals(JobState.DOWNLOADING, state())
        run.cancel(CancellationException("user cancelled"))
        run.join()
        assertFalse(jobDir().exists())
    }

    @Test fun progressIsReportedWhileDownloading() = runTest {
        job()
        runJob(Analyzer())("j1")
        assertNull(store.get("j1")!!.progress)   // cleared when the job ends
    }

    // --- D23: user context rides in the same analysis call

    @Test fun contextGoesIntoTheSingleAnalysisCallAndBudgetIsUnchanged() = runTest {
        job(userContext = "What song is this?")
        val a = Analyzer()
        runJob(a)("j1")
        assertEquals(listOf<String?>("What song is this?"), a.contexts)   // one analysis call, one context
        assertEquals(1, ai.calls)                                          // research stays one call: no extra AI call
        assertEquals(JobState.DONE, state())
    }

    @Test fun noContextMeansNullInTheCall() = runTest {
        job()
        val a = Analyzer()
        runJob(a)("j1")
        assertEquals(listOf<String?>(null), a.contexts)
    }

    @Test fun contextIsReusedOnPathBFallbackWithinTheTwoCallBudget() = runTest {
        job(userContext = "Who is this?")
        val a = Analyzer(AnalyzerException(FailureReason.NETWORK))
        runJob(a)("j1")
        assertEquals(listOf<String?>("Who is this?", "Who is this?"), a.contexts)
        assertEquals(0, ai.calls)   // both calls spent on analysis: research skipped, as before
    }

    @Test fun trySharperReusesTheStoredContext() = runTest {
        job(userContext = "Is this true?")
        val a = Analyzer()
        runJob(a)("j1", UserAction.TRY_SHARPER)
        assertEquals(listOf<String?>("Is this true?"), a.contexts)
    }

    @Test fun retryAfterFailureReusesTheStoredContext() = runTest {
        job(userContext = "Where is this?")
        runJob(Analyzer(AnalyzerException(FailureReason.MODEL_BUSY)))("j1")
        assertEquals(JobState.FAILED, state())
        val a = Analyzer()
        runJob(a)("j1")   // Retry = RUN on the same job row
        assertEquals(listOf<String?>("Where is this?"), a.contexts)
        assertEquals("Where is this?", store.get("j1")!!.userContext)
    }
}
