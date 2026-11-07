package com.tj90.unduanki.anki

import org.junit.Assert.*
import org.junit.Test

class DeckCompatibilityTest {
    private val forward = CardTemplate("{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}")
    private val basic = ModelInfo(1, "Basic", listOf("Front", "Back"), listOf(forward))
    private val reversed = ModelInfo(2, "Basic (and reversed card)", listOf("Front", "Back"),
        listOf(forward, CardTemplate("{{Back}}", "{{FrontSide}}<hr id=answer>{{Front}}")))
    private val cloze = ModelInfo(3, "Cloze", listOf("Text", "Back Extra"),
        listOf(CardTemplate("{{cloze:Text}}", "{{cloze:Text}}<br>{{Back Extra}}")), cloze = true)
    private val models = listOf(basic, reversed, cloze).associateBy { it.id }
    private fun note(id: Long, model: Long, vararg fields: String) = DeckNote(id, model, fields.toList())

    @Test fun derivesMappingFromTheSingleForwardTemplate() {
        assertEquals(FieldMapping("Front", "Back") to null, DeckCompatibilityChecker.deriveMapping(basic))
    }

    @Test fun plainBasicDeckIsUsable() {
        val result = DeckCompatibilityChecker.evaluate(models, listOf(note(1, 1, "Q1", "A1"), note(2, 1, "<b>Q2</b>", "A2")))
        assertTrue(result.usable)
        assertEquals(FieldMapping("Front", "Back"), result.mappingFor(1))
        assertTrue(result.summary().startsWith("All 2 notes can be shown"))
    }

    @Test fun reversedAndClozeNoteTypesBlockTheDeckWithAReason() {
        for ((model, text) in listOf(reversed to "2 card templates", cloze to "cloze")) {
            val result = DeckCompatibilityChecker.evaluate(models, listOf(note(1, model.id, "Q", "A")))
            assertFalse(result.usable)
            assertNull(result.mappingFor(model.id))
            assertTrue(result.summary(), result.summary().contains(text))
        }
    }

    @Test fun oneUnsupportedNoteBlocksTheWholeDeckInsteadOfBeingSkipped() {
        val result = DeckCompatibilityChecker.evaluate(models,
            listOf(note(1, 1, "Q", "A"), note(2, 1, "<img src=x>", "A"), note(3, 1, "Q", "[sound:a.mp3]")))
        assertFalse(result.usable)
        assertEquals(1, result.verdicts.single().supportedNotes)
        assertEquals(2, result.verdicts.single().unsupportedNotes)
    }

    @Test fun summaryNamesTheActualReasonsWithCountsAndNoCardText() {
        val result = DeckCompatibilityChecker.evaluate(models, listOf(
            note(1, 1, "Q", "A"),
            note(2, 1, "secret one", "<div class=\"x\">secret two</div>"),
            note(3, 1, "secret three", "<div style=\"y\">secret four</div>"),
            note(4, 1, "Q", "   "),
            note(5, 1, "Q", "a &rarr; b"),
            note(6, 1, "<code>secret five</code>", "A")
        ))
        val summary = result.summary()
        assertEquals(5, result.verdicts.single().unsupportedNotes)
        assertTrue(summary, summary.contains("5 of 6 notes cannot be shown"))
        assertTrue(summary, summary.contains("2 × a field has a <div> tag with extra settings"))
        assertTrue(summary, summary.contains("1 × the answer is empty"))
        assertTrue(summary, summary.contains("1 × a field contains the character code &rarr;"))
        assertTrue(summary, summary.contains("1 × a field contains a <code> tag"))
        assertFalse(summary, summary.contains("secret"))
    }

    @Test fun mixedDeckReportsOnlyTheProblemTypes() {
        val result = DeckCompatibilityChecker.evaluate(models, listOf(note(1, 1, "Q", "A"), note(2, 2, "Q", "A")))
        assertFalse(result.usable)
        assertTrue(result.summary().contains("Basic (and reversed card)"))
        assertFalse(result.summary().contains("Basic:"))
    }

    @Test fun layoutsWithConditionalsExtraFieldsOrSharedFieldsAreRejected() {
        for (template in listOf(
            CardTemplate("{{Front}} {{Back}}", "{{Back}}"),
            CardTemplate("{{#Front}}{{Front}}{{/Front}}", "{{Back}}"),
            CardTemplate("{{Front}}", "{{Front}}"),
            CardTemplate("{{Missing}}", "{{Back}}")
        )) assertNull(template.toString(), DeckCompatibilityChecker.deriveMapping(basic.copy(templates = listOf(template))).first)
    }

    @Test fun emptyDeckUnknownTypeAndFieldCountMismatchAreNotUsable() {
        assertFalse(DeckCompatibilityChecker.evaluate(models, emptyList()).usable)
        assertFalse(DeckCompatibilityChecker.evaluate(models, listOf(note(1, 99, "Q", "A"))).usable)
        assertFalse(DeckCompatibilityChecker.evaluate(models, listOf(note(1, 1, "Q"))).usable)
    }
}
