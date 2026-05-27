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
            val saved = job.analysisJson?.takeIf { action != UserAction.TRY_SHARPER }
                ?.let { runCatching { json.decodeFromString(SavedAnalysis.serializer(), it) }.getOrNull() }
            val analysis = saved ?: analyse(job, dir, action, budget)
            if (saved == null) store.update(jobId) { it.copy(analysisJson = json.encodeToString(SavedAnalysis.serializer(), analysis)) }

            val lang = language()
            val models = mapOf("analyse" to analysis.model)
            val result = if (analysis.result.entities.isEmpty()) {
                JobResult(analysis.result, emptyList(), emptyList(), null, analysis.path, models) // valid "nothing to look up"
            } else {
                store.update(jobId) { it.copy(state = JobState.RESEARCHING, progress = null) }
                val ctx = ResearchContext(analysis.result.summary, lang.display, lang.tag)
                val out = research.run(analysis.result.entities, ctx, budgetLeft = budget.remaining > 0)
                JobResult(
                    analysis.result, out.findings, out.sources, out.searchSuggestionsHtml, analysis.path,
                    models + listOfNotNull(out.model?.let { "research" to it }), out.degraded,
                )
            }
            store.update(jobId) {
                it.copy(state = JobState.DONE, resultJson = json.encodeToString(JobResult.serializer(), result),
                    failure = null, failureDetail = null, retryAtEpochMs = null, progress = null)
            }
            finished = true
        } catch (e: CancellationException) {
            throw e // the worker resumes or the user cancelled; the job dir still goes in finally
        } catch (e: AnalyzerException) {
            fail(jobId, e.reason, e.message, e.retryAtEpochMs)
            finished = true
        } catch (e: Exception) {
            fail(jobId, FailureReason.UNKNOWN, "${e::class.simpleName}: ${e.message}", null)
            finished = true
        } finally {
            dir.deleteRecursively()
            if (finished) deleteSharedCopy(job.localUri)
        }
    }

    private suspend fun analyse(job: Job, dir: File, action: UserAction, budget: CallBudget): SavedAnalysis {
        val url = LinkRouter.extractUrl(job.sharedText)
        val local = job.localUri
        if (url == null && local == null) throw AnalyzerException(FailureReason.UNSUPPORTED_LINK)
        val lang = language().display

        var lastError: AnalyzerException? = null
        if (local == null) {
            val resolved = if (LinkRouter.needsResolving(url!!)) resolver.resolve(url) else url
            store.update(job.id) { it.copy(url = resolved) }
            if (LinkRouter.classify(resolved) is Route.YouTube && analyzer.supportsYouTubeUrl && action != UserAction.TRY_SHARPER) {
                store.update(job.id) { it.copy(state = JobState.ANALYSING) }
                when (val r = attempt(budget, timeouts.pathA) { analyzer.analyze(AnalysisInput.YouTubeUrl(resolved), lang) }) {
                    is Attempt.Ok -> return SavedAnalysis(r.analysis.result, r.analysis.model, "youtube-url")
                    is Attempt.Failed -> lastError = r.error // falls through to the download path
                }
            }
            return analyseDownload(job, dir, resolved, fetchers.link, action, budget, lang, lastError)
        }
        return analyseDownload(job, dir, local, fetchers.sharedFile, action, budget, lang, null)
    }

    private suspend fun analyseDownload(
        job: Job, dir: File, source: String, fetcher: MediaFetcher, action: UserAction,
        budget: CallBudget, lang: String, previous: AnalyzerException?,
    ): SavedAnalysis {
        var lastError = previous
        store.update(job.id) { it.copy(state = JobState.DOWNLOADING, progress = 0f) }
        val quality = if (action == UserAction.TRY_SHARPER) FetchQuality.SHARPER else FetchQuality.DEFAULT
        var media = fetch(job.id, fetcher, source, dir, quality, null)

        // Path A: a muxed file ≤ 50 MB. YouTube's two-file download (separate audio) goes straight to Path B.
        if (media.audio == null && media.video.length() <= timeouts.maxPathABytes) {
            if (!budget.canSpend()) throw lastError ?: AnalyzerException(FailureReason.UNKNOWN, message = "call budget used up")
            store.update(job.id) { it.copy(state = JobState.ANALYSING, progress = null) }
            when (val r = attempt(budget, timeouts.pathA) { analyzer.analyze(AnalysisInput.VideoFile(media.video, mime(media.video)), lang) }) {
                is Attempt.Ok -> return SavedAnalysis(r.analysis.result, r.analysis.model, "A")
                is Attempt.Failed -> lastError = r.error
            }
        }

        // Path B: frames + audio. Zero frames is never a success: one other-format re-download for links, then fail.
