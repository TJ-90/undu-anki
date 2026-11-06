package com.tj90.unduanki.anki

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray

/** Read-only provider foundation. Run provider calls away from the main thread. */
internal class AnkiDroidGateway(private val resolver: ContentResolver, private val deckId: Long) : AnkiGateway {
    data class ScheduledCard(
        val noteId: Long,
        val ordinal: Int,
        val cardId: Long,
        val buttonCount: Int,
        val nextReviewTimes: List<String>,
        val mediaFiles: List<String>,
        val state: CardState,
        val queue: Int,
        val due: Long,
        val interval: Long
    )

    private var current: ScheduledCard? = null

    /** Returns null only for an empty queue on a deck that still exists. */
    fun nextCard(): ScheduledCard? {
        current = null
        ensureDeckExists()
        val cursor = resolver.query(
            Uri.parse(AnkiContract.SCHEDULE_URI), AnkiContract.scheduleColumns,
            AnkiContract.SCHEDULE_SELECTION, AnkiContract.scheduleArguments(deckId), null
        ) ?: throw IllegalStateException("AnkiDroid returned no scheduler cursor")
        val scheduled = cursor.use {
            if (!it.moveToFirst()) null else {
                val noteId = it.requiredLong("note_id")
                val ordinal = it.requiredLong("ord").toIntExact("ord")
                val count = it.requiredLong("button_count").toIntExact("button_count")
                require(noteId > 0 && ordinal >= 0 && count in 2..4) { "Invalid scheduler data" }
                val times = it.stringArray("next_review_times")
                val media = it.stringArray("media_files")
                require(times.size == count) { "Invalid scheduler button data" }
                Triple(noteId, ordinal, Triple(count, times, media))
            }
        }
        if (scheduled == null) {
            ensureDeckExists()
            return null
        }
        val snapshot = readCard(scheduled.first, scheduled.second)
        val result = ScheduledCard(
            scheduled.first, scheduled.second, snapshot.state.cardId,
            scheduled.third.first, scheduled.third.second, scheduled.third.third,
            snapshot.state, snapshot.queue, snapshot.due, snapshot.interval
        )
        current = result
        return result
    }

    override fun readState(cardId: Long): CardState {
        val card = checkNotNull(current) { "No scheduled card is bound" }
        check(card.cardId == cardId) { "Card is not the current scheduled card" }
        val state = readCard(card.noteId, card.ordinal).state
        check(state.cardId == cardId) { "Scheduled card identity changed" }
        return state
    }

    override fun answer(cardId: Long, rating: Rating, elapsedMs: Long): Nothing {
        // The current interface does not carry a verified mapping for the installed provider.
        // A provider update must not be enabled until the documented compatibility gate passes.
        throw UnsupportedOperationException("Live AnkiDroid review is not verified")
    }

    private data class Snapshot(val state: CardState, val queue: Int, val due: Long, val interval: Long)

    private fun readCard(noteId: Long, ordinal: Int): Snapshot {
        val cursor = resolver.query(Uri.parse(AnkiContract.cardUri(noteId, ordinal)), AnkiContract.cardColumns, null, null, null)
            ?: throw IllegalStateException("AnkiDroid returned no card cursor")
        return cursor.use {
            check(it.moveToFirst()) { "Scheduled card no longer exists" }
            val actualNote = it.requiredLong("note_id")
            val actualOrdinal = it.requiredLong("ord").toIntExact("ord")
            val cardId = it.requiredLong("_id")
            val reps = it.requiredLong("reps").toIntExact("reps")
            check(actualNote == noteId && actualOrdinal == ordinal && cardId > 0 && reps >= 0) { "Invalid card identity or repetitions" }
            val lastIndex = it.getColumnIndexOrThrow("last_review_time_secs")
            val lastReview = if (it.isNull(lastIndex)) {
                check(reps == 0) { "Missing last review time" }
                0L
            } else it.getLong(lastIndex)
            check(lastReview >= 0) { "Invalid last review time" }
            Snapshot(CardState(cardId, reps, lastReview), it.requiredLong("queue").toIntExact("queue"), it.requiredLong("due"), it.requiredLong("interval"))
        }
    }

    private fun ensureDeckExists() {
        val cursor = resolver.query(Uri.parse(AnkiContract.deckUri(deckId)), arrayOf("deck_id"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid returned no deck cursor")
        cursor.use { check(it.moveToFirst() && it.requiredLong("deck_id") == deckId) { "Selected deck is unavailable" } }
    }

    private fun Cursor.requiredLong(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        check(!isNull(index)) { "Missing $column" }
        return getLong(index)
    }

    private fun Cursor.stringArray(column: String): List<String> {
        val index = getColumnIndexOrThrow(column)
        check(!isNull(index)) { "Missing $column" }
        val values = JSONArray(getString(index))
        return List(values.length()) { values.getString(it) }
    }

    private fun Long.toIntExact(column: String): Int {
        check(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Invalid $column" }
        return toInt()
    }
}
