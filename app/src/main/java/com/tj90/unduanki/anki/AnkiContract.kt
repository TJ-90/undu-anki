package com.tj90.unduanki.anki

/** Provider names from the pinned AnkiDroid contract; installed versions still need verification. */
internal object AnkiContract {
    private const val ROOT = "content://com.ichi2.anki.flashcards"
    const val SCHEDULE_URI = "$ROOT/schedule"
    const val SCHEDULE_SELECTION = "limit=?, deckID=?"
    val scheduleColumns = arrayOf("note_id", "ord", "button_count", "next_review_times", "media_files")
    val cardColumns = arrayOf("_id", "note_id", "ord", "reps", "last_review_time_secs", "queue", "due", "interval")

    fun scheduleArguments(deckId: Long): Array<String> {
        require(deckId > 0)
        return arrayOf("1", deckId.toString())
    }

    fun deckUri(deckId: Long): String {
        require(deckId > 0)
        return "$ROOT/decks/$deckId"
    }

    fun cardUri(noteId: Long, ordinal: Int): String {
        require(noteId > 0 && ordinal >= 0)
        return "$ROOT/notes/$noteId/cards/$ordinal"
    }
}
