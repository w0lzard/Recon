package dev.wolzard.recon.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.ShareActivity
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.notice.Notice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Share sheet on a device: the real activity, Room and Koin graph; no job is run against Gemini. */
@RunWith(AndroidJUnit4::class)
class ShareSheetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = GlobalContext.get()
    private val store get() = koin.get<JobStore>()
    private val secrets get() = koin.get<SecretStore>()
    private val created = mutableListOf<String>()
    private val shell = InstrumentationRegistry.getInstrumentation().uiAutomation

    /** Whole-screen capture (the sheet over whatever is behind it) to <external files>/screenshots/<name>.png. */
    private fun capture(name: String) {
        Thread.sleep(800) // let the sheet finish sliding in
        val dir = java.io.File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        shell.takeScreenshot()?.let { b -> java.io.File(dir, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** Runs a shell command and waits for it to finish (closing the descriptor alone does not wait). */
    private fun sh(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand(cmd)).use { it.readBytes() }
    }

    @Before fun setUp() {
        Notice(ctx.getSharedPreferences(Notice.PREFS, Context.MODE_PRIVATE)).accept()
        GeminiSession.save(secrets, "__Secure-1PSID=test-not-real")
        ctx.getSharedPreferences("sheet", Context.MODE_PRIVATE).edit().putBoolean("overlayAsked", true).commit()
    }

    @After fun tearDown() {
        runBlocking { created.forEach { store.delete(it) } }
        GeminiSession.clear(secrets)
        sh("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW default")
    }

    private fun doneJob(summary: String): String = runBlocking {
        val id = store.create("https://www.instagram.com/reel/abc/", null, "What song is this?")
        val result = JobResult(AnalysisResult(emptyList(), summary, 0.9), emptyList(), emptyList(), null, "A")
        store.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), result)) }
        created += id
        id
    }

    @Test fun sheetShowsAJobsResult() {
        val id = doneJob("A cat plays the piano in a kitchen.")
        ActivityScenario.launch<ShareActivity>(ShareActivity.openJob(ctx, id)).use {
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("A cat plays the piano in a kitchen.").assertExists() }.isSuccess }
            compose.onNodeWithTag("share_sheet").assertExists()
            compose.onNodeWithText("“What song is this?”").assertExists()
            capture("sheet_result")
        }
    }

    @Test fun sharedLinkOpensAddContextInTheSheet() {
        val share = Intent(Intent.ACTION_SEND).setClass(ctx, ShareActivity::class.java).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "look https://www.instagram.com/reel/abc/")
        ActivityScenario.launch<ShareActivity>(share).use {
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Add context").assertExists() }.isSuccess }
            compose.onNodeWithTag("context_field").assertExists()
            compose.onNodeWithText("Start").assertExists()
            compose.onNodeWithText("Skip").assertExists()
            capture("sheet_context")
        }
    }
}
