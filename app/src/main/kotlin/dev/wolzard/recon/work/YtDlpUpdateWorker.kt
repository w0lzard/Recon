package dev.wolzard.recon.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.wolzard.recon.data.media.YtDlp
import java.util.concurrent.TimeUnit

/** Keeps extractors working between Recon releases (Distribution item 7). Failure is logged only. */
class YtDlpUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = runCatching { YtDlp.update(applicationContext) }
        val status = result.getOrNull()
        if (status != null) Log.i(TAG, "yt-dlp update: $status") else Log.w(TAG, "yt-dlp update failed; the current version stays in use", result.exceptionOrNull())
        return Result.success()
    }

    companion object {
        private const val TAG = "YtDlpUpdate"

        /** Unique periodic 24 h on any network; the first run happens right after the first enqueue (app start). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ytdlp-update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
