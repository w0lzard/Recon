package dev.wolzard.recon.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.signin.SignInScreen
import dev.wolzard.recon.feature.signin.SignInState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * D27: with the keyboard open on the WebView step, a Next button pinned to the bottom of the page (as on Google's
 * mobile sign-in) is still above the keyboard. A local page stands in for Google's, so the test needs no network.
 */
@RunWith(AndroidJUnit4::class)
class SignInKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val page = "data:text/html," + android.net.Uri.encode(
        """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;height:100vh">
        <input id="email" style="position:absolute;top:24px;left:16px;width:80%;height:48px;font-size:18px">
        <button id="next" style="position:fixed;bottom:16px;right:16px;height:40px">Next</button>
        </body></html>""",
    )

    private fun webView(): WebView? {
        var found: WebView? = null
        compose.activityRule.scenario.onActivity { a ->
            fun walk(v: View) { if (v is WebView) found = v else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
            walk(a.window.decorView)
        }
        return found
    }

    /** Bottom of #next in window pixels, or null until the page has laid it out. */
    private fun nextBottom(web: WebView): Float? {
        val latch = CountDownLatch(1)
        var css: Float? = null
        instrumentation.runOnMainSync {
            web.evaluateJavascript("(function(){var b=document.getElementById('next');return b?b.getBoundingClientRect().bottom:null})()") {
                css = it.toFloatOrNull(); latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        val loc = IntArray(2)
        instrumentation.runOnMainSync { web.getLocationInWindow(loc) }
        return css?.let { loc[1] + it * web.resources.displayMetrics.density }
    }

    private fun imeTop(): Int {
        var top = Int.MAX_VALUE
        compose.activityRule.scenario.onActivity { a ->
            val root = a.window.decorView
            val ime = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            top = if (ime > 0) root.height - ime else Int.MAX_VALUE
        }
        return top
    }

    @Test fun keyboardNeverCoversGooglesNextButton() {
        instrumentation.uiAutomation.executeShellCommand("settings put secure show_ime_with_hard_kbd 1").close()
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent { ReconTheme { SignInScreen(SignInState(), signInUrl = page) {} } }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.waitUntil(10_000) { webView()?.let(::nextBottom) != null }
        val web = webView()!!

        // A real tap on the field, so the WebView asks for the keyboard the way it does on Google's page.
        val loc = IntArray(2)
        instrumentation.runOnMainSync { web.getLocationOnScreen(loc) }
        val d = web.resources.displayMetrics.density
        val x = loc[0] + 40 * d; val y = loc[1] + 48 * d
        val t = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        compose.waitUntil(10_000) { imeTop() != Int.MAX_VALUE }

        // The WebView shrinks with the keyboard's show animation; give the page up to 3 s to reflow.
        var bottom = Float.MAX_VALUE
        val top = imeTop()
        runCatching { compose.waitUntil(3_000) { bottom = nextBottom(web) ?: Float.MAX_VALUE; bottom <= top } }
        assertTrue("Next bottom $bottom must be above the keyboard top $top", bottom <= top)
    }
}
