package dev.wolzard.recon.ui.job

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Source
import dev.wolzard.recon.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobScreenTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_000_000L
    private val actions = mutableListOf<JobAction>()

    private fun job(state: JobState, failure: FailureReason? = null, retryAt: Long? = null) =
        Job("j1", "https://www.instagram.com/reel/x/", state = state, failure = failure, failureDetail = "detail", retryAtEpochMs = retryAt, createdAt = 0, updatedAt = 0)

    private fun entity(type: EntityType, name: String, evidence: Evidence = Evidence.ON_SCREEN) = Entity(type, name, null, evidence, "The video says $name.")
    private fun finding(e: Entity, verdict: Verdict, basis: Basis, link: String? = null, candidates: List<GhRepo> = emptyList()) =
        Finding(e, "x", verdict, e.name, "What ${e.name} is.", listOf("fact one"), "how", "why", link, null, basis, candidates)

    private fun result(findings: List<Finding>, degraded: Map<String, Long?> = emptyMap(), sources: List<Source> = emptyList(), html: String? = null, path: String = "A") =
        JobResult(AnalysisResult(findings.map { it.entity }, "A video about things.", 0.8, listOf("on screen")), findings, sources, html, path,
            mapOf("analyse" to "gemini-3.8-flash", "research" to "gemini-3.7-flash"), degraded)

    private fun show(job: Job, result: JobResult? = null) = compose.setContent { JobScreen(job, result, now) { actions += it } }

    // --- FAILED: one test per failure-table row (message + action)

    private fun assertFailure(reason: FailureReason, vararg actionLabels: String) {
        show(job(JobState.FAILED, reason))
        compose.onNodeWithText(JobCopy.failureMessage(reason, null, "detail"), substring = true).assertExists()
        actionLabels.forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun unsupportedLink() = assertFailure(FailureReason.UNSUPPORTED_LINK)
    @Test fun downloadFailed() = assertFailure(FailureReason.DOWNLOAD_FAILED, "Share the video file instead", "Retry")
    @Test fun videoUnreadable() = assertFailure(FailureReason.VIDEO_UNREADABLE, "Share a different file")
    @Test fun apiKeyMissing() = assertFailure(FailureReason.API_KEY_MISSING, "Sign in")
    @Test fun apiKeyInvalid() = assertFailure(FailureReason.API_KEY_INVALID, "Sign in")
    @Test fun network() = assertFailure(FailureReason.NETWORK, "Retry")
    @Test fun modelOutputInvalid() = assertFailure(FailureReason.MODEL_OUTPUT_INVALID, "Retry")
    @Test fun modelBusy() = assertFailure(FailureReason.MODEL_BUSY, "Retry")
    @Test fun unknownShowsDetail() {
        assertFailure(FailureReason.UNKNOWN, "Retry")
        compose.onNodeWithText("detail", substring = true).assertExists()
    }

    @Test fun quotaExceededRetryDisabledUntilRetryAt() {
        show(job(JobState.FAILED, FailureReason.QUOTA_EXCEEDED, retryAt = now + 3_600_000))
        compose.onNodeWithText("reached your Gemini usage limit", substring = true).assertExists()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
    }

    // --- degraded banners

    @Test fun degradedKnowledgeBanner() {
        val e = entity(EntityType.BOOK, "Atomic Habits")
        show(job(JobState.DONE), result(listOf(finding(e, Verdict.NOT_CHECKED, Basis.VIDEO_ONLY)), degraded = mapOf("knowledge" to null)))
        compose.onNodeWithText("Couldn't look things up", substring = true).assertExists()
        compose.onNodeWithText("Check again").performClick()
        assertEquals(listOf<JobAction>(JobAction.CheckAgain), actions)
    }

    @Test fun degradedGithubBanner() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.REPO, "cli"), Verdict.NOT_CHECKED, Basis.VIDEO_ONLY)), degraded = mapOf("github" to null)))
        compose.onNodeWithText("GitHub limit reached", substring = true).assertExists()
        compose.onNodeWithText("Add GitHub token").assertExists()
    }

    @Test fun degradedBudgetBanner() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.APP, "X"), Verdict.NOT_CHECKED, Basis.VIDEO_ONLY)), degraded = mapOf("budget" to null)))
        compose.onNodeWithText("lookups were skipped to save your Gemini usage", substring = true).assertExists()
        compose.onNodeWithText("Look up").performClick()
        assertEquals(listOf<JobAction>(JobAction.LookUp), actions)
    }

    @Test fun wikipediaDegradedIsSilent() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.APP, "X"), Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE)), degraded = mapOf("wikipedia" to null)))
        compose.onNodeWithText("Couldn't look things up", substring = true).assertDoesNotExist()
    }

    // --- verdicts, badges, basis

    @Test fun disputedClaimNeverConfirmed() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.CLAIM, "Chocolate causes acne"), Verdict.DISPUTED, Basis.WEB_SOURCES))))
        compose.onNodeWithText("Disputed").assertExists()
        compose.onNodeWithText("Chocolate causes acne").assertExists()
        compose.onNodeWithText("Confirmed").assertDoesNotExist()
    }

    @Test fun inferredShowsGuessBadge() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.PRODUCT, "Kirby", Evidence.INFERRED), Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE))))
        compose.onNodeWithText("Guess").assertExists()
    }

    @Test fun notCheckedShowsWhatTheVideoSaid() {
        val e = entity(EntityType.PLACE, "Taj Mahal")
        show(job(JobState.DONE), result(listOf(Finding(e, "none", Verdict.NOT_CHECKED, e.name, e.saidInVideo, emptyList(), "", "", null, null, Basis.VIDEO_ONLY))))
        compose.onNodeWithText("Not checked").assertExists()
        compose.onNodeWithText("The video says Taj Mahal.").assertExists()
    }

    @Test fun aiKnowledgeIsLabelledUnverifiedNeverConfirmedOrSupported() {
        show(job(JobState.DONE), result(listOf(
            finding(entity(EntityType.CLAIM, "Water while standing hurts knees"), Verdict.UNCLEAR, Basis.AI_KNOWLEDGE),
            finding(entity(EntityType.BOOK, "Atomic Habits"), Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE),
        )))
        compose.onAllNodesWithText("Unverified, from AI knowledge").assertCountEquals(2)
        compose.onNodeWithText("Confirmed").assertDoesNotExist()
        compose.onNodeWithText("Supported").assertDoesNotExist()
    }

    // --- r2 candidates picker

    @Test fun candidatesPickerShowsRowsAndPicksOne() {
        val repos = listOf(
            GhRepo("HEJustinSun/my-girlfriend-jingtian-latex", "https://github.com/HEJustinSun/my-girlfriend-jingtian-latex", "LaTeX", 3),
            GhRepo("fuzzland/girlfriend", "https://github.com/fuzzland/girlfriend", null, 1),
            GhRepo("habi/latex", "https://github.com/habi/latex", null, 1),
        )
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.REPO, "he-justinsun-s-girlfriend-jinchin-latex", Evidence.SPOKEN), Verdict.BEST_MATCH, Basis.GITHUB_API, candidates = repos))))
        compose.onNodeWithText("Which repo did the video mean?").assertExists()
        repos.forEach { compose.onNodeWithText(it.fullName).assertExists() }
        compose.onNodeWithText("None of these").assertExists()
        compose.onNodeWithText("fuzzland/girlfriend").performScrollTo().performClick()
        assertEquals(listOf<JobAction>(JobAction.Pick(0, repos[1])), actions)
    }

    // --- done layout

    @Test fun noEntitiesSaysNothingToLookUpAndOffersSharper() {
        show(job(JobState.DONE), result(emptyList()))
        compose.onNodeWithText("Nothing specific to look up in this video").assertExists()
        compose.onNodeWithText("Try sharper video").assertExists()
    }

    @Test fun youtubeUrlRouteNeverOffersSharper() {
        show(job(JobState.DONE), result(emptyList(), path = "youtube-url"))
        compose.onNodeWithText("Try sharper video").assertDoesNotExist()
    }

    @Test fun sourcesAndSuggestionsOnlyWhenGrounded() {
