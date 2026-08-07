package dev.wolzard.recon.feature.signin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import dev.wolzard.recon.designsystem.openInBrowser
import java.net.URI

/** D25: what the sign-in WebView may load, how it is locked down, and how sign-out wipes it. */
object SignInWeb {
    // ponytail: exact hosts plus Google's per-country accounts hosts (SetSID redirects); add a host only when sign-in breaks without it.
    private val HOSTS = setOf(
        "accounts.google.com", "gemini.google.com", "consent.google.com", "www.google.com", "accounts.youtube.com", "myaccount.google.com",
    )
    private val COUNTRY_ACCOUNTS = Regex("""accounts\.google\.(?:[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})""")

    /** True for an https URL on a Google sign-in or Gemini host; everything else leaves the WebView. */
    fun allowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in HOSTS || COUNTRY_ACCOUNTS.matches(host)
    }

    /** Navigation outside [allowed]: http(s) goes to the system browser, any other scheme is dropped. */
    fun external(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    @SuppressLint("SetJavaScriptEnabled") // Google's sign-in page needs JavaScript
    fun lockDown(web: WebView) = with(web.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        @Suppress("DEPRECATION") saveFormData = false
    }

    /** Main-frame navigations off the allow-list never load here. */
    open class Client(private val ctx: Context) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (!request.isForMainFrame || allowed(url)) return false
            if (external(url)) ctx.openInBrowser(url)
            return true
        }
    }

    /** Sign-out: WebView cookies, DOM/IndexedDB storage, HTTP cache and saved HTTP auth. Call on the main thread. */
    fun clear(ctx: Context) {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies { cookies.flush() } // async; the flush writes the empty jar to disk
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(ctx).clearHttpAuthUsernamePassword()
        WebView(ctx).apply { clearCache(true); destroy() }
    }
}
