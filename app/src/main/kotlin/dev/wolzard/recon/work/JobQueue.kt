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
