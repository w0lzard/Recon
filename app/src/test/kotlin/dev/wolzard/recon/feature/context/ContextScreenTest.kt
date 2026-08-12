package dev.wolzard.recon.feature.context

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextScreenTest {
    @get:Rule val compose = createComposeRule()
    private val intents = mutableListOf<ContextIntent>()
    private val base = ContextState("https://www.instagram.com/reel/abc/", "instagram.com")

    @Test fun showsHostTitleCounterChipsAndBothButtons() {
        compose.setContent { ContextScreen(base) { intents += it } }
        compose.onNodeWithText("Add context").assertExists()
        compose.onNodeWithText("OPTIONAL").assertExists()
        compose.onNodeWithTag("context_host").assertExists()
        compose.onNodeWithText("instagram.com").assertExists()
        compose.onNodeWithText("0/300").assertExists()
        ContextCopy.SUGGESTIONS.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Start").assertExists()
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test fun buttonsAndChipsSendIntents() {
        compose.setContent { ContextScreen(base) { intents += it } }
        compose.onNodeWithText("What song is this?").performScrollTo().performClick()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf(ContextIntent.Suggest("What song is this?"), ContextIntent.Start, ContextIntent.Skip), intents)
    }

    @Test fun typingSendsTextChanged() {
        compose.setContent { ContextScreen(base) { intents += it } }
        compose.onNodeWithTag("context_field").performTextInput("hi")
        assertEquals(ContextIntent.TextChanged("hi"), intents.single())
    }

    @Test fun errorAndDisabledWhileStarting() {
        compose.setContent { ContextScreen(base.copy(error = ContextCopy.INVALID_LINK, text = "abc", starting = true)) { intents += it } }
        compose.onNodeWithText(ContextCopy.INVALID_LINK).assertExists()
        compose.onNodeWithText("3/300").assertExists()
        compose.onNodeWithText("Start").performClick()
        assertEquals(emptyList<ContextIntent>(), intents)
    }

    @Test fun linkIconUntilAThumbnailAndLengthInTheDetailLine() { // D26
        val p = dev.wolzard.recon.core.model.LinkPreview("Glass Harbour", 38, "https://i.ytimg.com/vi/x/hq.jpg")
        compose.setContent { ContextScreen(base.copy(preview = p)) { intents += it } } // no loader provided: the fallback stays
        compose.onNodeWithTag("thumb_fallback").assertExists()
        compose.onNodeWithTag("context_detail").assertTextEquals("/reel/abc/ · 0:38 video")
    }
}
