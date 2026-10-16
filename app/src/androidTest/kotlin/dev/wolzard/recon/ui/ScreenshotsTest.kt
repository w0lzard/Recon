package dev.wolzard.recon.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.performClick
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

    /** [dark] null = follow the device, as the app does. */
    private fun shot(name: String, scrollTo: String? = null, scrollToTag: String? = null, clickText: String? = null, dark: Boolean? = null, content: @Composable () -> Unit) {
        // The real app gets its background from the Scaffold in ReconNavHost; the harness needs its own.
        compose.setContent {
            ReconTheme(dark ?: androidx.compose.foundation.isSystemInDarkTheme()) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
        clickText?.let { compose.onNodeWithText(it).performClick(); compose.waitForIdle() }
        scrollTo?.let { compose.onNodeWithText(it).performScrollTo() }
        scrollToTag?.let { compose.onNodeWithTag(it).performScrollTo() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- Job fixtures: the design's example sweep (Recon UI 1f–1k, 3c–3g; stowly, Kenji Mori and Tern are fictional)

    private val started = now - 12_000

    /** A run 12 s in: downloaded at 0:04, frames at 0:07, analysis running (design 1f). */
    private fun job(state: JobState, retryAt: Long? = null) = Job(
        "7f3a21c0", "https://www.instagram.com/reel/C9xQ2kLmN0p/", url = "https://www.instagram.com/reel/C9xQ2kLmN0p/", state = state,
        retryAtEpochMs = retryAt, createdAt = started - 60_000, updatedAt = now,
        videoTitle = "My desk setup and the CLI behind my dotfiles", videoDurationSec = 38,
        startedAt = started, downloadedAt = started + 4_000, sampledAt = started + 7_000,
        analysedAt = if (state == JobState.DONE) started + 12_000 else null, downloadBytes = 4_200_000, frameCount = 24, hasAudio = true,
    )

    private fun finding(
        type: EntityType, name: String, verdict: Verdict, basis: Basis, why: String, link: String? = null,
        qualifier: String? = null, subtype: dev.wolzard.recon.core.model.MediaType? = null, candidates: List<GhRepo> = emptyList(), source: String? = null, whatItIs: String = "",
    ) = Entity(type, name, qualifier, Evidence.INFERRED, why, subtype = subtype).let { e ->
        Finding(e, "web", verdict, name, whatItIs, emptyList(), "", why, link, null, basis, candidates, source)
    }

    private val sources = listOf("github.com/kmori/stowly", "stowly.dev/docs/install", "github.com/kmori/stowly/bench", "github.com/kmori", "papersatellitesfilm.com")
        .map { Source(it, "https://$it") }

    private val candidates = listOf(
        GhRepo("kmori/stowly", "https://github.com/kmori/stowly", "Go · matches 0:12", 4100),
        GhRepo("stowly-dev/stowly-cli", "https://github.com/stowly-dev/stowly-cli", "fork of kmori/stowly", 212),
        GhRepo("abrams/stowlie", "https://github.com/abrams/stowlie", "similar name, other tool", 38),
    )

    private fun claim(name: String, v: Verdict, basis: Basis, source: String? = null, whatItIs: String = "", d: dev.wolzard.recon.core.model.ClaimDomain = dev.wolzard.recon.core.model.ClaimDomain.OTHER) =
        finding(EntityType.CLAIM, name, v, basis, "", source = source, whatItIs = whatItIs).let { it.copy(entity = it.entity.copy(domain = d)) }

    private val sweep = listOf(
        finding(EntityType.MEDIA, "Paper Satellites", Verdict.CONFIRMED, Basis.WIKIPEDIA, "Poster on the wall behind the desk at 0:15. Title and year match the film's listing.",
            "https://papersatellitesfilm.com", "film, 2019", dev.wolzard.recon.core.model.MediaType.MOVIE),
        finding(EntityType.REPO, "stowly", Verdict.BEST_MATCH, Basis.GITHUB_API, "Name and terminal output on screen match the repository README.", "https://github.com/kmori/stowly", candidates = candidates)
            .copy(title = "kmori/stowly"),
        finding(EntityType.PERSON, "Kenji Mori", Verdict.CONFIRMED, Basis.WEB_SOURCES, "Named in the audio and listed as owner of the repo.", "https://github.com/kmori"),
        finding(EntityType.APP, "Tern", Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE, "Window title visible at 0:05. Named from AI knowledge; no source confirmed it.", qualifier = "terminal emulator"),
        finding(EntityType.PRODUCT, "65% mechanical keyboard", Verdict.UNCLEAR, Basis.VIDEO_ONLY, "Shown at 0:21. The brand mark is not readable at this resolution."),
        claim("stowly runs on macOS, Linux and Windows.", Verdict.SUPPORTED, Basis.WEB_SOURCES, "stowly.dev/docs/install"),
        claim("It cuts shell startup time in half.", Verdict.DISPUTED, Basis.WEB_SOURCES, "github.com/kmori/stowly/bench", "Project benchmarks show about 12%."),
        claim("Sitting under six hours a day halves your risk of back pain.", Verdict.UNCLEAR, Basis.AI_KNOWLEDGE, d = dev.wolzard.recon.core.model.ClaimDomain.HEALTH),
    )

    private val summary = "The creator shows a terminal tool that syncs dotfiles across machines from one config file. The repo name is on screen at 0:12 " +
        "but partly cut off, so three matching repositories were found. One health claim is mentioned in passing."

    private fun result(findings: List<Finding> = sweep, sources: List<Source> = this.sources, summary: String = this.summary) =
        JobResult(AnalysisResult(findings.map { it.entity }, summary, 0.8), findings, sources, null, "B", emptyMap(), emptyMap())

    // Design 1d's example rows.
    private fun homeRows(): List<HomeRow> {
        fun j(id: String, url: String, state: JobState, minAgo: Long, title: String? = null) =
            Job(id, url, url = url, state = state, videoTitle = title, createdAt = now - minAgo * 60_000, updatedAt = now)
        return listOf(
            j("1", "https://www.instagram.com/reel/C9xQ2/", JobState.DONE, 2, "My desk setup and the CLI behind my dotfiles"),
            j("2", "https://www.tiktok.com/@x/video/1", JobState.ANALYSING, 0),
            j("3", "https://www.youtube.com/watch?v=abc", JobState.WAITING_QUOTA, 18, "Sourdough at 80% hydration, start to finish"),
            j("4", "https://x.com/a/status/18402", JobState.FAILED, 60),
            j("5", "https://www.instagram.com/reel/D1/", JobState.DONE, 26 * 60, "Five-minute garlic noodles"),
        ).map { HomeRow.from(it, now) }
    }

    @Test fun home() = shot("home") { HomeScreen(HomeState(homeRows())) {} }

    @Test fun homeLight() = shot("home_light", dark = false) { HomeScreen(HomeState(homeRows())) {} }

    @Test fun homeEmpty() = shot("home_empty") { HomeScreen(HomeState()) {} }

    @Test fun jobDone() = shot("job_done") { JobScreen(job(JobState.DONE), result(), now) {} }                          // 1h

    @Test fun jobRunning() = shot("job_running") { JobScreen(job(JobState.ANALYSING), null, now) {} }                   // 1f

    @Test fun unverified() = shot("job_unverified", scrollTo = "Tern terminal emulator") { JobScreen(job(JobState.DONE), result(), now) {} }

    @Test fun noEntity() = shot("job_no_entity") {                                                                       // 1j
        JobScreen(
            job(JobState.DONE).copy(id = "51d402aa", videoTitle = "Five-minute garlic noodles", videoDurationSec = 52, frameCount = 30),
            result(emptyList(), emptyList(), "A cooking clip: noodles tossed with garlic, butter and soy sauce. No names, products, links or factual claims appear on screen or in the audio."), now,
        ) {}
    }

    @Test fun candidatePicker() = shot("job_candidates", scrollTo = "Kenji Mori") { JobScreen(job(JobState.DONE), result(), now) {} } // 1i

    @Test fun quotaWaiting() = shot("job_quota_waiting") {                                                             // 1g
        JobScreen(job(JobState.WAITING_QUOTA, retryAt = now + 118 * 60_000 + 30_000).copy(id = "2b9014ee", url = "https://www.youtube.com/watch?v=Psy_4mEWNDI"), null, now) {}
    }

    // --- D23: context screen, the question echo and the answer card

    private fun asked(state: JobState) = job(state).copy(userContext = "What song is playing in the background?")

    private val answerSources = listOf("music.lowtide.fm/glass-harbour", "lyricsindex.org/lowtide-radio")

    private fun answered(answer: Answer?, findings: List<Finding> = sweep) =
        result(findings).let { it.copy(analysis = it.analysis.copy(answer = answer)) }

    @Test fun contextEmpty() = shot("context_empty") {
        ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com")) {}
    }

    @Test fun contextFilled() = shot("context_filled") {
        ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com", text = "What song is this, and who sings it?")) {}
    }

    // --- D26: link preview on Add context (loaded, and the fallback when the image never arrives)

    private val preview = dev.wolzard.recon.core.model.LinkPreview("Glass Harbour (live session)", 38, "https://i.ytimg.com/vi/x/hq.jpg")

    /** A stand-in thumbnail: a two-tone diagonal, so the shot shows a real image in the slot without any network. */
    private fun fakeThumb(): androidx.compose.ui.graphics.ImageBitmap {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xFF2E4A3A.toInt())
        c.drawPath(android.graphics.Path().apply { moveTo(0f, 160f); lineTo(160f, 0f); lineTo(160f, 160f); close() }, android.graphics.Paint().apply { color = 0xFFA8E06C.toInt() })
        return b.asImageBitmap()
    }

    @Test fun contextThumbnail() = shot("context_thumbnail") {
        val img = fakeThumb()
        androidx.compose.runtime.CompositionLocalProvider(dev.wolzard.recon.designsystem.LocalThumbnails provides { _ -> img }) {
            ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com", text = "What song is this?", preview = preview)) {}
        }
    }

    @Test fun contextFallback() = shot("context_fallback") {
        androidx.compose.runtime.CompositionLocalProvider(dev.wolzard.recon.designsystem.LocalThumbnails provides { _ -> null }) {
            ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com", text = "What song is this?", preview = preview)) {}
        }
    }

    @Test fun jobRunningWithQuestion() = shot("job_running_question") { JobScreen(asked(JobState.ANALYSING), null, now) {} }

    @Test fun jobAnswerVerified() = shot("job_answer") {                                                                // 3d
        val a = Answer("Plays under the voiceover for the whole clip. Lyrics heard at 0:09 match the track page.", AnswerStatus.VERIFIED, 0.9, answerSources,
            EntityType.MEDIA, dev.wolzard.recon.core.model.MediaType.SONG, "Glass Harbour Lowtide Radio, 2024")
        JobScreen(asked(JobState.DONE), answered(a), now) {}
    }

    @Test fun jobAnswerUnclear() = shot("job_answer_unclear") {                                                         // 3g
        val a = Answer("The music sits under loud speech and no lyrics are audible. Try sharper video, or share a clip with clearer audio.", AnswerStatus.UNCLEAR, 0.2,
            emptyList(), EntityType.MEDIA, dev.wolzard.recon.core.model.MediaType.SONG, "No confident match")
        JobScreen(asked(JobState.DONE), answered(a), now) {}
    }

    @Test fun jobAnswerNoSource() = shot("job_answer_no_source") {                                                      // 3f
        val a = Answer("Named from AI knowledge of the melody. No source confirmed the match.", AnswerStatus.UNVERIFIED, 0.5,
            emptyList(), EntityType.MEDIA, dev.wolzard.recon.core.model.MediaType.SONG, "Glass Harbour Lowtide Radio")
        JobScreen(asked(JobState.DONE), answered(a), now) {}
    }

    // --- D24

    @Test fun jobFailed() = shot("job_failed") {                                                                        // 1k
        JobScreen(
            job(JobState.FAILED).copy(
                id = "9c1e07bb", sharedText = "https://x.com/someone/status/1840200000000000000", url = "https://x.com/someone/status/1840200000000000000",
                failure = dev.wolzard.recon.core.model.FailureReason.VIDEO_UNREADABLE, downloadBytes = 2_100_000, frameCount = 0, videoTitle = null,
            ),
            null, now,
        ) {}
    }

    @Test fun jobClaims() = shot("job_claims", scrollToTag = "sharper_card") { JobScreen(job(JobState.DONE), result(), now) {} } // 1i, end

    @Test fun quotaWaitingNoTime() = shot("job_quota_waiting_no_time") { JobScreen(job(JobState.WAITING_QUOTA), null, now) {} }

    @Test fun signIn() {
        shot("sign_in") { SignInScreen(SignInState(), onBack = {}) {} }
    }

    @Test fun signInPaste() { // design 1c: the paste field, inline under "Advanced"
        shot("sign_in_paste", clickText = "Advanced: paste cookie header", scrollToTag = "cookie_field") {
            SignInScreen(SignInState(error = dev.wolzard.recon.core.copy.SetupCopy.NO_SESSION_COOKIE), onBack = {}) {}
        }
    }

    // Design 1l content: an update waiting, signed in, checked at 14:00, this build's real certificate.
    @Test fun settings() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val state = dev.wolzard.recon.feature.settings.SettingsState(
            signedIn = true, email = "recon.side@gmail.com",
            available = dev.wolzard.recon.data.update.UpdateInfo("1.4.0", "https://github.com/a.apk", "https://github.com/r"),
            lastChecked = java.time.LocalDate.now().atTime(14, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            certSha256 = dev.wolzard.recon.feature.settings.signingSha256(ctx),
        )
        shot("settings") { SettingsScreen(state) {} }
    }

    @Test fun settingsSignedOut() {
        val state = SettingsViewModel(koin.get(), koin.get()).state.value.copy(signedIn = false, email = null, available = null)
        shot("settings_signed_out") { SettingsScreen(state) {} }
    }

    @Test fun notice() = shot("first_run_notice") { NoticeScreen {} }

    private val koin get() = GlobalContext.get()
}
