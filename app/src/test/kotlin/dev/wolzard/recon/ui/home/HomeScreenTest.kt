package dev.wolzard.recon.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.data.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyState() {
        compose.setContent { HomeScreen(emptyList(), {}, {}) }
        compose.onNodeWithText("Share a video link to Recon from any app").assertExists()
    }

    @Test fun updateBannerOpensLinksAndDismisses() {
        val opened = mutableListOf<String>()
        var dismissed = false
