package dev.wolzard.recon.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * D23: a real version-1 database (built from the exported schemas/…/1.json, not from today's entity) is opened by
 * Room with MIGRATION_1_2. Room validates the migrated tables against the current entity when it opens, so a column
 * that is missing or has the wrong type fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before fun setUp() { ctx.deleteDatabase(name) }
    @After fun tearDown() { ctx.deleteDatabase(name) }

    private fun createV1(insertRow: Boolean) {
        val schema = Json.parseToJsonElement(File("schemas/dev.wolzard.recon.data.db.ReconDb/1.json").readText()).jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        schema["entities"]!!.jsonArray.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", it.jsonObject["tableName"]!!.jsonPrimitive.content)) }
        if (insertRow) db.execSQL(
            "INSERT INTO jobs (id, sharedText, url, localUri, state, failure, failureDetail, retryAtEpochMs, progress, analysisJson, resultJson, createdAt, updatedAt) " +
                "VALUES ('j1', 'look https://youtu.be/a', 'https://youtu.be/a', NULL, 'DONE', NULL, NULL, NULL, NULL, '{\"a\":1}', '{\"r\":1}', 10, 20)",
        )
        db.version = 1
        db.close()
    }

    private fun open() = Room.databaseBuilder(ctx, ReconDb::class.java, name).addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()

    @Test fun v1RowsKeepTheirDataAndGetNoContext() {
        createV1(insertRow = true)
        val room = open()
        try {
            val job = runBlocking { RoomJobStore(room).observeAll().first() }.single()
            assertEquals("j1", job.id)
            assertEquals("look https://youtu.be/a", job.sharedText)
            assertEquals("https://youtu.be/a", job.url)
            assertEquals("{\"a\":1}", job.analysisJson)
            assertEquals("{\"r\":1}", job.resultJson)
            assertEquals(10L, job.createdAt)
            assertNull(job.userContext)
        } finally {
            room.close()
        }
    }

    @Test fun migratedDatabaseStoresContextForNewJobs() {
        createV1(insertRow = false)
        val room = open()
        try {
            val store = RoomJobStore(room)
            val id = runBlocking { store.create("https://youtu.be/a", null, "  What song is this?  ") }
            assertEquals("What song is this?", runBlocking { store.get(id) }!!.userContext)
        } finally {
            room.close()
        }
    }

    @Test fun v1SchemaHasNoUserContextColumn() { // guards the test itself: it must start from the old shape
        createV1(insertRow = false)
        val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info(jobs)", null).use { c ->
            val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertEquals(false, "userContext" in cols)
        }
        db.close()
    }
}
