package dev.wolzard.recon.ui.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.JobStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

