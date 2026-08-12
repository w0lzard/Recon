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

/** D27 structure: intro first, paste as its own step, D25 inline states on every step. The WebView step is in SignInKeyboardTest. */
@RunWith(AndroidJUnit4::class)
class SignInScreenTest {
    @get:Rule val compose = createComposeRule()
    private val intents = mutableListOf<SignInIntent>()

    @Test fun introHasTheSentenceRiskRowBothWaysInAndTheFinePrint() {
        compose.setContent { SignInScreen(SignInState()) { intents += it } }
        compose.onNodeWithText("STEP 2 OF 2 · ACCOUNT").assertExists()
        compose.onNodeWithText(SetupCopy.SIGN_IN_INTRO).assertExists()
        compose.onNodeWithTag("risk_row").assertExists()
        compose.onNodeWithText(SetupCopy.RISK_SHORT).assertExists()
        compose.onNodeWithText("Sign in with Google").assertIsEnabled()
        compose.onNodeWithText("Paste cookies instead").assertIsEnabled()
        compose.onNodeWithText(SetupCopy.WEBVIEW_BLOCKED_HELP).performScrollTo().assertExists()
        compose.onNodeWithText(SetupCopy.SIGN_IN_STORED).performScrollTo().assertExists()
        compose.onNodeWithTag("account_risk").performScrollTo().assertExists()
        compose.onNodeWithTag("sign_in_webview").assertDoesNotExist() // Google's page only after the tap
    }

    @Test fun pasteStepSendsTheCookiesAndBackReturnsToTheIntro() {
        compose.setContent { SignInScreen(SignInState()) { intents += it } }
        compose.onNodeWithText("Paste cookies instead").performClick()
        compose.onNodeWithText(SetupCopy.PASTE_HELP).assertExists()
        compose.onNodeWithText("Use these cookies").assertIsNotEnabled()
        compose.onNodeWithTag("cookie_field").performTextInput("__Secure-1PSID=abc")
        compose.onNodeWithText("Use these cookies").performClick()
        assertEquals(listOf<SignInIntent>(SignInIntent.CookiesReceived("__Secure-1PSID=abc")), intents)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sign in with Google").assertExists()
    }

    @Test fun errorsShowInlineOnTheIntroAndThePasteStep() {
        val error = SetupCopy.signInFailed(dev.wolzard.recon.core.model.FailureReason.API_KEY_INVALID)
        compose.setContent { SignInScreen(SignInState(error = error)) { intents += it } }
        compose.onNodeWithText(error).assertExists()
        compose.onNodeWithText("Paste cookies instead").performClick()
        compose.onNodeWithText(error).assertExists()
    }

    @Test fun checkingDisablesBothWaysIn() {
        compose.setContent { SignInScreen(SignInState(checking = true)) { intents += it } }
        compose.onNodeWithText("Checking sign-in…").assertExists()
        compose.onNodeWithText("Sign in with Google").assertIsNotEnabled()
        compose.onNodeWithText("Paste cookies instead").assertIsNotEnabled()
    }
}
