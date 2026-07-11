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
