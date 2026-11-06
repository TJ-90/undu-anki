package com.tj90.unduanki.island

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tj90.unduanki.game.RewardService

/** All mutations, including the ticket receipt, are serialized by SQLite transactions. */
class IslandRepository(private val helper: SQLiteOpenHelper) {
    data class Snapshot(val stars: Int, val rescued: Boolean, val owned: Set<String>, val placements: Map<Int, String>)

    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE island_wallet (id INTEGER PRIMARY KEY CHECK(id=1), stars INTEGER NOT NULL CHECK(stars>=0), rescued INTEGER NOT NULL CHECK(rescued IN (0,1)))")
            db.execSQL("INSERT INTO island_wallet VALUES (1, 0, 0)")
            db.execSQL("CREATE TABLE island_owned (item_id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE island_placements (slot INTEGER PRIMARY KEY CHECK(slot BETWEEN 1 AND 6), item_id TEXT NOT NULL UNIQUE REFERENCES island_owned(item_id))")
            db.execSQL("CREATE TABLE island_receipts (ticket_id TEXT PRIMARY KEY REFERENCES tickets(id), collected INTEGER NOT NULL, reward INTEGER NOT NULL, rescue INTEGER NOT NULL)")
        }
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun snapshot(): Snapshot = transaction { db -> snapshot(db) }

    private fun snapshot(db: SQLiteDatabase): Snapshot {
        val wallet = db.rawQuery("SELECT stars, rescued FROM island_wallet WHERE id=1", null).use {
            check(it.moveToFirst()); it.getInt(0) to (it.getInt(1) != 0)
        }
        val owned = mutableSetOf<String>()
        db.rawQuery("SELECT item_id FROM island_owned", null).use { while (it.moveToNext()) owned.add(it.getString(0)) }
        val placements = mutableMapOf<Int, String>()
        db.rawQuery("SELECT slot, item_id FROM island_placements", null).use { while (it.moveToNext()) placements[it.getInt(0)] = it.getString(1) }
        return Snapshot(wallet.first, wallet.second, owned, placements)
    }

    /** Only a successful rewarded attempt should call this boundary; practice/failed runs must not. */
    fun settle(ticketId: String, collected: Int, won: Boolean, practice: Boolean): Boolean = transaction { db ->
        val reward = RewardService.reward(won, practice, collected) ?: return@transaction false
        val ticketEarned = db.rawQuery("SELECT status FROM tickets WHERE id=?", arrayOf(ticketId)).use { it.moveToFirst() && it.getString(0) == "earned" }
        if (!ticketEarned) return@transaction false
        val old = snapshot(db)
        val values = ContentValues().apply {
            put("ticket_id", ticketId); put("collected", collected.coerceAtMost(20)); put("reward", reward); put("rescue", if (old.rescued) 0 else 1)
        }
        if (db.insertWithOnConflict("island_receipts", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1L) return@transaction false
        db.execSQL("UPDATE island_wallet SET stars=stars+?, rescued=1 WHERE id=1", arrayOf(reward))
        check(db.update("tickets", ContentValues().apply { put("status", "settled") }, "id=? AND status='earned'", arrayOf(ticketId)) == 1)
        true
    }

    fun buy(itemId: String): Boolean = transaction { db ->
        val state = snapshot(db)
        val newBalance = RewardService.purchase(state.stars, state.owned, itemId) ?: return@transaction false
        db.insertOrThrow("island_owned", null, ContentValues().apply { put("item_id", itemId) })
        db.update("island_wallet", ContentValues().apply { put("stars", newBalance) }, "id=1", null)
        true
    }

    fun place(itemId: String, slot: Int): Boolean = transaction { db ->
        val state = snapshot(db)
        if (slot !in 1..6 || itemId !in state.owned || slot in state.placements) return@transaction false
        db.delete("island_placements", "item_id=?", arrayOf(itemId))
        db.insertOrThrow("island_placements", null, ContentValues().apply { put("slot", slot); put("item_id", itemId) })
        true
    }

    fun unplace(slot: Int): Boolean = transaction { db -> db.delete("island_placements", "slot=?", arrayOf(slot.toString())) == 1 }
}
