package com.tj90.unduanki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Live and synthetic progress must never share a database. */
class GameDatabase(context: Context, demo: Boolean = false) :
    SQLiteOpenHelper(context, if (demo) "demo-progress.db" else "game-progress.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE batches (id TEXT PRIMARY KEY, connection_id TEXT NOT NULL, target INTEGER NOT NULL CHECK(target BETWEEN 3 AND 10), state TEXT NOT NULL CHECK(state IN ('active','completed','abandoned'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_active_batch ON batches(state) WHERE state = 'active'")
        db.execSQL("CREATE TABLE attempts (id TEXT PRIMARY KEY, batch_id TEXT REFERENCES batches(id), card_id INTEGER NOT NULL, pre_reps INTEGER NOT NULL, pre_review_time INTEGER NOT NULL, rating TEXT NOT NULL, elapsed_ms INTEGER NOT NULL, status TEXT NOT NULL CHECK(status IN ('pending','confirmed','uncertain','abandoned'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_unresolved_attempt ON attempts((1)) WHERE status IN ('pending','uncertain')")
        db.execSQL("CREATE TABLE credits (batch_id TEXT NOT NULL REFERENCES batches(id), card_id INTEGER NOT NULL, PRIMARY KEY(batch_id, card_id))")
        db.execSQL("CREATE TABLE tickets (id TEXT PRIMARY KEY, batch_id TEXT NOT NULL UNIQUE REFERENCES batches(id), seed INTEGER NOT NULL, difficulty TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('earned','settled'))) ")
        db.execSQL("CREATE UNIQUE INDEX one_earned_ticket ON tickets(status) WHERE status = 'earned'")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("No migration from schema $oldVersion to $newVersion")
    }
}
