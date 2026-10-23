package dev.wolzard.recon.core.library

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalKeyTest {
    private fun f(type: EntityType, name: String, title: String = name) =
        Finding(Entity(type, name, null, Evidence.SPOKEN, ""), "web", Verdict.BEST_MATCH, title, "", emptyList(), "", "", null, null, Basis.WEB_SOURCES)

    @Test fun confirmedRepoKeyIsOwnerSlashRepoLowercase() =
        assertEquals("github:kmori/stowly", CanonicalKey.of(f(EntityType.REPO, "stowly", "KMori/Stowly")))

    @Test fun repoWithoutOwnerFallsBackToTypeAndSlug() =
        assertEquals("repo:stowly", CanonicalKey.of(f(EntityType.REPO, "Stowly")))

    @Test fun otherTypesAreTypeColonSlug() {
        assertEquals("app:obsidian", CanonicalKey.of(f(EntityType.APP, "Obsidian")))
        assertEquals("book:atomic-habits", CanonicalKey.of(f(EntityType.BOOK, "Atomic  Habits!")))
        assertEquals("media:glass-harbour", CanonicalKey.of(f(EntityType.MEDIA, "x", "Glass Harbour")))
    }

    @Test fun peopleAndClaimsAreNeverKept() {
        assertNull(CanonicalKey.of(f(EntityType.PERSON, "Kenji Mori")))
        assertNull(CanonicalKey.of(f(EntityType.CLAIM, "It halves startup time.")))
        assertFalse(CanonicalKey.keeps(EntityType.PERSON))
        assertTrue(CanonicalKey.keeps(EntityType.PLACE))
    }

    @Test fun nameWithNoLettersOrDigitsHasNoKey() = assertNull(CanonicalKey.of(f(EntityType.APP, "***")))

    @Test fun aliasesAreSlugsWithTheRepoBareName() =
        assertEquals(listOf("stowly", "kmori-stowly"), CanonicalKey.aliases(f(EntityType.REPO, "stowly", "kmori/stowly")))

    @Test fun longNamesAreCut() = assertEquals(CanonicalKey.MAX_NAME, CanonicalKey.slug("a".repeat(500)).length)
}
