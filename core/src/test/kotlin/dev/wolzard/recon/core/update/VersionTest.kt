package dev.wolzard.recon.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    private fun v(s: String) = Version.parse(s)!!

    @Test fun parsesWithOrWithoutV() {
        assertEquals(Version(1, 2, 3, null), v("v1.2.3"))
        assertEquals(Version(1, 2, 3, null), v("1.2.3"))
        assertEquals(Version(1, 0, 0, "beta"), v("1.0.0-beta"))
    }

    @Test fun numericOrdering() = assertTrue(v("v1.10.0") > v("v1.9.9"))

    @Test fun preReleaseSortsBeforeRelease() {
        assertTrue(v("1.0.0-beta") < v("1.0.0"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-beta"))
    }

    @Test fun garbageIsNull() {
        listOf("", "v", "1.2", "1.2.3.4", "x1.2.3", "1.2.c", "latest", "1.2.3-", " 1.2.3").forEach { assertNull(it, Version.parse(it)) }
    }
}
