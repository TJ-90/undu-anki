package com.tj90.unduanki.study

import com.tj90.unduanki.anki.*
import org.junit.Assert.*
import org.junit.Test

class ReviewSubmissionCoordinatorTest {
    private val before = CardState(1, 3, 10)
    private val ref = CardRef(5, 0)
    private class Journal : ReviewJournal {
        var status = ""
        override fun pending(id: String, batchId: String?, ref: CardRef, before: CardState, rating: Rating, elapsedMs: Long) { status = "pending" }
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
        assertEquals(SubmissionResult.UNCERTAIN, ReviewSubmissionCoordinator(gateway, journal).submit("a", null, ref, before, Rating.AGAIN, 42))
        assertEquals(1, calls)
        assertEquals("uncertain", journal.status)
    }
    @Test fun changedPrestateDoesNotWrite() {
        val journal = Journal()
        val gateway = object : AnkiGateway {
            override fun readState(cardId: Long) = before.copy(reps = 4)
            override fun answer(cardId: Long, rating: Rating, elapsedMs: Long) { fail("stale review written") }
        }
        assertEquals(SubmissionResult.STALE, ReviewSubmissionCoordinator(gateway, journal).submit("a", null, ref, before, Rating.GOOD, 1))
        assertEquals("", journal.status)
    }
    @Test fun successfulReadbackConfirmsEveryRating() {
        for (rating in Rating.entries) {
            val journal = Journal()
            var state = before
            val gateway = object : AnkiGateway {
                override fun readState(cardId: Long) = state
                override fun answer(cardId: Long, rating: Rating, elapsedMs: Long) {
                    state = before.copy(reps = before.reps + 1, lastReviewTimeSeconds = 11)
                }
            }
            assertEquals(SubmissionResult.CONFIRMED, ReviewSubmissionCoordinator(gateway, journal).submit("id", "batch", ref, before, rating, 10))
            assertEquals("confirmed", journal.status)
        }
    }
    @Test fun providerExceptionIsUncertainAfterOneWrite() {
        val journal = Journal()
        var writes = 0
        val gateway = object : AnkiGateway {
            override fun readState(cardId: Long) = before
            override fun answer(cardId: Long, rating: Rating, elapsedMs: Long) { writes++; error("unknown outcome") }
        }
        assertEquals(SubmissionResult.UNCERTAIN, ReviewSubmissionCoordinator(gateway, journal).submit("id", null, ref, before, Rating.EASY, 10))
        assertEquals(1, writes)
        assertEquals("uncertain", journal.status)
    }
}
