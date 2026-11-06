package com.tj90.unduanki.anki

/** No implementation may claim live support until the provider gate is verified. */
data class CardState(val cardId: Long, val reps: Int, val lastReviewTimeSeconds: Long)
enum class Rating { AGAIN, HARD, GOOD, EASY }
interface AnkiGateway {
    fun readState(cardId: Long): CardState
    fun answer(cardId: Long, rating: Rating, elapsedMs: Long)
}
