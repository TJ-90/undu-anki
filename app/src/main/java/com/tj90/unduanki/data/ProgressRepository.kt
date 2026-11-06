package com.tj90.unduanki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Mutations are transactional; provider writes must happen separately and are never replayed here. */
class ProgressRepository(private val helper: GameDatabase) {
    data class UnresolvedAttempt(val id: String, val cardId: Long, val preReps: Int, val preReviewTime: Long, val status: String)

    /** Recovery inspects these records and must never automatically resubmit them. */
    fun unresolvedAttempts(): List<UnresolvedAttempt> = helper.readableDatabase.rawQuery(
        "SELECT id, card_id, pre_reps, pre_review_time, status FROM attempts WHERE status IN ('pending','uncertain')", null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(UnresolvedAttempt(cursor.getString(0), cursor.getLong(1), cursor.getInt(2), cursor.getLong(3), cursor.getString(4)))
        }
    }

    /** Parent decision: the previous write is not replayed; fetch a fresh scheduled card. */
    fun abandonUnresolved(id: String) {
        check(helper.writableDatabase.update("attempts", values("status" to "abandoned"), "id=? AND status IN ('pending','uncertain')", arrayOf(id)) == 1)
    }

    fun startBatch(connectionId: String, target: Int, seed: Long, difficulty: String): String {
        require(difficulty in setOf("easy", "normal"))
        require(target in 3..10)
        val db = helper.writableDatabase
        return transaction(db) {
            check(!exists(db, "SELECT 1 FROM tickets WHERE status='earned'"))
            check(!exists(db, "SELECT 1 FROM attempts WHERE status IN ('pending','uncertain')"))
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("batches", null, values("id" to id, "connection_id" to connectionId, "target" to target, "seed" to seed, "difficulty" to difficulty, "state" to "active"))
            id
        }
    }

    fun recordPending(id: String, batchId: String?, cardId: Long, reps: Int, reviewTime: Long, rating: String, elapsedMs: Long) {
        require(rating in setOf("again", "hard", "good", "easy") && elapsedMs >= 0)
        helper.writableDatabase.insertOrThrow("attempts", null, values("id" to id, "batch_id" to batchId, "card_id" to cardId, "pre_reps" to reps, "pre_review_time" to reviewTime, "rating" to rating, "elapsed_ms" to elapsedMs, "status" to "pending"))
    }

    fun markUncertain(id: String) {
        val count = helper.writableDatabase.update("attempts", values("status" to "uncertain"), "id=? AND status='pending'", arrayOf(id))
        check(count == 1)
    }

    /** Confirm only after externally verified readback. A repeated card earns one credit. */
    fun confirm(id: String): Boolean {
        val db = helper.writableDatabase
        return transaction(db) {
            val row = db.rawQuery("SELECT batch_id, card_id FROM attempts WHERE id=? AND status IN ('pending','uncertain')", arrayOf(id)).use {
                check(it.moveToFirst())
                Pair(if (it.isNull(0)) null else it.getString(0), it.getLong(1))
            }
            db.update("attempts", values("status" to "confirmed"), "id=?", arrayOf(id))
            val batch = row.first ?: return@transaction false
            check(exists(db, "SELECT 1 FROM batches WHERE id=? AND state='active'", arrayOf(batch)))
            db.insertWithOnConflict("credits", null, values("batch_id" to batch, "card_id" to row.second), SQLiteDatabase.CONFLICT_IGNORE)
            val counts = db.rawQuery("SELECT target, (SELECT COUNT(*) FROM credits WHERE batch_id=?) FROM batches WHERE id=?", arrayOf(batch, batch)).use { it.moveToFirst(); it.getInt(0) to it.getInt(1) }
            if (counts.second >= counts.first) issueTicket(db, batch) else false
        }
    }

    /** Call only when the scheduler positively reports exhaustion, not on errors. */
    fun finishExhausted(batchId: String): Boolean {
        val db = helper.writableDatabase
        return transaction(db) {
            check(!exists(db, "SELECT 1 FROM attempts WHERE status IN ('pending','uncertain')"))
            check(exists(db, "SELECT 1 FROM batches WHERE id=? AND state='active'", arrayOf(batchId)))
            if (exists(db, "SELECT 1 FROM credits WHERE batch_id=?", arrayOf(batchId))) issueTicket(db, batchId) else false
        }
    }

    private fun issueTicket(db: SQLiteDatabase, batch: String): Boolean {
        val config = db.rawQuery("SELECT seed, difficulty FROM batches WHERE id=? AND state='active'", arrayOf(batch)).use { check(it.moveToFirst()); it.getLong(0) to it.getString(1) }
        db.insertOrThrow("tickets", null, values("id" to UUID.randomUUID().toString(), "batch_id" to batch, "seed" to config.first, "difficulty" to config.second, "status" to "earned"))
        check(db.update("batches", values("state" to "completed"), "id=? AND state='active'", arrayOf(batch)) == 1)
        return true
    }

    private fun exists(db: SQLiteDatabase, sql: String, args: Array<String> = emptyArray()) = db.rawQuery(sql, args).use { it.moveToFirst() }
    private fun <T> transaction(db: SQLiteDatabase, block: () -> T): T {
        db.beginTransaction()
        try { val result = block(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
        for ((key, value) in pairs) when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Long -> put(key, value)
            is Int -> put(key, value)
            else -> error("Unsupported value")
        }
    }
}
