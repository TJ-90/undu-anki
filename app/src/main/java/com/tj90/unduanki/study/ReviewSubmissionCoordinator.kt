package com.tj90.unduanki.study

import com.tj90.unduanki.anki.AnkiGateway
import com.tj90.unduanki.anki.CardRef
import com.tj90.unduanki.anki.CardState
import com.tj90.unduanki.anki.Rating

interface ReviewJournal {
    fun pending(id: String, batchId: String?, ref: CardRef, before: CardState, rating: Rating, elapsedMs: Long)
    fun uncertain(id: String)
    fun confirmed(id: String)
}

enum class SubmissionResult { CONFIRMED, STALE, UNCERTAIN }

/** The caller must run this away from the UI thread and retain the journal across restarts. */
class ReviewSubmissionCoordinator(private val gateway: AnkiGateway, private val journal: ReviewJournal) {
    @Synchronized
    fun submit(id: String, batchId: String?, ref: CardRef, before: CardState, rating: Rating, elapsedMs: Long): SubmissionResult {
        require(elapsedMs >= 0)
        val fresh = try { gateway.readState(before.cardId) } catch (_: Exception) { return SubmissionResult.STALE }
        if (fresh != before) return SubmissionResult.STALE
        journal.pending(id, batchId, ref, before, rating, elapsedMs)
        return try {
            gateway.answer(before.cardId, rating, elapsedMs)
            val after = gateway.readState(before.cardId)
            if (after.cardId == before.cardId && after.reps == before.reps + 1 && after.lastReviewTimeSeconds > before.lastReviewTimeSeconds) {
                journal.confirmed(id)
                SubmissionResult.CONFIRMED
            } else {
                journal.uncertain(id)
                SubmissionResult.UNCERTAIN
            }
        } catch (_: Exception) {
            journal.uncertain(id)
            SubmissionResult.UNCERTAIN
        }
    }
}
