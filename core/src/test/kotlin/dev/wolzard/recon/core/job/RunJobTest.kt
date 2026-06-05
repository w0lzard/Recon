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
        override suspend fun create(sharedText: String, localUri: String?) = error("unused")
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
        override val supportsYouTubeUrl = true
        override suspend fun analyze(input: AnalysisInput, language: String): Analysis {
            inputs += input
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

    private fun job(text: String = "look https://www.instagram.com/reel/abc/", localUri: String? = null, analysisJson: String? = null) =
        Job("j1", text, localUri = localUri, analysisJson = analysisJson, createdAt = 0, updatedAt = 0).also { store.put(it) }

    private suspend fun result(): JobResult = Json.decodeFromString(JobResult.serializer(), store.get("j1")!!.resultJson!!)
    private suspend fun state() = store.get("j1")!!.state
    private fun jobDir() = File(tmp.root, "jobs/j1")

    // --- routes and fallbacks

    @Test fun youtubeGoesStraightToAnalysisWithoutDownload() = runTest {
