package dev.wolzard.recon.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserContextTest {
    @Test fun blankIsNull() {
        assertNull(UserContext.clean(null))
        assertNull(UserContext.clean(" \n\t "))
    }

    @Test fun trimmedAndCappedAt300() {
        assertEquals("hello", UserContext.clean("  hello \n"))
        assertEquals(300, UserContext.clean("x".repeat(500))!!.length)
        assertEquals("a".repeat(299), UserContext.clean("a".repeat(299) + " " + "b".repeat(50))) // cut leaves a trailing space: trimmed again
    }
}
