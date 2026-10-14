package dev.wolzard.recon.feature.signin

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.copy.SetupCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Design 1c on the D27 flow: intro first, paste inline under "Advanced", D25 inline states. The WebView step is in SignInKeyboardTest. */
@RunWith(AndroidJUnit4::class)
class SignInScreenTest {
    @get:Rule val compose = createComposeRule()
    private val intents = mutableListOf<SignInIntent>()

    @Test fun introHasTheSentenceRiskCardAndBothWaysIn() {
        compose.setContent { SignInScreen(SignInState()) { intents += it } }
        compose.onNodeWithText("STEP 2 OF 2 · ACCOUNT").assertExists()
        compose.onNodeWithText(SetupCopy.SIGN_IN_INTRO).assertExists()
        compose.onNodeWithTag("risk_row").assertExists()
        compose.onNodeWithText("ACCOUNT RISK").assertExists()
        compose.onNodeWithText(SetupCopy.RISK_SHORT).assertExists()
        compose.onNodeWithText("Continue with Google").assertIsEnabled()
        compose.onNodeWithText("Advanced: paste cookie header").performScrollTo().assertExists()
        compose.onNodeWithTag("cookie_field").assertDoesNotExist() // folded until "Advanced" is opened
        compose.onNodeWithTag("sign_in_webview").assertDoesNotExist() // Google's page only after the tap
        compose.onNodeWithContentDescription("Back").assertDoesNotExist() // first run: nothing to go back to
    }

    @Test fun backArrowOnlyWhenThereIsSomewhereToGo() {
        var back = 0
        compose.setContent { SignInScreen(SignInState(), onBack = { back++ }) { intents += it } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test fun advancedOpensThePasteFieldInlineAndSendsTheCookies() {
        compose.setContent { SignInScreen(SignInState()) { intents += it } }
        compose.onNodeWithText("Advanced: paste cookie header").performScrollTo().performClick()
        compose.onNodeWithText(SetupCopy.PASTE_HELP).performScrollTo().assertExists()
        compose.onNodeWithText("Use these cookies").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("cookie_field").performTextInput("__Secure-1PSID=abc")
        compose.onNodeWithText("Use these cookies").performScrollTo().performClick()
        assertEquals(listOf<SignInIntent>(SignInIntent.CookiesReceived("__Secure-1PSID=abc")), intents)
    }

    @Test fun errorsShowInlineOnTheIntro() {
        val error = SetupCopy.signInFailed(dev.wolzard.recon.core.model.FailureReason.API_KEY_INVALID)
        compose.setContent { SignInScreen(SignInState(error = error)) { intents += it } }
        compose.onNodeWithText(error).assertExists()
    }

    @Test fun checkingDisablesBothWaysIn() {
        compose.setContent { SignInScreen(SignInState(checking = true)) { intents += it } }
        compose.onNodeWithText("Checking sign-in…").assertExists()
        compose.onNodeWithText("Continue with Google").assertIsNotEnabled()
        compose.onNodeWithText("Advanced: paste cookie header").performScrollTo().performClick()
        compose.onNodeWithTag("cookie_field").performTextInput("__Secure-1PSID=abc")
        compose.onNodeWithText("Use these cookies").performScrollTo().assertIsNotEnabled()
    }
}
