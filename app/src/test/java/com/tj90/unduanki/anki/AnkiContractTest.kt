package com.tj90.unduanki.anki

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnkiContractTest {
    @Test fun schedulerSelectionUsesProviderSyntaxAndExplicitDeck() {
        assertEquals("limit=?, deckID=?", AnkiContract.SCHEDULE_SELECTION)
        assertArrayEquals(arrayOf("1", "123"), AnkiContract.scheduleArguments(123))
    }

    @Test fun cardPathRetainsNoteAndOrdinal() {
        assertEquals("content://com.ichi2.anki.flashcards/notes/123/cards/0", AnkiContract.cardUri(123, 0))
        assertThrows(IllegalArgumentException::class.java) { AnkiContract.cardUri(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { AnkiContract.cardUri(123, -1) }
        assertThrows(IllegalArgumentException::class.java) { AnkiContract.scheduleArguments(0) }
    }

    @Test fun everyRatingHasADistinctProviderButton() {
        assertEquals(listOf(1, 2, 3, 4), Rating.values().map { AnkiContract.ease(it) })
    }
}
