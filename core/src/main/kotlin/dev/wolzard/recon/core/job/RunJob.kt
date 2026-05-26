package dev.wolzard.recon.core.job

import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.link.Route
import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.port.Analysis
import dev.wolzard.recon.core.port.FetchQuality
import dev.wolzard.recon.core.port.FetchedMedia
import dev.wolzard.recon.core.port.FrameSampler
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.LinkResolver
import dev.wolzard.recon.core.port.MediaFetcher
import dev.wolzard.recon.core.port.VideoAnalyzer
import dev.wolzard.recon.core.research.ResearchChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Fetchers(val link: MediaFetcher, val sharedFile: MediaFetcher)
data class Language(val display: String, val tag: String)
data class Timeouts(
    val pathA: Duration = 150.seconds,
    val pathB: Duration = 120.seconds,
    val maxPathABytes: Long = 50L * 1024 * 1024,
)

enum class UserAction {
    RUN,             // first run, Retry, or resume after process death
    RESEARCH_ONLY,   // "Check again" / "Look up": research from the saved analysis (1 call)
    TRY_SHARPER,     // D21: re-download ≤ 720p and re-analyse
}

/** analysisJson payload: lets Retry/Check again resume at RESEARCHING without paying for analysis again (D7). */
@Serializable
data class SavedAnalysis(val result: AnalysisResult, val model: String, val path: String)

/**
 * The pipeline (plan → Pipeline, State machine). Only RunJob writes `state`. At most 2 AI calls per invocation
 * (D9); every exit path deletes `jobs/<id>/`; a shared-file copy is deleted once the job is DONE or FAILED.
 */
class RunJob(
    private val store: JobStore,
    private val resolver: LinkResolver,
    private val fetchers: Fetchers,
    private val sampler: FrameSampler,
    private val analyzer: VideoAnalyzer,
    private val research: ResearchChain,
    private val language: () -> Language,
    /** Earliest retryAt when every model's quota is known to be used up; null when some model has quota. */
    private val quotaWaitUntil: () -> Long?,
    private val workRoot: File,
    private val sharedRoot: File = File(workRoot, "shared"),
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeouts: Timeouts = Timeouts(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(jobId: String, action: UserAction = UserAction.RUN) {
        val job = store.get(jobId) ?: return
        val dir = File(workRoot, "jobs/$jobId")
        var finished = false
        try {
            quotaWaitUntil()?.let { retryAt ->
                store.update(jobId) { it.copy(state = JobState.WAITING_QUOTA, retryAtEpochMs = retryAt, progress = null) }
                return
            }
            val budget = CallBudget(MAX_CALLS)
