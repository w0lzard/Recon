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
