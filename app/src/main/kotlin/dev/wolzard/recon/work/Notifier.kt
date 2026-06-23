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
