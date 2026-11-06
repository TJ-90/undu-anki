package com.tj90.unduanki.anki

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri

/** Read-only provider access for setup and per-card content. Call off the main thread. */
internal class AnkiDeckCatalog(private val resolver: ContentResolver) {
    data class DeckInfo(val id: Long, val name: String)

    /** False when the provider returns no cursor (collection not open) or the permission is missing. */
    fun isReadable(): Boolean = try {
        resolver.query(Uri.parse("$ROOT/decks"), null, null, null, null)?.use { true } ?: false
    } catch (_: SecurityException) {
        false
    }

    fun decks(): List<DeckInfo> {
        val cursor = resolver.query(Uri.parse("$ROOT/decks"), null, null, null, null)
            ?: throw IllegalStateException("AnkiDroid returned no deck list")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val dynIndex = it.getColumnIndex("deck_dyn")
                    val dynamic = dynIndex >= 0 && !it.isNull(dynIndex) && it.getInt(dynIndex) != 0
                    if (!dynamic) add(DeckInfo(it.long("deck_id"), it.string("deck_name")))
                }
            }.sortedBy { it.name }
        }
    }

    fun models(): Map<Long, ModelInfo> {
        val cursor = resolver.query(
            Uri.parse("$ROOT/models"), arrayOf("_id", "name", "field_names", "num_cards", "type"), null, null, null
        ) ?: throw IllegalStateException("AnkiDroid returned no note types")
        val rows = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(arrayOf(it.long("_id").toString(), it.string("name"), it.string("field_names"), it.long("type").toString()))
                }
            }
        }
        return rows.associate { row ->
            val id = row[0].toLong()
            id to ModelInfo(id, row[1], row[2].split('\u001f'), templates(id), cloze = row[3] == "1")
        }
    }

    private fun templates(modelId: Long): List<CardTemplate> {
        val cursor = resolver.query(Uri.parse("$ROOT/models/$modelId/templates"), null, null, null, null)
            ?: throw IllegalStateException("AnkiDroid returned no templates")
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(CardTemplate(it.string("question_format"), it.string("answer_format")))
            }
        }
    }

    /** Anki search syntax: includes child decks, matching what the scheduler treats as the selected deck. */
    fun notesInDeck(deckName: String): List<DeckNote> {
        val escaped = deckName.replace("\\", "\\\\").replace("\"", "\\\"").replace("*", "\\*").replace("_", "\\_")
        val cursor = resolver.query(Uri.parse("$ROOT/notes"), arrayOf("_id", "mid", "flds"), "deck:\"$escaped\"", null, null)
            ?: throw IllegalStateException("AnkiDroid returned no notes")
        return cursor.use { buildList { while (it.moveToNext()) add(it.toNote()) } }
    }

    /** Fresh content for presentation; nothing is copied into this app's storage. */
    fun note(noteId: Long): DeckNote? {
        require(noteId > 0)
        val cursor = resolver.query(Uri.parse("$ROOT/notes/$noteId"), arrayOf("_id", "mid", "flds"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid returned no note")
        return cursor.use { if (it.moveToFirst()) it.toNote() else null }
    }

    private fun Cursor.toNote() = DeckNote(long("_id"), long("mid"), string("flds").split('\u001f'))

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        check(!isNull(index)) { "Missing $column" }
        return getLong(index)
    }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndexOrThrow(column)
        check(!isNull(index)) { "Missing $column" }
        return getString(index)
    }

    private companion object { const val ROOT = "content://com.ichi2.anki.flashcards" }
}
