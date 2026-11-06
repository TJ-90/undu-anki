package com.tj90.unduanki.study

import android.content.SharedPreferences
import com.tj90.unduanki.anki.FieldMapping
import org.json.JSONObject
import java.util.UUID

/** Parent-chosen live connection. Holds IDs and field names only, never card text. */
class LiveSettings(private val prefs: SharedPreferences) {
    val deckId: Long get() = prefs.getLong("deck-id", 0)
    val deckName: String get() = prefs.getString("deck-name", "") ?: ""
    val ankiVersion: String get() = prefs.getString("anki-version", "") ?: ""
    val connectionId: String get() = prefs.getString("connection-id", "") ?: ""
    val configured: Boolean get() = deckId > 0 && connectionId.isNotEmpty()

    fun mappings(): Map<Long, FieldMapping> {
        val json = JSONObject(prefs.getString("mappings", "{}") ?: "{}")
        return json.keys().asSequence().associate { key ->
            val entry = json.getJSONObject(key)
            key.toLong() to FieldMapping(entry.getString("q"), entry.getString("a"))
        }
    }

    /** A new binding gets a new connection ID so batches from another deck are never mixed in. */
    fun save(deckId: Long, deckName: String, ankiVersion: String, mappings: Map<Long, FieldMapping>) {
        require(deckId > 0 && mappings.isNotEmpty())
        val json = JSONObject()
        for ((model, mapping) in mappings) json.put(model.toString(), JSONObject().put("q", mapping.question).put("a", mapping.answer))
        prefs.edit()
            .putLong("deck-id", deckId).putString("deck-name", deckName).putString("anki-version", ankiVersion)
            .putString("mappings", json.toString()).putString("connection-id", UUID.randomUUID().toString())
            .apply()
    }
}
