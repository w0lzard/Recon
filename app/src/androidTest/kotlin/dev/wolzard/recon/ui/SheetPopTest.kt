package dev.wolzard.recon.ui

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.notice.Notice
import dev.wolzard.recon.work.ResultPresenter
import dev.wolzard.recon.work.Visible
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Share sheet: a job that succeeds while Recon is in the background pops the sheet over the current app.
 * No Compose test rule here: with one in the process, Compose animations follow the test clock and the popped sheet
 * would never slide in, which is not what a user sees.
 */
@RunWith(AndroidJUnit4::class)
class SheetPopTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = GlobalContext.get()
    private val store get() = koin.get<JobStore>()
    private val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var id: String? = null

    private fun sh(cmd: String) { ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand(cmd)).use { it.readBytes() } }

    @After fun tearDown() {
        sh("input keyevent KEYCODE_BACK")
        runBlocking { id?.let { store.delete(it) } }
        GeminiSession.clear(koin.get<SecretStore>())
        sh("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW default")
    }

    @Test fun successfulJobPopsTheSheetOverAnotherApp() {
        Notice(ctx.getSharedPreferences(Notice.PREFS, Context.MODE_PRIVATE)).accept()
        GeminiSession.save(koin.get<SecretStore>(), "__Secure-1PSID=test-not-real")
        sh("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
        assertTrue("overlay permission granted", Settings.canDrawOverlays(ctx))
        val jobId = runBlocking {
            val result = JobResult(AnalysisResult(emptyList(), "Popped up by itself.", 0.9), emptyList(), emptyList(), null, "A")
            store.create("https://www.instagram.com/reel/abc/", null, null).also { id ->
                store.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), result)) }
            }
        }.also { id = it }

        sh("input keyevent KEYCODE_HOME")
        val deadline = System.currentTimeMillis() + 5_000
        while (Visible.resumedActivities > 0 && System.currentTimeMillis() < deadline) Thread.sleep(100)
        Thread.sleep(1_500) // let the launcher finish coming to the front

        koin.get<ResultPresenter>().finished(runBlocking { store.get(jobId) }!!, "Popped up by itself.")

        val until = System.currentTimeMillis() + 10_000
        while (Visible.sheetJob != jobId && System.currentTimeMillis() < until) Thread.sleep(100)
        // sheetJob is set only while ShareActivity is resumed on this job: the sheet is up, over the launcher.
        assertTrue("the sheet should be on screen showing job $jobId (was ${Visible.sheetJob})", Visible.sheetJob == jobId)
        Thread.sleep(1_500) // slide-in
        val dir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        shell.takeScreenshot()?.let { b -> File(dir, "sheet_popped.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        Thread.sleep(6_000) // past ResultPresenter's check: a sheet that came up means no fallback notification
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue("no fallback notification after a successful pop", nm.activeNotifications.none { it.id == jobId.hashCode() })
    }
}
