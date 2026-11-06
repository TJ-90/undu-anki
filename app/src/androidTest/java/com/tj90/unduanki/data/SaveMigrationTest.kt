package com.tj90.unduanki.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tj90.unduanki.island.IslandRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Builds a schema-1 save exactly as the shipped v1 code created it, then upgrades it in place. */
@RunWith(AndroidJUnit4::class)
class SaveMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-${UUID.randomUUID()}.db"

    @After fun cleanup() { context.deleteDatabase(name) }

    private fun createVersionOne() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        db.execSQL("CREATE TABLE batches (id TEXT PRIMARY KEY, connection_id TEXT NOT NULL, target INTEGER NOT NULL CHECK(target BETWEEN 3 AND 10), seed INTEGER NOT NULL, difficulty TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('active','completed','abandoned'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_active_batch ON batches(state) WHERE state = 'active'")
        db.execSQL("CREATE TABLE attempts (id TEXT PRIMARY KEY, batch_id TEXT REFERENCES batches(id), card_id INTEGER NOT NULL, pre_reps INTEGER NOT NULL, pre_review_time INTEGER NOT NULL, rating TEXT NOT NULL, elapsed_ms INTEGER NOT NULL, status TEXT NOT NULL CHECK(status IN ('pending','confirmed','uncertain','abandoned'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_unresolved_attempt ON attempts((1)) WHERE status IN ('pending','uncertain')")
        db.execSQL("CREATE TABLE credits (batch_id TEXT NOT NULL REFERENCES batches(id), card_id INTEGER NOT NULL, PRIMARY KEY(batch_id, card_id))")
        db.execSQL("CREATE TABLE tickets (id TEXT PRIMARY KEY, batch_id TEXT NOT NULL UNIQUE REFERENCES batches(id), seed INTEGER NOT NULL, difficulty TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('earned','settled'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_earned_ticket ON tickets(status) WHERE status = 'earned'")
        IslandRepository.createSchema(db)
        db.execSQL("INSERT INTO batches VALUES ('b1','synthetic',3,42,'easy','active')")
        db.execSQL("INSERT INTO attempts VALUES ('old-pending','b1',7,1,10,'good',100,'pending')")
        db.execSQL("UPDATE island_wallet SET stars=33, rescued=1")
        db.version = 1
        db.close()
    }

    @Test fun versionOneSaveUpgradesWithoutLosingProgressAndRoutesOldAttemptsToAParent() {
        createVersionOne()
        val helper = GameDatabase(context, databaseName = name)
        val progress = ProgressRepository(helper)
        val old = progress.unresolvedAttempts().single()
        assertEquals("old-pending", old.id)
        assertEquals(7L, old.cardId)
        assertEquals(0L, old.noteId)
        assertEquals(-1, old.ordinal)
        helper.readableDatabase.rawQuery("SELECT stars, rescued FROM island_wallet", null).use {
            assertTrue(it.moveToFirst()); assertEquals(33, it.getInt(0)); assertEquals(1, it.getInt(1))
        }
        assertEquals(2, helper.readableDatabase.version)
        // An upgraded save can resolve the old attempt and record new ones with a readable reference.
        progress.abandonUnresolved("old-pending")
        progress.recordPending("new", "b1", 70, 0, 8, 0, 0, "easy", 5)
        assertEquals(70L, progress.unresolvedAttempts().single().noteId)
        helper.close()
    }
}
