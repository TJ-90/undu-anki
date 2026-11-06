package com.tj90.unduanki.anki

/** No implementation may claim live support until the provider gate is verified. */
data class CardState(val cardId: Long, val reps: Int, val lastReviewTimeSeconds: Long)
enum class Rating { AGAIN, HARD, GOOD, EASY }

/** The provider can only read a card by note and ordinal, so journals must keep both. */
data class CardRef(val noteId: Long, val ordinal: Int) {
    init { require(noteId > 0 && ordinal >= 0) { "Invalid card reference" } }
}

/** Read-only by construction: recovery code receives this and so cannot submit a review. */
interface CardReader {
    fun readCard(ref: CardRef): CardState
}
interface AnkiGateway {
    fun readState(cardId: Long): CardState
    fun answer(cardId: Long, rating: Rating, elapsedMs: Long)
}
