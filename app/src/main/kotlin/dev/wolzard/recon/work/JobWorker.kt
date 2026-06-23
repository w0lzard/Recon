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
