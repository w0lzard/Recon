package dev.wolzard.recon.feature.job

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.mvi.MviViewModel
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

    /** Only a WAITING_QUOTA job can be cancelled; it is removed, not kept as failed. */
    suspend fun cancel(id: String): Boolean {
        val job = store.get(id)?.takeIf { it.state == JobState.WAITING_QUOTA } ?: return false
        cancelWork(job)
        store.delete(id)
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
