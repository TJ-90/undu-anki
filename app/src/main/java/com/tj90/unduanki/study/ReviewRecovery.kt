package com.tj90.unduanki.study

import com.tj90.unduanki.anki.CardReader
import com.tj90.unduanki.anki.CardRef
import com.tj90.unduanki.anki.CardState
import com.tj90.unduanki.data.ProgressRepository

/** An attempt whose outcome was never confirmed locally. [ref] is null for rows from schema 1. */
data class UnresolvedReview(val id: String, val ref: CardRef?, val before: CardState, val status: String)

interface RecoveryStore {
    fun unresolved(): List<UnresolvedReview>
    fun markUncertain(id: String)
    fun confirm(id: String): Boolean
}

enum class RecoveryOutcome {
    /** Readback shows exactly one new review; the attempt was confirmed locally. */
    CONFIRMED,
    /** The card is exactly as before the attempt. This is not proof the write failed; a parent decides. */
    UNCHANGED,
    /** The card changed in a way that is not one clean review. A parent decides. */
    CONTRADICTORY,
    /** The card could not be read, or the journal cannot say which card it was. A parent decides. */
    UNREADABLE
}

/**
 * Resolves attempts left behind by process death or an unknown provider outcome. It receives only a
 * [CardReader], so it cannot submit a review: recovery never replays a stored write.
 */
class ReviewRecovery(private val reader: CardReader, private val store: RecoveryStore) {
    @Synchronized
    fun recover(attempt: UnresolvedReview): RecoveryOutcome {
        val outcome = classify(attempt)
        if (outcome == RecoveryOutcome.CONFIRMED) {
            store.confirm(attempt.id)
        } else if (attempt.status == "pending") {
            store.markUncertain(attempt.id)
        }
        return outcome
    }

    private fun classify(attempt: UnresolvedReview): RecoveryOutcome {
        val ref = attempt.ref ?: return RecoveryOutcome.UNREADABLE
        val now = try { reader.readCard(ref) } catch (_: Exception) { return RecoveryOutcome.UNREADABLE }
        val before = attempt.before
        return when {
            now.cardId != before.cardId -> RecoveryOutcome.CONTRADICTORY
            now == before -> RecoveryOutcome.UNCHANGED
            now.reps == before.reps + 1 && now.lastReviewTimeSeconds > before.lastReviewTimeSeconds -> RecoveryOutcome.CONFIRMED
            else -> RecoveryOutcome.CONTRADICTORY
        }
    }
}

class PersistentRecoveryStore(private val progress: ProgressRepository) : RecoveryStore {
    override fun unresolved(): List<UnresolvedReview> = progress.unresolvedAttempts().map {
        UnresolvedReview(
            it.id,
            if (it.noteId > 0 && it.ordinal >= 0) CardRef(it.noteId, it.ordinal) else null,
            CardState(it.cardId, it.preReps, it.preReviewTime),
            it.status
        )
    }
    override fun markUncertain(id: String) = progress.markUncertain(id)
    override fun confirm(id: String): Boolean = progress.confirm(id)
}
