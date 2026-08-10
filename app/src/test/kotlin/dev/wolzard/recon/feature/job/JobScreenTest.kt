package dev.wolzard.recon.feature.job

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
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
    private val actions = mutableListOf<JobIntent>()

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
    @Test fun apiKeyInvalid() = assertFailure(FailureReason.API_KEY_INVALID, "Sign in again")
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
        assertEquals(listOf<JobIntent>(JobIntent.CheckAgain), actions)
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
        assertEquals(listOf<JobIntent>(JobIntent.LookUp), actions)
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
        assertEquals(listOf<JobIntent>(JobIntent.Pick(0, repos[1])), actions)
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
        val f = finding(entity(EntityType.CLAIM, "c"), Verdict.DISPUTED, Basis.WEB_SOURCES)
        show(job(JobState.DONE), result(listOf(f), sources = listOf(Source("WHO fact sheet", "https://who.int/x")), html = "<div>chips</div>"))
        compose.onNodeWithText("WHO fact sheet").performScrollTo().assertExists()
        compose.onNodeWithTag("search-suggestions").assertExists()
    }

    @Test fun noSuggestionsViewWithoutHtml() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.APP, "X"), Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE))))
        compose.onNodeWithTag("search-suggestions").assertDoesNotExist()
        compose.onNodeWithText("SOURCES", substring = true).assertDoesNotExist()
    }

    @Test fun answeringModelsAreShown() {
        show(job(JobState.DONE), result(listOf(finding(entity(EntityType.APP, "X"), Verdict.BEST_MATCH, Basis.AI_KNOWLEDGE))))
        compose.onNodeWithText("gemini-3.8-flash", substring = true).assertExists()
    }

    // --- running states

    @Test fun waitingQuotaSaysWhenItStarts() {
        show(job(JobState.WAITING_QUOTA, retryAt = now + 60_000))
        compose.onNodeWithText("Waiting for your free AI quota").assertExists()
        compose.onNodeWithText("Link: instagram.com").assertExists()
        compose.onNodeWithText("Quota resets at ${JobCopy.time(now + 60_000)}", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<JobIntent>(JobIntent.Cancel), actions)
    }

    @Test fun topBarShowsHostAndBack() {
        show(job(JobState.ANALYSING))
        compose.onNodeWithText("instagram.com").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf<JobIntent>(JobIntent.Back), actions)
    }

    @Test fun downloadingShowsSteps() {
        show(job(JobState.DOWNLOADING))
        compose.onNodeWithText("Downloading").assertExists()
        compose.onNodeWithText("Analysing").assertExists()
    }

    // --- D23: the viewer's question and the answer card

    private fun answered(answer: dev.wolzard.recon.core.model.Answer?, findings: List<Finding> = listOf(finding(entity(EntityType.APP, "Codeium"), Verdict.CONFIRMED, Basis.WEB_SOURCES))) =
        result(findings).let { it.copy(analysis = it.analysis.copy(answer = answer)) }

    private fun asked(state: JobState = JobState.DONE) = job(state).copy(userContext = "What app is this?")

    @Test fun questionIsEchoedWhileRunning() {
        show(asked(JobState.ANALYSING))
        compose.onNodeWithText("YOUR QUESTION").assertExists()
        compose.onNodeWithTag("your_question").assertExists()
        compose.onNodeWithText("“What app is this?”").assertExists()
    }

    @Test fun questionIsEchoedWhenFailed() {
        show(asked(JobState.FAILED).copy(failure = FailureReason.NETWORK))
        compose.onNodeWithText("“What app is this?”").assertExists()
    }

    @Test fun noQuestionMeansNoQuestionBlockOrAnswerCard() {
        show(job(JobState.DONE), answered(null))
        compose.onAllNodesWithText("YOUR QUESTION").assertCountEquals(0)
        compose.onAllNodesWithText("ANSWER").assertCountEquals(0)
    }

    @Test fun answerCardComesBeforeSummaryAndEntities() {
        val a = dev.wolzard.recon.core.model.Answer("It is Codeium.", dev.wolzard.recon.core.model.AnswerStatus.VERIFIED, 0.9, listOf("caption at the start"))
        show(asked(), answered(a))
        compose.onNodeWithText("It is Codeium.").assertExists()
        compose.onNodeWithText("Verified").assertExists()
        compose.onNodeWithText("90%").assertExists()
        compose.onAllNodesWithText("caption at the start").assertCountEquals(2) // in the card and in the shared source list
        val y = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        val summaryTop = compose.onNodeWithText("SUMMARY").fetchSemanticsNode().boundsInRoot.top
        val entitiesTop = compose.onNodeWithText("ENTITIES FOUND · 1", substring = true).fetchSemanticsNode().boundsInRoot.top
        val q = y("your_question")
        val card = y("answer_card")
        assert(q < card && card < summaryTop && summaryTop < entitiesTop) { "order: question $q, answer $card, summary $summaryTop, entities $entitiesTop" }
    }

    @Test fun unclearAnswerShowsItsStatus() {
        val a = dev.wolzard.recon.core.model.Answer("The video does not say who this is.", dev.wolzard.recon.core.model.AnswerStatus.UNCLEAR, 0.2)
        show(asked(), answered(a))
        compose.onNodeWithText("Unclear").assertExists()
        compose.onNodeWithText("The video does not say who this is.").assertExists()
    }

    @Test fun questionWithoutAnswerShowsAFallbackCard() {
        show(asked(), answered(null))
        compose.onNodeWithText(JobCopy.NO_ANSWER).assertExists()
        compose.onNodeWithText("Unclear").assertExists()
    }

    @Test fun mediaFindingShowsItsLabel() {
        val f = finding(entity(EntityType.MEDIA, "Inception"), Verdict.CONFIRMED, Basis.WIKIPEDIA)
        show(job(JobState.DONE), result(listOf(f)))
        compose.onNodeWithText("MEDIA").assertExists()
        compose.onNodeWithText("Watch or listen: how").assertExists()
    }

    // --- Design turn 3 (2026-10-14): shared source numbering, no-source line, media qualifier

    @Test fun answerSourcesAreNumberedAfterTheJobSources() {
        val a = dev.wolzard.recon.core.model.Answer("It is Codeium.", dev.wolzard.recon.core.model.AnswerStatus.VERIFIED, 0.9, listOf("caption at 0:04", "codeium.com pricing page"))
        val r = answered(a).copy(sources = listOf(Source("Codeium docs", "https://codeium.com/docs")))
        show(asked(), r)
        compose.onNodeWithText("SOURCES · 3").performScrollTo().assertExists()
        compose.onAllNodesWithText("2").assertCountEquals(2) // "caption at 0:04" in the card and in the list
        compose.onAllNodesWithText("3").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Opens in browser").assertCountEquals(1) // answer sources are plain text, never links
    }

    @Test fun unverifiedAnswerWithoutSourcesSaysSo() {
        val a = dev.wolzard.recon.core.model.Answer("Probably Codeium.", dev.wolzard.recon.core.model.AnswerStatus.UNVERIFIED, 0.5)
        show(asked(), answered(a))
        compose.onNodeWithText("Unverified").assertExists()
        compose.onNodeWithText(JobCopy.NO_SOURCE).assertExists()
        compose.onNodeWithTag("no_source").assertExists() // icon and text in one row
    }

    @Test fun mediaTitleCarriesItsQualifier() {
        val f = finding(entity(EntityType.MEDIA, "Paper Satellites").copy(qualifier = "film, 2019"), Verdict.CONFIRMED, Basis.WIKIPEDIA)
        show(job(JobState.DONE), result(listOf(f)))
        compose.onNodeWithText("Paper Satellites film, 2019").assertExists()
    }

    // --- D24: advice line and waiting without a time

    private fun claim(name: String, domain: dev.wolzard.recon.core.model.ClaimDomain?, verdict: Verdict = Verdict.UNCLEAR) =
        finding(entity(EntityType.CLAIM, name).copy(domain = domain), verdict, Basis.WEB_SOURCES)

    @Test fun healthLegalAndFinancialClaimsShowTheAdviceLineAndOneFooter() {
        val d = dev.wolzard.recon.core.model.ClaimDomain.values()
        show(job(JobState.DONE), result(listOf(claim("h", d[0]), claim("l", d[1]), claim("f", d[2]))))
        compose.onAllNodesWithTag("advice_line").assertCountEquals(3)
        compose.onAllNodesWithText(JobCopy.NOT_ADVICE).assertCountEquals(4) // one per claim + the footer
        compose.onNodeWithTag("advice_footer").performScrollTo().assertExists()
    }

    @Test fun otherOrUntaggedClaimsHaveNoAdviceLineOrFooter() {
        show(job(JobState.DONE), result(listOf(claim("o", dev.wolzard.recon.core.model.ClaimDomain.OTHER, Verdict.SUPPORTED), claim("n", null, Verdict.DISPUTED))))
        compose.onAllNodesWithTag("advice_line").assertCountEquals(0)
        compose.onAllNodesWithTag("advice_footer").assertCountEquals(0)
        compose.onAllNodesWithText(JobCopy.NOT_ADVICE).assertCountEquals(0)
    }

    @Test fun adviceLineIsOnlyOnTheSensitiveClaim() {
        show(job(JobState.DONE), result(listOf(claim("sensitive", dev.wolzard.recon.core.model.ClaimDomain.HEALTH), claim("plain", dev.wolzard.recon.core.model.ClaimDomain.OTHER, Verdict.SUPPORTED))))
        compose.onAllNodesWithTag("advice_line").assertCountEquals(1)
        compose.onAllNodesWithText(JobCopy.NOT_ADVICE).assertCountEquals(2) // the line and the footer
    }

    @Test fun namedSourceIsShownOnTheClaim() {
        val f = claim("c", dev.wolzard.recon.core.model.ClaimDomain.HEALTH, Verdict.DISPUTED).copy(source = "WHO fact sheet")
        show(job(JobState.DONE), result(listOf(f)))
        compose.onNodeWithText("Source: WHO fact sheet").assertExists()
    }

    @Test fun waitingWithoutATimeShowsTryAgainLaterAndNoClock() {
        show(job(JobState.WAITING_QUOTA, retryAt = null))
        compose.onAllNodesWithText(JobCopy.TRY_LATER, substring = true).assertCountEquals(2) // sentence + reset card
        compose.onAllNodesWithText("Quota resets at", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("~", substring = true).assertCountEquals(0)
        val clock = Regex("""\b\d{1,2}:\d{2}\b""")
        val texts = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().let { n ->
            fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                (x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.map { t -> t.text }.orEmpty()) + x.children.flatMap(::walk)
            walk(n)
        }
        assertEquals(emptyList<String>(), texts.filter { clock.containsMatchIn(it) })
    }

    @Test fun failedQuotaWithoutATimeDoesNotInventOne() {
        assertEquals("You've reached your Gemini usage limit. Try again later.", JobCopy.failureMessage(FailureReason.QUOTA_EXCEEDED, null))
    }
}
