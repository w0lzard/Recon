package dev.wolzard.recon.core.search

import dev.wolzard.recon.core.search.NeedMatcher.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class NeedMatcherTest {
    @Test fun ftsQueryQuotesEarlierWordsAndPrefixesTheLast() {
        assertEquals("\"edit\" \"videos\" and*", NeedMatcher.ftsQuery("edit videos and"))
        assertEquals("Sym*", NeedMatcher.ftsQuery("  Sym "))   // case is kept; FTS4 ignores it
        assertEquals("\"a\" \"OR\" b*", NeedMatcher.ftsQuery("a OR b"))      // a typed operator is a plain word
        assertEquals("\"c\" \"NEAR\" \"3\" x*", NeedMatcher.ftsQuery("c, NEAR/3 x"))   // punctuation is dropped, the slash splits the words
        assertEquals("", NeedMatcher.ftsQuery("  ?! "))
        assertEquals("\"éditer\" vidé*", NeedMatcher.ftsQuery("éditer vidé"))
    }

    @Test fun distanceCountsAnAdjacentSwapOnce() {
        assertEquals(0, NeedMatcher.distance("tern", "tern"))
        assertEquals(1, NeedMatcher.distance("tern", "tren"))
        assertEquals(1, NeedMatcher.distance("stowly", "stowy"))
        assertEquals(3, NeedMatcher.distance("kitten", "sitting"))
        assertEquals(3, NeedMatcher.distance("abc", "xyz"))
    }

    @Test fun allowedTyposByWordLength() {
        assertEquals(listOf(0, 0, 1, 1, 2, 2), listOf(1, 2, 3, 4, 5, 9).map(NeedMatcher::maxDistance))
    }

    private val library = listOf(
        Candidate(1, listOf("kmori/stowly", "kmori-stowly", "stowly")),
        Candidate(2, listOf("Tern")),
        Candidate(3, listOf("yt-dlp", "ytdlp")),
        Candidate(4, listOf("Paper Satellites")),
    )

    @Test fun aTypoWithinTheLimitMatches() {
        assertEquals(listOf(1L), NeedMatcher.typoMatches("stowlly", library))     // one extra letter, 7 letters
        assertEquals(listOf(1L), NeedMatcher.typoMatches("stwoly", library))      // a swap
        assertEquals(listOf(2L), NeedMatcher.typoMatches("tren", library))        // 4 letters, one swap
        assertEquals(listOf(4L), NeedMatcher.typoMatches("papr sattelites", library))
    }

    @Test fun beyondTheLimitAndTooShortWordsDoNotMatch() {
        assertEquals(emptyList<Long>(), NeedMatcher.typoMatches("tx", library))           // two letters need an exact word
        assertEquals(emptyList<Long>(), NeedMatcher.typoMatches("tarx", library))         // 4 letters allow one edit; "tern" is two away
        assertEquals(emptyList<Long>(), NeedMatcher.typoMatches("zebra", library))
        assertEquals(emptyList<Long>(), NeedMatcher.typoMatches("", library))
    }

    @Test fun everyQueryWordMustMatchAndBestComesFirst() {
        val two = listOf(Candidate(1, listOf("paper plane")), Candidate(2, listOf("paper satellites")), Candidate(3, listOf("papr satellites")))
        assertEquals(listOf(2L, 3L), NeedMatcher.typoMatches("paper satellites", two))   // exact before typo; 1 has no second word
        assertEquals(listOf(2L, 3L), NeedMatcher.typoMatches("paper satel", two))        // the last word may be a typo'd start
    }
}
