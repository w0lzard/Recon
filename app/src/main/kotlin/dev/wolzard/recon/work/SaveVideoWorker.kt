package dev.wolzard.recon.work

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.wolzard.recon.data.media.MediaStoreSaver
import dev.wolzard.recon.data.media.YtDlpException
import dev.wolzard.recon.data.media.YtDlpRunner
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * WorkManager worker (D28) that re-downloads a video at a chosen quality and merges streams with FFmpeg through yt-dlp.
 * Runs in the foreground with a progress notification. Cancellable at any time.
 * On API 29+ saves to MediaStore under Movies/Recon; on API 26-28 stream-copies to [KEY_TARGET_URI].
 * Guarantees temp directory cleanup on cancel, failure, and completion.
 */
class SaveVideoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val runner: YtDlpRunner by inject()
    private val notifier: Notifier by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        return notifier.saveForegroundInfo(jobId, title, null)
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val formatId = inputData.getString(KEY_FORMAT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val targetUriStr = inputData.getString(KEY_TARGET_URI)
        val realHeight = inputData.getInt(KEY_REAL_HEIGHT, 0)
        val resStr = if (realHeight > 0) "${realHeight}p" else null

        runCatching { setForeground(notifier.saveForegroundInfo(jobId, title, 0f)) }

        val tempDir = File(applicationContext.cacheDir, "save_video/$jobId").apply {
            deleteRecursively()
            mkdirs()
        }
        val processId = "save-$jobId"

        try {
            val args = listOf(
                "-f", formatId,
                "--no-playlist",
                "-o", "${tempDir.path}/output.%(ext)s",
                "--print", "after_move:%(filepath)s",
                "--no-simulate",
            )

            val stdout = runner.run(url, args, processId) { progress ->
                runCatching {
                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                    notifier.saveForegroundInfo(jobId, title, progress)
                }
            }

            val outputFile = parseOutputFile(stdout, tempDir)
                ?: throw YtDlpException("No merged output file found in $tempDir")

            val savedUri: Uri? = if (targetUriStr != null) {
                val targetUri = Uri.parse(targetUriStr)
                MediaStoreSaver.saveToUri(applicationContext, outputFile, targetUri)
                targetUri
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStoreSaver.saveToMediaStore(applicationContext, outputFile, title)
            } else {
                throw IllegalStateException("Target URI required on Android 9 and below")
            }

            notifier.saveFinished(jobId, title, success = true, uri = savedUri, resolution = resStr)
            return Result.success(
                workDataOf(
                    KEY_SAVED_URI to savedUri?.toString(),
                    KEY_DESTINATION to "Movies/Recon",
                    KEY_RESOLUTION to resStr,
                )
            )
        } catch (e: CancellationException) {
            tempDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            notifier.saveFinished(jobId, title, success = false, uri = null)
            return Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Failed to save video")))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun parseOutputFile(stdout: String, tempDir: File): File? {
        val lines = stdout.lines()
        val candidate = lines.lastOrNull { it.isNotBlank() && !it.startsWith("ERROR") && !it.startsWith("WARNING") }
            ?.trim()?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        return candidate ?: tempDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }
    }

    companion object {
        const val KEY_JOB_ID = "jobId"
        const val KEY_URL = "url"
        const val KEY_FORMAT_ID = "formatId"
        const val KEY_TITLE = "title"
        const val KEY_TARGET_URI = "targetUri"
        const val KEY_PROGRESS = "progress"
        const val KEY_SAVED_URI = "savedUri"
        const val KEY_DESTINATION = "destination"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_REAL_HEIGHT = "realHeight"
        const val KEY_ERROR = "error"

        fun workName(jobId: String) = "save-video-$jobId"

        fun enqueue(
            context: Context,
            jobId: String,
            url: String,
            formatId: String,
            title: String,
            targetUri: String? = null,
            realHeight: Int = 0,
        ) {
            val input = workDataOf(
                KEY_JOB_ID to jobId,
                KEY_URL to url,
                KEY_FORMAT_ID to formatId,
                KEY_TITLE to title,
                KEY_TARGET_URI to targetUri,
                KEY_REAL_HEIGHT to realHeight,
            )
            val request = OneTimeWorkRequestBuilder<SaveVideoWorker>()
                .setInputData(input)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("save:$jobId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(jobId),
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context, jobId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(jobId))
        }
    }
}
