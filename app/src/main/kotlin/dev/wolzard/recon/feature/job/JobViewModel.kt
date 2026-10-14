package dev.wolzard.recon.feature.job

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.core.model.UserContext
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.mvi.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** User actions on a job. Requeueing goes through [enqueue] (JobQueue in the app); picking makes no AI call. */
/** [cancelWork] drops the job's queued/delayed work and its shared copy (WorkManager + files in the app). */
class JobActions(private val store: JobStore, private val cancelWork: (Job) -> Unit = {}, private val enqueue: (String, UserAction) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requeue(id: String, action: UserAction) {
        store.update(id) { it.copy(state = JobState.QUEUED, failure = null, failureDetail = null, retryAtEpochMs = null, progress = null) }
        enqueue(id, action)
    }

    /**
     * D23 Start/Skip: validates the link again (it came from a share or paste, possibly long ago), then creates and
     * queues the job with the cleaned context. Null = the link is not usable and nothing was created.
     */
    suspend fun start(shared: String, context: String?, preview: LinkPreview? = null): String? {
        val link = LinkRouter.validLink(shared) ?: return null
        val id = store.create(link, null, UserContext.clean(context))
        // D26: kept for Home and the Job screen; the thumbnail only if https.
        if (preview != null) store.update(id) {
            it.copy(videoTitle = preview.title, videoDurationSec = preview.durationSec, thumbnailUrl = preview.thumbnailUrl?.takeIf { u -> u.startsWith("https://") })
        }
        enqueue(id, UserAction.RUN)
        return id
    }

    /** Share sheet: a shared video file, already copied, starts with its optional context. */
    suspend fun startFile(localUri: String, sharedText: String, context: String?): String {
        val id = store.create(sharedText, localUri, UserContext.clean(context))
        enqueue(id, UserAction.RUN)
        return id
    }

    /**
     * Cancel job / Delete (design 1f, 1g, the result's overflow menu): the job is removed, not kept as failed.
     * Cancelling a queued or running job's work inside the one-at-a-time chain also cancels the jobs queued behind it,
     * so every QUEUED job is queued again (JobWorker skips one that already finished).
     */
    suspend fun cancel(id: String): Boolean {
        val job = store.get(id) ?: return false
        val active = job.state != JobState.DONE && job.state != JobState.FAILED
        if (active) cancelWork(job)
        store.delete(id)
        if (active && job.state != JobState.WAITING_QUOTA) {
            store.observeAll().first().filter { it.state == JobState.QUEUED }.asReversed().forEach { enqueue(it.id, UserAction.RUN) } // oldest first
        }
        return true
    }

    /** r2: the user picks which GitHub candidate the video meant (null = none of them). */
    suspend fun pick(id: String, findingIndex: Int, repo: GhRepo?) = store.update(id) { job ->
        val r = job.resultJson?.let { json.decodeFromString(JobResult.serializer(), it) } ?: return@update job
        val f = r.findings.getOrNull(findingIndex) ?: return@update job
        val picked = if (repo == null) f.copy(verdict = Verdict.NOT_FOUND, candidates = emptyList())
        else f.copy(
            title = repo.fullName, primaryLink = repo.htmlUrl.takeIf { it.startsWith("https://github.com/") },
            stars = repo.stars, whatItIs = repo.description ?: f.whatItIs, candidates = emptyList(), verdict = Verdict.BEST_MATCH,
        )
        val findings = r.findings.toMutableList().also { it[findingIndex] = picked }
        job.copy(resultJson = json.encodeToString(JobResult.serializer(), r.copy(findings = findings)))
    }

    fun result(job: Job?): JobResult? = job?.resultJson?.let { runCatching { json.decodeFromString(JobResult.serializer(), it) }.getOrNull() }
}

class JobViewModel(private val id: String, store: JobStore, private val actions: JobActions) :
    MviViewModel<JobViewState, JobIntent, JobEffect>(JobViewState()) {
    private companion object { val SIGN_IN_FAILURES = setOf(FailureReason.API_KEY_MISSING, FailureReason.API_KEY_INVALID) }

    init {
        viewModelScope.launch {
            store.observe(id).collect { job -> setState { JobViewState(job, actions.result(job)) } }
        }
    }

    override fun onIntent(intent: JobIntent) {
        when (intent) {
            JobIntent.SignIn -> send(JobEffect.NavigateToSignIn)
            JobIntent.OpenSettings -> send(JobEffect.NavigateToSettings)
            JobIntent.Back -> send(JobEffect.NavigateBack)
            is JobIntent.Open -> send(JobEffect.OpenUrl(intent.url))
            else -> viewModelScope.launch {
                when (intent) {
                    JobIntent.Retry -> actions.requeue(id, UserAction.RUN)
                    // D25: a job that stopped on sign-in runs again with its saved link and context; a shared file's copy is gone.
                    JobIntent.SignedInAgain -> state.value.job
                        ?.takeIf { it.state == JobState.FAILED && it.failure in SIGN_IN_FAILURES && it.localUri == null }
                        ?.let { actions.requeue(id, UserAction.RUN) }
                    JobIntent.CheckAgain, JobIntent.LookUp -> actions.requeue(id, UserAction.RESEARCH_ONLY)
                    JobIntent.TrySharper -> actions.requeue(id, UserAction.TRY_SHARPER)
                    is JobIntent.Pick -> actions.pick(id, intent.findingIndex, intent.repo)
                    JobIntent.Cancel -> { actions.cancel(id); send(JobEffect.NavigateBack) }
                    else -> Unit
                }
            }
        }
    }
}
