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
