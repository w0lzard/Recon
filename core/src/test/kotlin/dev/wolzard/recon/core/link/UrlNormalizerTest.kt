package dev.wolzard.recon.core.link

import dev.wolzard.recon.core.port.LinkResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    private val resolver = object : LinkResolver {
        override suspend fun resolve(url: String): String = when {
            url.contains("vm.tiktok.com/ZM8eX") -> "https://www.tiktok.com/@creator/video/7123456789012345678?is_from_webapp=1"
            url.contains("vt.tiktok.com/ZS123") -> "https://www.tiktok.com/@creator/video/7987654321098765432"
            else -> url
        }
    }

    private val normalizer = UrlNormalizer(resolver)

    @Test fun normalizesYouTubeLinks() = runBlocking {
        val cases = listOf(
            "https://www.youtube.com/watch?v=Psy_4mEWNDI" to "youtube:Psy_4mEWNDI",
            "https://youtube.com/watch?v=Psy_4mEWNDI&feature=share&si=abc123xyz" to "youtube:Psy_4mEWNDI",
            "https://youtu.be/Psy_4mEWNDI?t=42s&si=deadbeef" to "youtube:Psy_4mEWNDI",
            "https://www.youtube.com/shorts/aqz-KE-bpKQ?feature=share" to "youtube:aqz-KE-bpKQ",
            "https://youtube.com/live/Psy_4mEWNDI?si=xyz" to "youtube:Psy_4mEWNDI",
            "http://m.youtube.com/watch?v=Psy_4mEWNDI" to "youtube:Psy_4mEWNDI",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, normalizer.normalize(input))
        }
    }

    @Test fun normalizesInstagramLinks() = runBlocking {
        val cases = listOf(
            "https://www.instagram.com/reel/C9xQ2kLmN0p/" to "instagram:C9xQ2kLmN0p",
            "https://instagram.com/reel/C9xQ2kLmN0p/?igsh=MWx5eDds&utm_source=ig_web" to "instagram:C9xQ2kLmN0p",
            "https://www.instagram.com/p/DF123456789/?img_index=1" to "instagram:DF123456789",
            "https://instagram.com/tv/C9xQ2kLmN0p" to "instagram:C9xQ2kLmN0p",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, normalizer.normalize(input))
        }
    }

    @Test fun normalizesTikTokLinks() = runBlocking {
        val cases = listOf(
            "https://www.tiktok.com/@username/video/7123456789012345678?is_from_webapp=1" to "tiktok:7123456789012345678",
            "https://tiktok.com/@user.name/video/7123456789012345678" to "tiktok:7123456789012345678",
            "https://vm.tiktok.com/ZM8eX1234/" to "tiktok:7123456789012345678",
            "https://vt.tiktok.com/ZS123abc/" to "tiktok:7987654321098765432",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, normalizer.normalize(input))
        }
    }

    @Test fun normalizesXTwitterLinks() = runBlocking {
        val cases = listOf(
            "https://x.com/user/status/1840212345678901234?s=46&t=xyz" to "x:1840212345678901234",
            "https://twitter.com/someone/status/1840212345678901234" to "x:1840212345678901234",
            "https://www.x.com/user/status/1840212345678901234/" to "x:1840212345678901234",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, normalizer.normalize(input))
        }
    }

    @Test fun normalizesGenericWebFallback() = runBlocking {
        val cases = listOf(
            "https://www.github.com/torvalds/linux?utm_source=twitter#readme" to "web:https://github.com/torvalds/linux",
            "http://example.com/video/?ref=share&fbclid=123" to "web:http://example.com/video",
            "https://Example.COM/Path/To/Item/" to "web:https://example.com/Path/To/Item",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, normalizer.normalize(input))
        }
    }
}
