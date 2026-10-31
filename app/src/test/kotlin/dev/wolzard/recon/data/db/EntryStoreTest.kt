package dev.wolzard.recon.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.EntryLink
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.RepoFacts
import dev.wolzard.recon.core.research.LinkType
import dev.wolzard.recon.core.research.Maturity
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java).allowMainThreadQueries().build()
    private val jobs = RoomJobStore(db)
    private var tick = 0L
    private val entries = RoomEntryStore(db) { ++tick }
    @After fun close() = db.close()

    private fun finding(type: EntityType, name: String, title: String = name, verdict: Verdict = Verdict.CONFIRMED, basis: Basis = Basis.GITHUB_API, link: String? = null, stars: Int? = null, what: String = "", facts: RepoFacts? = null, fields: (Entity) -> Entity = { it }) =
        Finding(fields(Entity(type, name, null, Evidence.SPOKEN, "said")), "web", verdict, title, what, emptyList(), "", "", link, stars, basis, repoFacts = facts)

    /** A finished job carrying [findings] and a distinct video link. */
    private fun done(vararg findings: Finding, url: String = "https://youtu.be/${tick + 1}") = runBlocking {
        val id = jobs.create(url, null, normalizedUrl = url)
        val result = JobResult(AnalysisResult(summary = "s", confidence = 0.9), findings.toList(), emptyList(), null, "A")
        jobs.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), result)) }
        jobs.get(id)!!.also { runBlocking { entries.linkJob(it) } }
    }

    private fun list(q: String = "", types: Set<EntityType>? = null, limit: Int = 50) = runBlocking { entries.observeList(q, types, limit).first() }

    @Test fun twoVideosAboutTheSameRepoShareOneEntry() {
        done(finding(EntityType.REPO, "stowly", "kmori/stowly", link = "https://github.com/kmori/stowly", stars = 4100))
        done(finding(EntityType.REPO, "Stowly", "KMori/Stowly", stars = 4200))
        val e = list().single()
        assertEquals("github:kmori/stowly", e.key)
        assertEquals(2, e.videos)
        assertEquals(4200, e.stars)
        assertEquals("https://github.com/kmori/stowly", e.primaryLink)   // the first link is kept, a later finding only fills a gap
        assertEquals("Verified", e.status)
    }

    @Test fun reanalysingTheSameVideoCountsOnce() {
        val a = done(finding(EntityType.APP, "Obsidian", basis = Basis.AI_KNOWLEDGE, verdict = Verdict.BEST_MATCH), url = "https://youtu.be/same")
        runBlocking { entries.linkJob(a) }
        val again = done(finding(EntityType.APP, "Obsidian", basis = Basis.AI_KNOWLEDGE, verdict = Verdict.BEST_MATCH), url = "https://youtu.be/same")
        assertEquals(1, list().single().videos)
        assertEquals("Unverified", list().single().status)
        assertTrue(again.id != a.id)
    }

    @Test fun peopleAndClaimsMakeNoEntry() {
        done(finding(EntityType.PERSON, "Kenji Mori"), finding(EntityType.CLAIM, "It is fast."))
        assertTrue(list().isEmpty())
    }

    @Test fun sameTypeUnderAKnownNameAttachesButTwoReposWithOneNameDoNot() {
        done(finding(EntityType.APP, "Obsidian Notes", title = "Obsidian"))
        done(finding(EntityType.APP, "Obsidian"))
        assertEquals(1, list().size)
        done(finding(EntityType.REPO, "stowly", "kmori/stowly"))
        done(finding(EntityType.REPO, "stowly", "other/stowly"))
        val repos = list(types = setOf(EntityType.REPO))
        assertEquals(2, repos.size)
    }

    @Test fun aNameSharedAcrossTypesIsFlaggedNeverMerged() {
        done(finding(EntityType.APP, "Tern"))
        done(finding(EntityType.PRODUCT, "Tern"))
        val all = list()
        assertEquals(2, all.size)
        val flagged = all.single { it.needsAttention }
        assertEquals("product", flagged.type.wire)
        assertEquals("Possible duplicate of Tern", flagged.attentionReason)
    }

    @Test fun searchMatchesNameSummaryAndAliasByWordAndPrefix() {
        done(finding(EntityType.REPO, "stowly", "kmori/stowly", what = "Symlink farm for dotfiles"))
        done(finding(EntityType.BOOK, "Atomic Habits", what = "Small changes, big results"))
        assertEquals(listOf("kmori/stowly"), list("symlink").map { it.name })
        assertEquals(listOf("kmori/stowly"), list("stow").map { it.name })         // prefix
        assertEquals(listOf("Atomic Habits"), list("habits big").map { it.name })   // every word must match
        assertEquals(listOf("kmori/stowly"), list("kmori-stowly").map { it.name })  // alias
        assertTrue(list("AND OR NEAR").isEmpty())                                  // operators are plain words
        assertTrue(list("***").isEmpty())
        assertEquals(2, list("").size)
    }

    @Test fun typeFilterAndLimitPage() {
        repeat(5) { done(finding(EntityType.APP, "App $it")) }
        done(finding(EntityType.BOOK, "A Book"))
        assertEquals(5, list(types = setOf(EntityType.APP)).size)
        assertEquals(3, list(limit = 3).size)
        assertEquals("A Book", list().first().name)    // newest activity first
    }

    @Test fun deletingTheOnlyVideoHidesTheEntryAndDeletingOneKeepsTheOther() {
        val a = done(finding(EntityType.APP, "Tern"))
        val b = done(finding(EntityType.APP, "Tern"))
        runBlocking { jobs.delete(a.id) }
        assertEquals(1, list().single().videos)
        runBlocking { jobs.delete(b.id) }
        assertTrue(list().isEmpty())
    }

    @Test fun detailHasAliasesAndVideosNewestFirst() = runBlocking {
        val a = done(finding(EntityType.REPO, "stowly", "kmori/stowly"))
        val b = done(finding(EntityType.REPO, "stowly", "kmori/stowly"))
        val id = list().single().id
        assertEquals(listOf("kmori-stowly", "stowly"), entries.observe(id).first()!!.aliases)
        assertEquals(listOf(b.id, a.id), entries.observeVideos(id).first().map { it.id })
        assertNull(entries.observe(999).first())
    }

    @Test fun lastWordMatchesAsPrefixAndTypedOperatorsAreWords() {
        done(finding(EntityType.APP, "Symlinkr", what = "Links things"))
        assertEquals(1, list("symlink").size)     // prefix of a name token
        assertEquals(1, list("links symlink").size)
        assertEquals(0, list("symlink OR nothing").size)   // OR is a plain word, so every word must match
        assertEquals(0, list("NEAR").size)
    }

    private fun repoWith(facts: RepoFacts?, link: String? = "https://github.com/kmori/stowly") =
        finding(EntityType.REPO, "stowly", "kmori/stowly", link = link, stars = 4100, facts = facts)

    @Test fun repoFactsAreStoredAndTheLinkIsTyped() {
        done(repoWith(RepoFacts(pushedAt = 1_000, licenseSpdx = "MIT", language = "Rust", releaseTag = "v1.0", releaseAt = 900, maturity = Maturity.ESTABLISHED)))
        val e = runBlocking { entries.observe(list().single().id).first()!! }
        assertEquals(1_000L, e.pushedAt); assertEquals("MIT", e.license); assertEquals("Rust", e.language)
        assertEquals("v1.0", e.latestReleaseTag); assertEquals(900L, e.latestReleaseAt)
        assertFalse(e.archived)
        assertEquals(Maturity.ESTABLISHED, e.maturity)
        assertEquals(listOf(EntryLink("https://github.com/kmori/stowly", LinkType.REPO)), e.links)
    }

    @Test fun aNewerFindingReplacesFactsButNoFactsLeavesThem() {
        done(repoWith(RepoFacts(pushedAt = 1_000, licenseSpdx = "MIT", releaseTag = "v1.0", releaseAt = 900, maturity = Maturity.GROWING)))
        done(repoWith(RepoFacts(pushedAt = 2_000, archived = true, licenseSpdx = "Apache-2.0", maturity = Maturity.INACTIVE)))   // no release this time (rate limit)
        val id = list().single().id
        val e = runBlocking { entries.observe(id).first()!! }
        assertEquals(2_000L, e.pushedAt); assertEquals("Apache-2.0", e.license); assertTrue(e.archived); assertEquals(Maturity.INACTIVE, e.maturity)
        assertEquals("v1.0", e.latestReleaseTag)   // an earlier release is kept when this lookup found none
        done(repoWith(null))
        val again = runBlocking { entries.observe(id).first()!! }
        assertEquals(2_000L, again.pushedAt); assertEquals(Maturity.INACTIVE, again.maturity)
        assertEquals(1, again.links.size)           // the same link twice is one row
    }

    @Test fun unknownMaturityShowsNoBadgeAndAGuessHasNoFacts() {
        done(repoWith(RepoFacts(maturity = Maturity.UNKNOWN)))
        assertNull(runBlocking { entries.observe(list().single().id).first()!!.maturity })
        done(finding(EntityType.APP, "Obsidian", basis = Basis.AI_KNOWLEDGE, verdict = Verdict.BEST_MATCH))
        val app = list(types = setOf(EntityType.APP)).single()
        val e = runBlocking { entries.observe(app.id).first()!! }
        assertNull(e.maturity); assertNull(e.pushedAt); assertTrue(e.links.isEmpty())
    }

    @Test fun nonHttpsLinkIsNotKept() {
        done(repoWith(RepoFacts(maturity = Maturity.GROWING), link = "http://github.com/kmori/stowly"))
        assertTrue(runBlocking { entries.observe(list().single().id).first()!!.links }.isEmpty())
    }

    private val editor = { e: Entity -> e.copy(searchPhrases = listOf("edit videos on android", "trim and cut clips", "add subtitles to videos")) }

    @Test fun searchByNeedFindsAnEntryWhoseNameHasNoneOfTheWords() {
        done(finding(EntityType.APP, "Snipper", fields = editor))
        done(finding(EntityType.APP, "Tern"))
        assertEquals(listOf("Snipper"), list("edit videos on android").map { it.name })
        assertEquals(listOf("Snipper"), list("trim clip").map { it.name })              // earlier words whole, the last as a prefix
        assertEquals(listOf("Snipper"), list("subtitles").map { it.name })
        assertTrue(list("android subtitles").isEmpty())                               // every word, in one phrase
        assertEquals(setOf("Snipper", "Tern"), list().map { it.name }.toSet())
    }

    @Test fun typosFallBackToNamesAndAliasesOnlyWhenFullTextFindsNothing() {
        done(finding(EntityType.REPO, "stowly", "kmori/stowly", link = "https://github.com/kmori/stowly"))
        done(finding(EntityType.APP, "Tern"))
        assertEquals(listOf("kmori/stowly"), list("stowlly").map { it.name })          // one extra letter
        assertEquals(listOf("Tern"), list("tren").map { it.name })                    // a swap
        assertEquals(listOf("Tern"), list("tren", types = setOf(EntityType.APP)).map { it.name })
        assertTrue(list("tren", types = setOf(EntityType.REPO)).isEmpty())            // the type filter still applies
        assertTrue(list("zebra").isEmpty())
        assertEquals(listOf("Tern"), list("tern").map { it.name })                    // a hit needs no typo pass
    }

    @Test fun typoFallbackNeverListsAnEntryWithoutAVideo() {
        val a = done(finding(EntityType.APP, "Obsidian", basis = Basis.AI_KNOWLEDGE, verdict = Verdict.BEST_MATCH))
        assertEquals(1, list("obsidan").size)
        runBlocking { jobs.delete(a.id) }
        assertTrue(list("obsidan").isEmpty())
    }

    @Test fun structuredFieldsAreStoredAndAnEmptyAnswerNeverWipesThem() {
        val full = { e: Entity -> e.copy(problemSolved = "Cuts clips.", keyFeatures = listOf("Trim", "Subtitles"), useCases = listOf("Reels"), whenToUse = "Quick edits", whenNotToUse = "Colour grading", searchPhrases = listOf("cut video clips")) }
        done(finding(EntityType.APP, "Snipper", fields = full))
        done(finding(EntityType.APP, "Snipper"))   // a later video that says nothing more
        val e = runBlocking { entries.observe(list().single().id).first()!! }
        assertEquals("Cuts clips.", e.problemSolved); assertEquals(listOf("Trim", "Subtitles"), e.keyFeatures); assertEquals(listOf("Reels"), e.useCases)
        assertEquals("Quick edits", e.whenToUse); assertEquals("Colour grading", e.whenNotToUse)
        done(finding(EntityType.APP, "Snipper", fields = { it.copy(problemSolved = "Edits video on a phone.") }))
        assertEquals("Edits video on a phone.", runBlocking { entries.observe(e.id).first()!!.problemSolved })   // newer text replaces
        assertEquals(1, list("cut video clips").size)   // the phrase from the first video is still searchable
    }

    @Test fun phrasesAreUniquePerEntryAndCappedAtTwentyFour() {
        val many = { n: Int -> { e: Entity -> e.copy(searchPhrases = (1..8).map { "phrase number ${n * 10 + it}" }) } }
        repeat(4) { done(finding(EntityType.APP, "Snipper", fields = many(it))) }   // 32 distinct phrases offered
        done(finding(EntityType.APP, "Snipper", fields = many(0)))                  // the first eight again
        val id = list().single().id
        assertEquals(24, runBlocking { db.entries().phraseCount(id) })
    }

    @Test fun backfillFilesOldFinishedJobsOnce() = runBlocking {
        val id = jobs.create("https://youtu.be/old", null)
        val result = JobResult(AnalysisResult(summary = "s", confidence = 0.9), listOf(finding(EntityType.APP, "Tern")), emptyList(), null, "A")
        jobs.update(id) { it.copy(state = JobState.DONE, resultJson = Json.encodeToString(JobResult.serializer(), result)) }
        assertTrue(list().isEmpty())
        entries.backfill(); entries.backfill()
        assertEquals(1, list().single().videos)
        assertFalse(list().single().needsAttention)
    }

    @Test fun unreadableResultIsSkippedNotFatal() = runBlocking {
        val id = jobs.create("https://youtu.be/bad", null)
        jobs.update(id) { it.copy(state = JobState.DONE, resultJson = "{not json") }
        entries.linkJob(jobs.get(id)!!)
        assertTrue(list().isEmpty())
    }

    @Test fun nonHttpsLinksAreNotKept() {
        done(finding(EntityType.APP, "Tern", link = "javascript:alert(1)"))
        assertNull(list().single().primaryLink)
    }
}
