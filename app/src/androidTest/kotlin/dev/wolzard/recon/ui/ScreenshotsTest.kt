package dev.wolzard.recon.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Source
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.ui.home.HomeRow
import dev.wolzard.recon.ui.home.HomeScreen
import dev.wolzard.recon.ui.job.JobScreen
import dev.wolzard.recon.ui.settings.SettingsScreen
import dev.wolzard.recon.ui.settings.SettingsViewModel
import dev.wolzard.recon.ui.setup.SignInScreen
import dev.wolzard.recon.ui.setup.SignInViewModel
import dev.wolzard.recon.ui.setup.NoticeScreen
import dev.wolzard.recon.ui.theme.ReconTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Not assertions: renders each Task 13 state with fixture data and saves a PNG to
 * <external files>/screenshots/<name><suffix>.png. Pass `-e suffix _360` for the small-screen run.
 * ponytail: viewport capture only; long Job screens are scrolled to the part being shown.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val suffix = InstrumentationRegistry.getArguments().getString("suffix", "")
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun shot(name: String, scrollTo: String? = null, content: @Composable () -> Unit) {
        compose.setContent { ReconTheme { content() } }
        compose.waitForIdle()
        scrollTo?.let { compose.onNodeWithText(it).performScrollTo() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun job(state: JobState, retryAt: Long? = null) =
        Job("j1", "https://www.instagram.com/reel/abc/", url = "https://www.instagram.com/reel/abc/", state = state, retryAtEpochMs = retryAt, createdAt = now, updatedAt = now)

    private fun finding(type: EntityType, name: String, verdict: Verdict, basis: Basis, evidence: Evidence = Evidence.ON_SCREEN, link: String? = null, candidates: List<GhRepo> = emptyList()) =
        Entity(type, name, null, evidence, "The video shows $name.").let { e ->
            Finding(e, name, verdict, name, "What $name is, in one line.", listOf("A key fact", "Another fact"), "How to try it", "Shown as the main pick.", link, null, basis, candidates)
        }

    private fun result(findings: List<Finding>, sources: List<Source> = emptyList()) =
        JobResult(AnalysisResult(findings.map { it.entity }, "A short video about a few things worth looking up.", 0.8, listOf("on-screen text")),
            findings, sources, null, "A", mapOf("analyse" to "gemini-3.8-flash", "research" to "gemini-3.7-flash"), emptyMap())

    @Test fun home() = shot("home") {
        val done = Job("j1", "https://www.instagram.com/reel/abc/", url = "https://www.instagram.com/reel/abc/", state = JobState.DONE,
            resultJson = """{"analysis":{"entities":[],"summary":"Three productivity books reviewed.","confidence":0.8},"findings":[],"sources":[],"searchSuggestionsHtml":null,"path":"A"}""",
            createdAt = now - 3_600_000, updatedAt = now)
        val waiting = Job("j2", "https://www.tiktok.com/@x/video/1", url = "https://www.tiktok.com/@x/video/1", state = JobState.WAITING_QUOTA, createdAt = now, updatedAt = now)
        HomeScreen(listOf(HomeRow.from(waiting), HomeRow.from(done)), {}, {})
    }

    @Test fun homeEmpty() = shot("home_empty") { HomeScreen(emptyList(), {}, {}) }

