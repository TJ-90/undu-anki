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

    @Test fun rejectionNamesTheRuleAndMapAgrees() {
        fun why(front: String, back: String) = CardContentMapper.rejection(listOf(forward), mapping, mapOf("Front" to front, "Back" to back))
        assertNull(why("<b>Q</b>", "A &amp; B<br>C&nbsp;D"))
        assertEquals("the question is empty", why("", "A"))
        assertEquals("the answer is empty", why("Q", " "))
        assertEquals("a field contains [sound:", why("Q", "[sound:a.mp3]"))
        assertEquals("a field contains a <img> tag", why("<img src=x>", "A"))
        assertEquals("a field contains a <span> tag", why("Q", "<span style='c'>A</span>"))
        assertEquals("a field has a <div> tag with extra settings such as a class or style", why("Q", "<div class=\"c\">A</div>"))
        assertEquals("a field contains the character code &rarr;", why("Q", "a &rarr; b"))
        assertEquals("a field contains a stray < or > character", why("a < b", "A"))
        assertEquals("a field contains a stray & character", why("AT&T", "A"))
        // Whatever rejection says, map() must give the same verdict.
        for ((front, back) in listOf("Q" to "A", "" to "A", "<img>" to "A", "Q" to "&rarr;")) {
            assertEquals(why(front, back) == null, CardContentMapper.map(listOf(forward), mapping, mapOf("Front" to front, "Back" to back)) != null)
        }
    }
}
