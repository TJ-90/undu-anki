package com.tj90.unduanki.anki

import org.junit.Assert.*
import org.junit.Test

class CardContentMapperTest {
    private val forward = CardTemplate("{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}")
    private val mapping = FieldMapping("Front", "Back")

    @Test fun acceptsForwardTextAndBasicFormatting() {
        val result = CardContentMapper.map(listOf(forward), mapping, mapOf("Front" to "<b>Why?</b>", "Back" to "Because<br>yes"))
        assertEquals(MappedCard("<b>Why?</b>", "Because<br>yes"), result)
    }

    @Test fun rejectsReversedOrMultipleTemplatesAndChangedFields() {
        val fields = mapOf("Front" to "Question", "Back" to "Answer")
        assertNull(CardContentMapper.map(listOf(CardTemplate("{{Back}}", "{{Front}}")), mapping, fields))
        assertNull(CardContentMapper.map(listOf(forward, forward), mapping, fields))
        assertNull(CardContentMapper.map(listOf(forward), mapping, mapOf("Front" to "Question")))
        assertNull(CardContentMapper.map(listOf(forward), FieldMapping("Front", "Front"), fields))
    }

    @Test fun rejectsMediaExecutableAndMathContent() {
        for (value in listOf("<img src=x>", "[sound:test.mp3]", "<script>alert(1)</script>", "\\(x\\)", "[latex]x[/latex]", "{{c1::hidden}}", "<a href='x'>link</a>")) {
            assertNull(value, CardContentMapper.map(listOf(forward), mapping, mapOf("Front" to "Question", "Back" to value)))
        }
    }

    @Test fun rejectsTemplatesWithHiddenDependencies() {
        val fields = mapOf("Front" to "Question", "Back" to "Answer")
        for (template in listOf(
            CardTemplate("{{Front}} {{Extra}}", "{{Back}}"),
            CardTemplate("{{Front}}", "{{Back}}<script>bad()</script>"),
            CardTemplate("{{#Front}}{{Front}}{{/Front}}", "{{Back}}"),
            CardTemplate("{{Front}} {{Back}}", "{{Back}}")
        )) assertNull(CardContentMapper.map(listOf(template), mapping, fields))
    }
}
