package dev.wolzard.recon.work

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dev.wolzard.recon.ShareActivity
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState

/** What Recon has on screen, kept by ReconApp (activities) and ShareActivity (the job its sheet shows). */
object Visible {
    @Volatile var resumedActivities = 0
    @Volatile var sheetJob: String? = null
    /** The last job the sheet came up with; unlike [sheetJob] it stays set after the sheet closes. */
    @Volatile var lastShownJob: String? = null
}

/**
 * Share sheet: how a finished job reaches the user.
 * - The sheet already shows it: nothing (it updates in place).
 * - Failed, or Recon is open on another screen: a notification (tap opens the sheet).
 * - Succeeded while nothing of Recon is on screen: the sheet pops up over the current app, if the user allowed
 *   "Display over other apps"; otherwise, or if the pop fails, the notification.
 */
class ResultPresenter(private val context: Context, private val notifier: Notifier) {
    enum class Action { NOTHING, POP, NOTIFY }

    fun finished(job: Job, summary: String?) {
        when (decide(job.state, Visible.sheetJob == job.id, Visible.resumedActivities > 0, canOverlay())) {
            Action.NOTHING -> Unit
            Action.NOTIFY -> notifier.finished(job, summary)
            Action.POP -> pop(job.id) { notifier.finished(job, summary) }
        }
    }

    private fun canOverlay() = Settings.canDrawOverlays(context)

    /**
     * Android 10+ blocks activity starts from the background; holding SYSTEM_ALERT_WINDOW lifts that, and from
     * Android 15 only while the app shows an overlay window. So a 1 px invisible, untouchable overlay is added,
     * the sheet is started, and the overlay is removed a moment later.
     */
    private fun pop(jobId: String, fallback: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        main.post {
            val wm = context.getSystemService(WindowManager::class.java)
            val dot = View(context)
            val params = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            try {
                wm.addView(dot, params)
                context.startActivity(ShareActivity.openJob(context, jobId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                main.postDelayed({
                    runCatching { wm.removeView(dot) }
                    if (Visible.lastShownJob != jobId) fallback() // a blocked start is dropped silently, not thrown
                }, 6_000)
            } catch (_: Exception) {
                runCatching { wm.removeView(dot) }
                fallback()
            }
        }
    }

    companion object {
        fun decide(state: JobState, sheetShowsJob: Boolean, appOnScreen: Boolean, canOverlay: Boolean): Action = when {
            sheetShowsJob -> Action.NOTHING
            state != JobState.DONE || appOnScreen || !canOverlay -> Action.NOTIFY
            else -> Action.POP
        }
    }
}
