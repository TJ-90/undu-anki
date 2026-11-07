package com.tj90.unduanki.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android's regex engine (ICU) is stricter than the desktop JVM that runs unit tests, so card screening
 * must also be exercised on a device. No AnkiDroid is needed.
 */
@RunWith(AndroidJUnit4::class)
class CardMappingOnDeviceTest {
    private val forward = CardTemplate("{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}")
    private val basic = ModelInfo(1, "Basic", listOf("Front", "Back"), listOf(forward))

    @Test fun plainForwardNoteIsMappedAndFormattedTextIsKept() {
        assertEquals(FieldMapping("Front", "Back") to null, DeckCompatibilityChecker.deriveMapping(basic))
        val mapped = CardContentMapper.map(listOf(forward), FieldMapping("Front", "Back"),
            mapOf("Front" to "<b>Why?</b>", "Back" to "Because<br>yes"))
        assertEquals(MappedCard("<b>Why?</b>", "Because<br>yes"), mapped)
        // An apostrophe stored as a numeric code is accepted (the real deck stores them this way).
        val apostrophe = CardContentMapper.map(listOf(forward), FieldMapping("Front", "Back"), mapOf("Front" to "What&#x27;s this?", "Back" to "It&#39;s a test"))
        assertEquals(MappedCard("What&#x27;s this?", "It&#39;s a test"), apostrophe)
    }

    @Test fun unsafeContentAndLayoutsAreRejectedOnDevice() {
        val mapping = FieldMapping("Front", "Back")
        for (bad in listOf("<img src=x>", "[sound:a.mp3]", "<script>x</script>", "\\(x\\)", "{{c1::x}}", "a & b")) {
            assertNull(bad, CardContentMapper.map(listOf(forward), mapping, mapOf("Front" to "Q", "Back" to bad)))
        }
        val conditional = basic.copy(templates = listOf(CardTemplate("{{#Front}}{{Front}}{{/Front}}", "{{Back}}")))
        assertNull(DeckCompatibilityChecker.deriveMapping(conditional).first)
    }

    @Test fun deckVerdictsWorkOnDevice() {
        val reversed = ModelInfo(2, "Reversed", listOf("Front", "Back"),
            listOf(forward, CardTemplate("{{Back}}", "{{FrontSide}}<hr id=answer>{{Front}}")))
        val models = mapOf(1L to basic, 2L to reversed)
        assertTrue(DeckCompatibilityChecker.evaluate(models, listOf(DeckNote(1, 1, listOf("Q", "A")))).usable)
        assertFalse(DeckCompatibilityChecker.evaluate(models, listOf(DeckNote(2, 2, listOf("Q", "A")))).usable)
        assertFalse(DeckCompatibilityChecker.evaluate(models, listOf(DeckNote(3, 1, listOf("<img src=x>", "A")))).usable)
    }
}
