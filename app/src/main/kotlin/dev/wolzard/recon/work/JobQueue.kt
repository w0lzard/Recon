package dev.wolzard.recon.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.wolzard.recon.core.job.Language
import dev.wolzard.recon.core.job.UserAction
import dev.wolzard.recon.core.model.Job
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

    fun tag(id: String) = "job:$id"
    fun waitName(id: String) = "recon-wait-$id"

    fun input(id: String, action: UserAction, requeue: Boolean = false): Data =
        workDataOf(KEY_ID to id, KEY_ACTION to action.name, KEY_REQUEUE to requeue)

    fun idOf(d: Data): String? = d.getString(KEY_ID)
    fun actionOf(d: Data): UserAction = d.getString(KEY_ACTION)?.let { runCatching { UserAction.valueOf(it) }.getOrNull() } ?: UserAction.RUN
    fun isRequeue(d: Data) = d.getBoolean(KEY_REQUEUE, false)

    fun enqueue(context: Context, id: String, action: UserAction = UserAction.RUN) {
        val request = OneTimeWorkRequestBuilder<JobWorker>()
            .setInputData(input(id, action))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(tag(id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Drops a WAITING_QUOTA job's delayed work and Recon's copy of a shared file.
     * Only the separate wait work: cancelling inside the APPEND chain would cancel every job queued after it.
     */
    fun cancel(context: Context, job: Job) {
        WorkManager.getInstance(context).cancelUniqueWork(waitName(job.id))
        val shared = File(context.filesDir, "shared").canonicalFile
        job.localUri?.let(::File)?.canonicalFile?.takeIf { it.path.startsWith(shared.path + File.separator) }?.let { f ->
            f.delete()
            f.parentFile?.takeIf { it != shared && it.listFiles().isNullOrEmpty() }?.delete()
        }
    }

    /** No network calls until [delayMs] has passed; then the job re-joins the main chain once. */
    fun enqueueAfter(context: Context, id: String, action: UserAction, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<JobWorker>()
            .setInputData(input(id, action, requeue = true))
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(tag(id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(waitName(id), ExistingWorkPolicy.REPLACE, request)
    }
}

/** Output language for summaries and explanations = device locale (Global Constraints). */
fun deviceLanguage(): Language = Locale.getDefault().let { Language(it.displayLanguage, it.language) }
