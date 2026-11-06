package com.tj90.unduanki.study

import com.tj90.unduanki.anki.*
import org.junit.Assert.*
import org.junit.Test

class ReviewSubmissionCoordinatorTest {
    private val before = CardState(1, 3, 10)
    private class Journal : ReviewJournal {
        var status = ""
        override fun pending(id: String, batchId: String?, before: CardState, rating: Rating, elapsedMs: Long) { status = "pending" }
        override fun uncertain(id: String) { status = "uncertain" }
        override fun confirmed(id: String) { status = "confirmed" }
    }
    @Test fun nominalSuccessWithoutReadbackIsUncertainAndNeverRetried() {
        val journal = Journal()
        var calls = 0
        val gateway = object : AnkiGateway {
            override fun readState(cardId: Long) = before
            override fun answer(cardId: Long, rating: Rating, elapsedMs: Long) { calls++ }
        }
        assertEquals(SubmissionResult.UNCERTAIN, ReviewSubmissionCoordinator(gateway, journal).submit("a", null, before, Rating.AGAIN, 42))
        assertEquals(1, calls)
        assertEquals("uncertain", journal.status)
    }
    @Test fun changedPrestateDoesNotWrite() {
        val journal = Journal()
        val gateway = object : AnkiGateway {
            override fun readState(cardId: Long) = before.copy(reps = 4)
            override fun answer(cardId: Long, rating: Rating, elapsedMs: Long) { fail("stale review written") }
        }
        assertEquals(SubmissionResult.STALE, ReviewSubmissionCoordinator(gateway, journal).submit("a", null, before, Rating.GOOD, 1))
        assertEquals("", journal.status)
    }
}
