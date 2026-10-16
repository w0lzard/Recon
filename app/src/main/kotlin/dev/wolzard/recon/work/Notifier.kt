package dev.wolzard.recon.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import dev.wolzard.recon.MainActivity
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState

class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Video lookups", NotificationManager.IMPORTANCE_LOW))
        }
    }

    fun foregroundInfo(jobId: String): ForegroundInfo {
        channel()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Recon is looking into a video")
            .setOngoing(true)
            .setContentIntent(open(jobId))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(ONGOING_ID, n)
    }

    /** Foreground notification for high-quality video save and merge. */
    fun saveForegroundInfo(jobId: String, title: String, progress: Float?): ForegroundInfo {
        channel()
        val text = if (progress != null) "${(progress * 100).toInt()}%" else "Downloading and merging..."
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (title.isNotEmpty()) "Saving: ${title.take(30)}" else "Saving video")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open(jobId))

        if (progress != null) {
            b.setProgress(100, (progress * 100).toInt(), false)
        } else {
            b.setProgress(0, 0, true)
        }
        val notifId = (SAVE_ONGOING_PREFIX + jobId).hashCode()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, b.build())
    }

    /** Save completed notification. */
    fun saveFinished(jobId: String, title: String, success: Boolean, uri: android.net.Uri?) {
        if (!manager.areNotificationsEnabled()) return
        channel()
        val nTitle = if (success) "Video saved" else "Couldn't save video"
        val nText = if (success) {
            if (title.isNotEmpty()) "Saved ${title.take(30)} to Movies/Recon." else "Saved to Movies/Recon."
        } else {
            "Save failed. Tap to retry."
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(nTitle)
            .setContentText(nText)
            .setAutoCancel(true)

        if (uri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = PendingIntent.getActivity(
                context, uri.hashCode(), viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.setContentIntent(pi)
        } else {
            b.setContentIntent(open(jobId))
        }

        try {
            manager.notify((SAVE_DONE_PREFIX + jobId).hashCode(), b.build())
        } catch (_: SecurityException) { /* permission revoked */ }
    }

    /** DONE/FAILED notification with a deep link to the job. Silently skipped without POST_NOTIFICATIONS. */
    fun finished(job: Job, summary: String?) {
        if (!manager.areNotificationsEnabled()) return
        channel()
        val (title, text) = when (job.state) {
            JobState.DONE -> "Recon finished" to (summary?.lineSequence()?.firstOrNull() ?: "Tap to see what was found.")
            else -> "Recon couldn't finish" to "Tap to see why and what to do next."
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open(job.id))
            .build()
        try {
            manager.notify(job.id.hashCode(), n)
        } catch (_: SecurityException) { /* permission revoked meanwhile; the job is in history anyway */ }
    }

    private fun open(jobId: String): PendingIntent = PendingIntent.getActivity(
        context, jobId.hashCode(),
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_JOB_ID, jobId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL = "jobs"
        const val ONGOING_ID = 1
        const val SAVE_ONGOING_PREFIX = "save_ongoing_"
        const val SAVE_DONE_PREFIX = "save_done_"
    }
}
