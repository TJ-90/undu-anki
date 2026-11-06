package com.tj90.unduanki.study

import com.tj90.unduanki.anki.CardState
import com.tj90.unduanki.anki.Rating
import com.tj90.unduanki.data.ProgressRepository
import java.util.Locale

class PersistentReviewJournal(private val progress: ProgressRepository) : ReviewJournal {
    override fun pending(id: String, batchId: String?, before: CardState, rating: Rating, elapsedMs: Long) {
        progress.recordPending(id, batchId, before.cardId, before.reps, before.lastReviewTimeSeconds, rating.name.lowercase(Locale.ROOT), elapsedMs)
    }
    override fun uncertain(id: String) = progress.markUncertain(id)
    override fun confirmed(id: String) { progress.confirm(id) }
}
