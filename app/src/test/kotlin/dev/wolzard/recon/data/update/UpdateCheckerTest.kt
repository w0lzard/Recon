package dev.wolzard.recon.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTest {
    private val server = MockWebServer()
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("update-test", Context.MODE_PRIVATE)
    private var now = 1_000_000_000L
    private val page = "https://github.com/w0lzard/recon/releases/tag/v0.2.0"

    @Before fun start() { prefs.edit().clear().commit(); server.start() }
    @After fun stop() = server.close()

    private fun checker(abis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "armeabi"), current: String = "0.1.0") =
        UpdateChecker(OkHttpClient(), prefs, current, "w0lzard/recon", abis, { now }, server.url("").toString().trimEnd('/'))

    // Asset names as assembleRelease produced them on 2026-10-08 (AGP <archivesName>-<abi>-<buildType>.apk).
    private fun asset(name: String, url: String = "https://github.com/w0lzard/recon/releases/download/v0.2.0/$name") =
        """{"name":"$name","browser_download_url":"$url"}"""
    private val arm64 = asset("recon-0.2.0-arm64-v8a-release.apk")
    private val v7a = asset("recon-0.2.0-armeabi-v7a-release.apk")

    private fun release(tag: String = "v0.2.0", vararg assets: String = arrayOf(arm64, v7a), htmlUrl: String = page) =
        MockResponse.Builder().body("""{"tag_name":"$tag","html_url":"$htmlUrl","draft":false,"prerelease":false,"assets":[${assets.joinToString(",")}]}""").build()

    private fun url(name: String) = "https://github.com/w0lzard/recon/releases/download/v0.2.0/$name"

    // --- ABI asset matching, one per shipped ABI path

    @Test fun arm64PhonePicksArm64() = runTest {
        server.enqueue(release())
        assertEquals(UpdateInfo("0.2.0", url("recon-0.2.0-arm64-v8a-release.apk"), page), checker().check())
        val req = server.takeRequest()
        assertEquals("/repos/w0lzard/recon/releases/latest", req.url.encodedPath)
        assertEquals("Recon", req.headers["User-Agent"])
    }

    @Test fun v7aPhonePicksV7a() = runTest {
        server.enqueue(release())
        assertEquals(url("recon-0.2.0-armeabi-v7a-release.apk"), checker(listOf("armeabi-v7a", "armeabi")).check()!!.apkUrl)
    }

    @Test fun x86WithArmTranslationPicksArm64() = runTest {
        server.enqueue(release())
        assertEquals(url("recon-0.2.0-arm64-v8a-release.apk"), checker(listOf("x86_64", "arm64-v8a")).check()!!.apkUrl)
    }

    @Test fun noShippedAbiGivesReleasePage() = runTest {
        server.enqueue(release())
        assertEquals(page, checker(listOf("x86_64", "x86")).check()!!.apkUrl)
    }

    @Test fun unsignedAssetNeverMatches() = runTest {
        server.enqueue(release(assets = arrayOf(asset("recon-0.2.0-UNSIGNED-FOR-RELEASE-arm64-v8a-release.apk"))))
        assertEquals(page, checker().check()!!.apkUrl)
    }

    // --- links: only https github.com

    @Test fun nonGithubAssetUrlGivesReleasePage() = runTest {
        listOf(
            "http://github.com/w0lzard/recon/releases/download/v0.2.0/a.apk",
            "https://evil.example/recon-0.2.0-arm64-v8a-release.apk",
            "https://github.com.evil.example/a.apk",
            "https://github.com@evil.example/a.apk",
            "https://objects.github.com.evil/a.apk",
            "javascript:alert(1)",
            "file:///sdcard/a.apk",
        ).forEach { bad ->
            prefs.edit().clear().commit()
            server.enqueue(release(assets = arrayOf(asset("recon-0.2.0-arm64-v8a-release.apk", bad))))
            assertEquals(bad, page, checker().check()!!.apkUrl)
        }
    }

    @Test fun nonGithubReleasePageFallsBackToReleasesList() = runTest {
        server.enqueue(release(htmlUrl = "https://evil.example/r"))
        assertEquals("https://github.com/w0lzard/recon/releases", checker(listOf("x86")).check()!!.releasePageUrl)
    }

    // --- results

    @Test fun sameOrOlderTagIsNull() = runTest {
        server.enqueue(release(tag = "v0.1.0"))
        assertNull(checker().check())
        server.enqueue(release(tag = "v0.0.9"))
        assertNull(checker().check(force = true))
    }

    @Test fun unparseableTagIsNull() = runTest {
        server.enqueue(release(tag = "latest"))
        assertNull(checker().check())
    }

    @Test fun errorsAreNull() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(checker().check(force = true))
        server.enqueue(MockResponse.Builder().code(403).build())
        assertNull(checker().check(force = true))
        server.enqueue(MockResponse.Builder().body("not json").build())
        assertNull(checker().check(force = true))
        val c = checker()
        server.close() // connection refused = IOException
        assertNull(c.check(force = true))
    }

    @Test fun nowDistinguishesUpToDateAndFailed() = runTest {
        server.enqueue(release(tag = "v0.1.0"))
        assertEquals(UpdateResult.UpToDate, checker().checkNow())
        server.enqueue(MockResponse.Builder().code(500).build())
        assertEquals(UpdateResult.Failed, checker().checkNow())
    }

    // --- throttling

    @Test fun secondCallWithin24hMakesNoRequestAndReturnsLastResult() = runTest {
        server.enqueue(release())
        val first = checker().check()
        now += 23 * 3_600_000L
        assertEquals(first, checker().check())
        assertEquals(1, server.requestCount)
    }

    @Test fun forceAndExpiryMakeRequests() = runTest {
        server.enqueue(release()); server.enqueue(release()); server.enqueue(release())
        checker().check()
        checker().check(force = true)
        now += 24 * 3_600_000L + 1
        checker().check()
        assertEquals(3, server.requestCount)
    }

    @Test fun cachedResultIsDroppedOnceInstalled() = runTest {
        server.enqueue(release())
        checker().check()
        assertNull(checker(current = "0.2.0").check())
    }

    // --- dismiss

    @Test fun dismissHidesThatVersionOnly() = runTest {
        server.enqueue(release())
        val c = checker()
        c.check()
        c.dismiss("0.2.0")
        assertNull(c.available.value)
        assertNull(checker().check())
        server.enqueue(release(tag = "v0.3.0", assets = arrayOf()))
        assertEquals("0.3.0", checker().check(force = true)!!.version)
    }

    @Test fun lastCheckedIsNullUntilGitHubIsAsked() = runTest {
        val c = checker()
        assertEquals(null, c.lastChecked())
        server.enqueue(release())
        c.checkNow()
        assertEquals(now, c.lastChecked())
    }
}
