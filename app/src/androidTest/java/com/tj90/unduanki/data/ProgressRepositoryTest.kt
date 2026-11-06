package com.tj90.unduanki.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var db: GameDatabase
    private lateinit var progress: ProgressRepository

    @Before fun setup() { db = GameDatabase(context, databaseName = name); progress = ProgressRepository(db) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun repeatedCardsCountOnceAndExhaustionIssuesTicket() {
        val batch = progress.startBatch("synthetic", 3, 42, "easy")
        for (i in 1..3) {
            progress.recordPending("attempt-$i", batch, 1, 0, 1, i, i.toLong(), "again", 100)
            assertFalse(progress.confirm("attempt-$i"))
        }
        assertTrue(progress.finishExhausted(batch))
        db.readableDatabase.rawQuery("SELECT count(*) FROM credits WHERE batch_id=?", arrayOf(batch)).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.readableDatabase.rawQuery("SELECT count(*) FROM tickets WHERE status='earned'", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test fun unresolvedAttemptBlocksAnotherBatchAndSurvivesReopen() {
        val batch = progress.startBatch("synthetic", 3, 42, "easy")
        progress.recordPending("pending", batch, 70, 0, 7, 1, 10, "good", 100)
        db.close()
        db = GameDatabase(context, databaseName = name)
        progress = ProgressRepository(db)
        val kept = progress.unresolvedAttempts().single()
        assertEquals("pending", kept.id)
        assertEquals(70L, kept.noteId)
        assertEquals(0, kept.ordinal)
        try { progress.startBatch("synthetic", 3, 1, "easy"); fail("unresolved attempt allowed another batch") } catch (_: IllegalStateException) { }
        progress.abandonUnresolved("pending")
        assertTrue(progress.unresolvedAttempts().isEmpty())
    }
    @Test fun cannotAbandonBatchWhileReviewIsUnresolved() {
        val batch = progress.startBatch("synthetic", 3, 42, "easy")
        progress.recordPending("pending", batch, 70, 0, 7, 1, 10, "good", 100)
        try { progress.abandonBatch(batch); fail("unresolved review discarded") } catch (_: IllegalStateException) { }
        progress.abandonUnresolved("pending")
        progress.abandonBatch(batch)
        val next = progress.startBatch("new-deck", 3, 43, "normal")
        assertNotEquals(batch, next)
    }
}
