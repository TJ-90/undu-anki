package com.tj90.unduanki.study

import com.tj90.unduanki.anki.CardReader
import com.tj90.unduanki.anki.CardRef
import com.tj90.unduanki.anki.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRecoveryTest {
    private val ref = CardRef(5, 0)
    private val before = CardState(9, 3, 100)

    private class Store : RecoveryStore {
        val events = mutableListOf<String>()
        override fun unresolved() = emptyList<UnresolvedReview>()
        override fun markUncertain(id: String) { events += "uncertain:$id" }
        override fun confirm(id: String): Boolean { events += "confirm:$id"; return false }
    }

    private fun attempt(status: String = "uncertain", r: CardRef? = ref) = UnresolvedReview("a", r, before, status)
    private fun reader(state: () -> CardState) = object : CardReader { override fun readCard(ref: CardRef) = state() }

    @Test fun exactlyOneNewReviewIsConfirmed() {
        val store = Store()
        val outcome = ReviewRecovery(reader { before.copy(reps = 4, lastReviewTimeSeconds = 101) }, store).recover(attempt())
        assertEquals(RecoveryOutcome.CONFIRMED, outcome)
        assertEquals(listOf("confirm:a"), store.events)
    }

    @Test fun unchangedCardIsNotConfirmedAndNeedsAParent() {
        val store = Store()
        assertEquals(RecoveryOutcome.UNCHANGED, ReviewRecovery(reader { before }, store).recover(attempt("pending")))
        assertEquals(listOf("uncertain:a"), store.events)
    }

    @Test fun anAlreadyUncertainAttemptIsNotRewritten() {
        val store = Store()
        ReviewRecovery(reader { before }, store).recover(attempt("uncertain"))
        assertTrue(store.events.isEmpty())
    }

    @Test fun contradictoryStatesAreNeverConfirmed() {
        val cases = listOf(
            before.copy(reps = 5, lastReviewTimeSeconds = 101),   // two reviews
            before.copy(reps = 4, lastReviewTimeSeconds = 100),   // count moved, time did not
            before.copy(reps = 2, lastReviewTimeSeconds = 90),    // went backwards
            before.copy(reps = 3, lastReviewTimeSeconds = 120),   // time moved, count did not
            before.copy(cardId = 10, reps = 4, lastReviewTimeSeconds = 101) // a different card
        )
        for (state in cases) {
            val store = Store()
            assertEquals("$state", RecoveryOutcome.CONTRADICTORY, ReviewRecovery(reader { state }, store).recover(attempt("pending")))
            assertEquals(listOf("uncertain:a"), store.events)
        }
    }

    @Test fun unreadableCardAndLegacyRowsNeedAParent() {
        val failing = reader { error("provider gone") }
        val store = Store()
        assertEquals(RecoveryOutcome.UNREADABLE, ReviewRecovery(failing, store).recover(attempt("pending")))
        assertEquals(listOf("uncertain:a"), store.events)
        val legacy = Store()
        assertEquals(RecoveryOutcome.UNREADABLE, ReviewRecovery(reader { before }, legacy).recover(attempt("uncertain", r = null)))
        assertTrue(legacy.events.isEmpty())
    }
}
