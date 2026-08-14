package dev.wolzard.recon.work

import android.content.Context
import android.provider.Settings
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState

/** What Recon has on screen, kept by ReconApp (activities) and ShareActivity (the job its sheet shows). */
object Visible {
    @Volatile var resumedActivities = 0
    @Volatile var sheetJob: String? = null
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
            Action.POP -> notifier.finished(job, summary) // until there is a sheet to pop up
        }
    }

    private fun canOverlay() = Settings.canDrawOverlays(context)

    companion object {
        fun decide(state: JobState, sheetShowsJob: Boolean, appOnScreen: Boolean, canOverlay: Boolean): Action = when {
            sheetShowsJob -> Action.NOTHING
            state != JobState.DONE || appOnScreen || !canOverlay -> Action.NOTIFY
            else -> Action.POP
        }
    }
}
