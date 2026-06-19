package dev.wolzard.recon.data.media

/** 64-bit difference hash over a 9×8 grayscale image: bit = left pixel brighter than its right neighbour. */
object DHash {
    const val W = 9
    const val H = 8

    /** @param gray W*H luminance values, row-major. */
    fun of(gray: IntArray): Long {
        require(gray.size == W * H)
        var hash = 0L
        var bit = 0
        for (y in 0 until H) for (x in 0 until W - 1) {
            if (gray[y * W + x] > gray[y * W + x + 1]) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

/** Path B sampling times: every max(2 s, duration/20), at most 20 (Review Focus #5). */
object FrameTimestamps {
    private const val MIN_STEP_MS = 2_000L
    private const val MAX_FRAMES = 20

    fun of(durationMs: Long): List<Long> {
        val step = maxOf(MIN_STEP_MS, durationMs / MAX_FRAMES)
        return generateSequence(0L) { it + step }.takeWhile { it < maxOf(durationMs, 1) }.take(MAX_FRAMES).toList()
    }
}
