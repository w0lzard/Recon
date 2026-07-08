package dev.wolzard.recon.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DHashTest {
    private val gradient = IntArray(DHash.W * DHash.H) { i -> (i % DHash.W) * 25 + (i / DHash.W) * 3 }
    private val noisy = IntArray(DHash.W * DHash.H) { i -> (i * 97 + 13) % 256 }

    @Test fun identicalImagesHaveDistanceZero() {
        assertEquals(0, DHash.distance(DHash.of(noisy), DHash.of(noisy.copyOf())))
    }

    @Test fun invertedImageIsFarAway() {
        val inverted = noisy.map { 255 - it }.toIntArray()
        assertTrue(DHash.distance(DHash.of(noisy), DHash.of(inverted)) > 32)
        assertTrue(DHash.distance(DHash.of(gradient), DHash.of(gradient.map { 255 - it }.toIntArray())) > 32)
    }

    @Test fun timestampsForTwentyMinutesAreTwentyEvenlySpaced() {
        val t = FrameTimestamps.of(1_200_000)
        assertEquals(20, t.size)
        assertEquals((0 until 20).map { it * 60_000L }, t)
    }

    @Test fun timestampsForThirtySecondsAreFifteenAtTwoSeconds() {
        assertEquals((0 until 15).map { it * 2_000L }, FrameTimestamps.of(30_000))
    }

    @Test fun shortAndEmptyDurations() {
        assertEquals(listOf(0L), FrameTimestamps.of(1_500))
        assertEquals(listOf(0L), FrameTimestamps.of(0))
    }

    @Test fun samplerLimitsAreBounded() {
        assertEquals(256 * 1024, SamplerLimits.audioBuffer(null))
        assertEquals(64 * 1024, SamplerLimits.audioBuffer(10))
        assertEquals(1024 * 1024, SamplerLimits.audioBuffer(Int.MAX_VALUE))   // file-controlled size is capped
        assertTrue(SamplerLimits.frameTooLarge(7680, 4320))
        assertTrue(SamplerLimits.frameTooLarge(100_000, 10))
        assertTrue(!SamplerLimits.frameTooLarge(3840, 2160))
        assertTrue(!SamplerLimits.frameTooLarge(null, null))
    }
}
