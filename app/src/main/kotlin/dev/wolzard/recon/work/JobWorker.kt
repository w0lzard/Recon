package dev.wolzard.recon.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/** Runs one job in a foreground `dataSync` worker (T12). */
class JobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val store: JobStore by inject()
    private val runner: JobRunner by inject()
    private val notifier: Notifier by inject()
    private val clock: () -> Long by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.foregroundInfo(JobQueue.idOf(inputData).orEmpty())

    override suspend fun doWork(): Result {
        val id = JobQueue.idOf(inputData) ?: return Result.failure()
        val action = JobQueue.actionOf(inputData)
        if (JobQueue.isRequeue(inputData)) {
            JobQueue.enqueue(applicationContext, id, action) // waited for quota; re-join the one-at-a-time chain
            return Result.success()
        }
        recoverAndSweep(id)
        runCatching { setForeground(notifier.foregroundInfo(id)) } // may be refused in the background; the job still runs

        try {
            runner.run(id, action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.update(id) { it.copy(state = JobState.FAILED, failure = FailureReason.UNKNOWN, failureDetail = e::class.simpleName, progress = null) }
            store.get(id)?.let { notifier.finished(it, null) }
            return Result.failure()
        }

        val job = store.get(id) ?: return Result.success()
        when (job.state) {
            JobState.WAITING_QUOTA -> JobQueue.enqueueAfter(applicationContext, id, action, (job.retryAtEpochMs ?: clock()) - clock())
            JobState.DONE, JobState.FAILED -> notifier.finished(job, summary(job))
            else -> Unit
        }
        return Result.success()
    }

    /**
     * Jobs Room shows as running belong to a run that died: reset them to QUEUED (this one runs now, others are
     * re-enqueued). Job dirs other than this job's are orphans of process death and are deleted.
     */
    private suspend fun recoverAndSweep(id: String) {
        for (stuck in store.findRunning()) {
            store.update(stuck.id) { it.copy(state = JobState.QUEUED, progress = null) }
            if (stuck.id != id) JobQueue.enqueue(applicationContext, stuck.id)
        }
        File(applicationContext.cacheDir, "jobs").listFiles()?.filter { it.name != id }?.forEach { it.deleteRecursively() }
    }

    private fun summary(job: dev.wolzard.recon.core.model.Job): String? = job.resultJson?.let {
        runCatching { json.decodeFromString(JobResult.serializer(), it).analysis.summary }.getOrNull()
    }

    private companion object { val json = Json { ignoreUnknownKeys = true } }
}
