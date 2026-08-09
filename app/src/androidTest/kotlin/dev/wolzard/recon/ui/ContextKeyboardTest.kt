package dev.wolzard.recon.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.context.ContextScreen
import dev.wolzard.recon.feature.context.ContextState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** With the keyboard open, the field and both buttons can be scrolled above it (D24, 360x640 requirement). */
@RunWith(AndroidJUnit4::class)
class ContextKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fieldAndButtonsStayAboveTheKeyboard() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put secure show_ime_with_hard_kbd 1").close()
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent { ReconTheme { ContextScreen(ContextState("https://www.instagram.com/reel/abc/", "instagram.com", text = "What song is this?")) {} } }
        compose.onNodeWithTag("context_field").performClick()
        var imeTop = Int.MAX_VALUE
        compose.waitUntil(10_000) {
            compose.activityRule.scenario.onActivity { a ->
                val root = a.window.decorView
                val ime = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                imeTop = if (ime > 0) root.height - ime else Int.MAX_VALUE
            }
            imeTop != Int.MAX_VALUE
        }
        // The root insets already hold the keyboard's final height, but imePadding() follows the show animation, and
        // waitForIdle() can return before it ends (always on a cold keyboard after a fresh install). So each target gets
        // up to 3 s to settle above the keyboard; a screen that really clips never does and still fails.
        for (target in listOf(compose.onNodeWithTag("context_field"), compose.onNodeWithText("Start"), compose.onNodeWithText("Skip"))) {
            var bottom = Float.MAX_VALUE
            runCatching {
                compose.waitUntil(3_000) {
                    target.performScrollTo()
                    bottom = target.fetchSemanticsNode().boundsInWindow.bottom
                    bottom <= imeTop
                }
            }
            assertTrue("bottom $bottom must be above the keyboard top $imeTop", bottom <= imeTop)
        }
    }
}
