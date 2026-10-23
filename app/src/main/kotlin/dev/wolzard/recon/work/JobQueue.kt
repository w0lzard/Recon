package dev.wolzard.recon.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.wolzard.recon.core.job.Language
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Runs one job; bound to RunJob in Koin, faked in tests. */
fun interface JobRunner {
    suspend fun run(jobId: String, action: UserAction)
}

/** D6: one job at a time, in one unique chain. A WAITING_QUOTA job waits in its own delayed work, then re-joins it. */
object JobQueue {
    const val NAME = "recon"
    private const val KEY_ID = "jobId"
    private const val KEY_ACTION = "action"
    private const val KEY_REQUEUE = "requeue"
    private const val KEY_TRIES = "tries"
    private const val BACKOFF_SECONDS = 30L
    /** Automatic tries after the first run, for FailureReason.NETWORK only. */
    const val NETWORK_RETRIES = 3

    fun tag(id: String) = "job:$id"
    fun waitName(id: String) = "recon-wait-$id"

    fun input(id: String, action: UserAction, requeue: Boolean = false, tries: Int = 0): Data =
        workDataOf(KEY_ID to id, KEY_ACTION to action.name, KEY_REQUEUE to requeue, KEY_TRIES to tries)

    fun idOf(d: Data): String? = d.getString(KEY_ID)
    fun actionOf(d: Data): UserAction = d.getString(KEY_ACTION)?.let { runCatching { UserAction.valueOf(it) }.getOrNull() } ?: UserAction.RUN
    fun isRequeue(d: Data) = d.getBoolean(KEY_REQUEUE, false)
    /** Automatic NETWORK retries already used by this job's run. */
    fun triesOf(d: Data) = d.getInt(KEY_TRIES, 0)
    /** Wait before automatic retry number [tries] + 1: 30 s, 60 s, 120 s. Spent in separate wait work, not in the chain. */
    fun retryDelayMs(tries: Int) = TimeUnit.SECONDS.toMillis(BACKOFF_SECONDS shl tries)

    fun enqueue(context: Context, id: String, action: UserAction = UserAction.RUN, tries: Int = 0) {
        val request = OneTimeWorkRequestBuilder<JobWorker>()
            .setInputData(input(id, action, tries = tries))
            // A job shared offline waits for a connection; a NETWORK failure waits in its own delayed work (JobWorker, 3 tries).
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(tag(id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Drops a job's work and Recon's copy of a shared file. A WAITING_QUOTA job only has its separate wait work.
     * A queued or running job's work sits in the APPEND chain, so cancelling it cancels every job queued after it;
     * JobActions.cancel queues those again.
     */
    fun cancel(context: Context, job: Job) {
        val wm = WorkManager.getInstance(context)
        if (job.state == JobState.WAITING_QUOTA) wm.cancelUniqueWork(waitName(job.id)) else wm.cancelAllWorkByTag(tag(job.id))
        val shared = File(context.filesDir, "shared").canonicalFile
        job.localUri?.let(::File)?.canonicalFile?.takeIf { it.path.startsWith(shared.path + File.separator) }?.let { f ->
            f.delete()
            f.parentFile?.takeIf { it != shared && it.listFiles().isNullOrEmpty() }?.delete()
        }
    }

    /** No network calls until [delayMs] has passed; then the job re-joins the main chain once. */
    fun enqueueAfter(context: Context, id: String, action: UserAction, delayMs: Long, tries: Int = 0) {
        val request = OneTimeWorkRequestBuilder<JobWorker>()
            .setInputData(input(id, action, requeue = true, tries = tries))
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(tag(id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(waitName(id), ExistingWorkPolicy.REPLACE, request)
    }
}

/** Output language for summaries and explanations = device locale (Global Constraints). */
fun deviceLanguage(): Language = Locale.getDefault().let { Language(it.displayLanguage, it.language) }
