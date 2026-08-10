package dev.wolzard.recon.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Answer
import dev.wolzard.recon.core.model.AnswerStatus
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
import dev.wolzard.recon.feature.context.ContextScreen
import dev.wolzard.recon.feature.context.ContextState
import dev.wolzard.recon.feature.home.HomeRow
import dev.wolzard.recon.feature.home.HomeScreen
import dev.wolzard.recon.feature.home.HomeState
import dev.wolzard.recon.feature.job.JobScreen
import dev.wolzard.recon.feature.settings.SettingsScreen
import dev.wolzard.recon.feature.settings.SettingsViewModel
import dev.wolzard.recon.feature.signin.SignInScreen
import dev.wolzard.recon.feature.signin.SignInState
import dev.wolzard.recon.feature.notice.NoticeScreen
import dev.wolzard.recon.designsystem.ReconTheme
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

    private fun shot(name: String, scrollTo: String? = null, scrollToTag: String? = null, content: @Composable () -> Unit) {
        // The real app gets its background from the Scaffold in ReconNavHost; the harness needs its own.
        compose.setContent { ReconTheme { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        scrollTo?.let { compose.onNodeWithText(it).performScrollTo() }
        scrollToTag?.let { compose.onNodeWithTag(it).performScrollTo() }
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
        HomeScreen(HomeState(listOf(HomeRow.from(waiting), HomeRow.from(done)))) {}
    }

    @Test fun homeEmpty() = shot("home_empty") { HomeScreen(HomeState()) {} }

    @Test fun jobDone() = shot("job_done") {
        JobScreen(job(JobState.DONE), result(listOf(
            finding(EntityType.BOOK, "Atomic Habits", Verdict.CONFIRMED, Basis.WIKIPEDIA, link = "https://en.wikipedia.org/wiki/Atomic_Habits"),
            finding(EntityType.PLACE, "Hampi", Verdict.SUPPORTED, Basis.WEB_SOURCES, Evidence.INFERRED),
        ), listOf(Source("Atomic Habits - Wikipedia", "https://en.wikipedia.org/wiki/Atomic_Habits"))), now) {}
    }

    @Test fun jobRunning() = shot("job_running") { JobScreen(job(JobState.ANALYSING), null, now) {} }

    @Test fun unverified() = shot("job_unverified") {
        JobScreen(job(JobState.DONE), result(listOf(finding(EntityType.PRODUCT, "Some gadget", Verdict.UNCLEAR, Basis.AI_KNOWLEDGE))), now) {}
    }

    @Test fun noEntity() = shot("job_no_entity") { JobScreen(job(JobState.DONE), result(emptyList()), now) {} }

    @Test fun candidatePicker() = shot("job_candidates", scrollTo = "None of these") {
        val repos = listOf(
            GhRepo("example/fast-notes", "https://github.com/example/fast-notes", "Markdown notes app", 1200),
            GhRepo("other/notes-fast", "https://github.com/other/notes-fast", null, 40),
            GhRepo("third/fastnote", "https://github.com/third/fastnote", "CLI notes", 3),
        )
        JobScreen(job(JobState.DONE), result(listOf(finding(EntityType.REPO, "fast notes", Verdict.BEST_MATCH, Basis.GITHUB_API, Evidence.SPOKEN, candidates = repos))), now) {}
    }

    @Test fun quotaWaiting() = shot("job_quota_waiting") { JobScreen(job(JobState.WAITING_QUOTA, retryAt = now + 2 * 3_600_000), null, now) {} }

    // --- D23: context screen, the question echo and the answer card

    private fun asked(state: JobState) = job(state).copy(userContext = "What song is this?")

    private fun answered(answer: Answer?, findings: List<Finding>) =
        result(findings).let { it.copy(analysis = it.analysis.copy(answer = answer)) }

    @Test fun contextEmpty() = shot("context_empty") {
        ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com")) {}
    }

    @Test fun contextFilled() = shot("context_filled") {
        ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com", text = "What song is this, and who sings it?")) {}
    }

    @Test fun jobRunningWithQuestion() = shot("job_running_question") { JobScreen(asked(JobState.ANALYSING), null, now) {} }

    @Test fun jobAnswerVerified() = shot("job_answer") {
        val a = Answer("It is “Blinding Lights” by The Weeknd, shown in the caption.", AnswerStatus.VERIFIED, 0.9, listOf("caption at the start", "spoken: “this is Blinding Lights”"))
        val song = finding(EntityType.MEDIA, "Blinding Lights", Verdict.CONFIRMED, Basis.WIKIPEDIA, link = "https://en.wikipedia.org/wiki/Blinding_Lights")
        JobScreen(asked(JobState.DONE), answered(a, listOf(song)), now) {}
    }

    @Test fun jobAnswerUnclear() = shot("job_answer_unclear") {
        val a = Answer("The video does not name the person, so this is not enough to say who it is.", AnswerStatus.UNCLEAR, 0.2)
        JobScreen(asked(JobState.DONE).copy(userContext = "Who is this?"), answered(a, emptyList()), now) {}
    }

    @Test fun jobAnswerNoSource() = shot("job_answer_no_source") {
        val a = Answer("Probably “Blinding Lights”, from the melody; nothing in the video names it.", AnswerStatus.UNVERIFIED, 0.5)
        JobScreen(asked(JobState.DONE), answered(a, emptyList()), now) {}
    }

    // --- D24

    @Test fun jobFailed() = shot("job_failed") {
        JobScreen(job(JobState.FAILED).copy(failure = dev.wolzard.recon.core.model.FailureReason.VIDEO_UNREADABLE), null, now) {}
    }

    @Test fun jobClaims() = shot("job_claims", scrollToTag = "advice_footer") {
        fun claim(name: String, v: Verdict, basis: Basis, d: dev.wolzard.recon.core.model.ClaimDomain?) =
            finding(EntityType.CLAIM, name, v, basis).let { it.copy(entity = it.entity.copy(domain = d), source = if (v == Verdict.DISPUTED) "Project benchmarks" else null) }
        JobScreen(job(JobState.DONE), result(listOf(
            claim("It cuts shell startup time in half.", Verdict.DISPUTED, Basis.WEB_SOURCES, dev.wolzard.recon.core.model.ClaimDomain.OTHER),
            claim("It runs on macOS, Linux and Windows.", Verdict.SUPPORTED, Basis.WEB_SOURCES, dev.wolzard.recon.core.model.ClaimDomain.OTHER),
            claim("Sitting under six hours a day halves back-pain risk.", Verdict.UNCLEAR, Basis.AI_KNOWLEDGE, dev.wolzard.recon.core.model.ClaimDomain.HEALTH),
        )), now) {}
    }

    @Test fun quotaWaitingNoTime() = shot("job_quota_waiting_no_time") { JobScreen(job(JobState.WAITING_QUOTA), null, now) {} }

    @Test fun homeUpdate() = shot("home_update") {
        val info = dev.wolzard.recon.data.update.UpdateInfo("0.2.0", "https://github.com/w0lzard/recon/releases/download/v0.2.0/a.apk", "https://github.com/w0lzard/recon/releases/tag/v0.2.0")
        HomeScreen(HomeState(update = info)) {}
    }

    @Test fun signIn() {
        shot("sign_in") { SignInScreen(SignInState()) {} }
    }

    @Test fun settings() {
        val state = SettingsViewModel(koin.get(), koin.get()).state.value
        shot("settings") { SettingsScreen(state) {} }
    }

    @Test fun notice() = shot("first_run_notice") { NoticeScreen {} }

    private val koin get() = GlobalContext.get()
}
