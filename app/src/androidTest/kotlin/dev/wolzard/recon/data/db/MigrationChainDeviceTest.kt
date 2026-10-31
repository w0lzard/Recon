package dev.wolzard.recon.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalysisResult
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.Verdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D39: the whole chain, v5 to the latest version, through Room's MigrationTestHelper (it checks the migrated schema against
 * the exported JSON). The v5 database holds a repo seen through two links, a failed job and an in-flight job; after the
 * migrations the backfill files the two finished jobs under one entry and nothing else, and running it again changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainDeviceTest {
    private val name = "migration-chain-device-test.db"

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ReconDb::class.java)

    private val migrations = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

    @Test fun v5ChainToLatestMergesTheRepoSkipsUnfinishedJobsAndBackfillsOnce() = runBlocking {
        val finding = Finding(Entity(EntityType.REPO, "stowly", null, Evidence.SPOKEN, "said"), "web", Verdict.CONFIRMED, "kmori/stowly", "Dotfile manager", emptyList(), "", "", "https://github.com/kmori/stowly", 4100, Basis.GITHUB_API)
        val result = Json.encodeToString(JobResult.serializer(), JobResult(AnalysisResult(summary = "s", confidence = 0.9), listOf(finding), emptyList(), null, "A")).replace("'", "''")
        helper.createDatabase(name, 5).use { db ->
            fun job(id: String, url: String, state: String, at: Long) = db.execSQL(
                "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt, normalizedUrl) " +
                    "VALUES ('$id', '$url', '$url', NULL, '$state', NULL, NULL, NULL, NULL, NULL, '$result', $at, $at, '$url')",
            )
            job("a", "https://youtu.be/a", "DONE", 1)
            job("b", "https://www.instagram.com/reel/b/", "DONE", 2)   // another URL form for the same repo
            job("failed", "https://youtu.be/f", "FAILED", 3)
            job("busy", "https://youtu.be/r", "ANALYSING", 4)
        }
        helper.runMigrationsAndValidate(name, 9, true, *migrations).close()

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), ReconDb::class.java, name).addMigrations(*migrations).build()
        try {
            val store = RoomEntryStore(room) { 100 }
            assertEquals(emptyList<Long>(), store.observeList("", null, 10).first().map { it.id })   // migrations alone file nothing
            store.backfill()
            val e = store.observeList("", null, 10).first().single()
            assertEquals("github:kmori/stowly", e.key)
            assertEquals(2, e.videos)
            assertEquals(listOf("b", "a"), store.observeVideos(e.id).first().map { it.id })
            room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entry_jobs WHERE jobId IN ('failed','busy')").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            room.openHelper.readableDatabase.query("SELECT rowid FROM entries_fts WHERE entries_fts MATCH 'kmori'").use { assertEquals(listOf(e.id), generateSequence { if (it.moveToNext()) it.getLong(0) else null }.toList()) }
            assertEquals(listOf(e.id), store.observeList("stowly", null, 10).first().map { it.id })
            store.backfill()
            assertEquals(e, store.observeList("", null, 10).first().single())
        } finally {
            room.close()
        }
    }
}
