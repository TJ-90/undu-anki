package com.tj90.unduanki.anki

data class CardTemplate(val question: String, val answer: String)
data class FieldMapping(val question: String, val answer: String)
data class MappedCard(val questionHtml: String, val answerHtml: String)

/** A deliberately narrow subset; a caller must still inspect every note in the selected deck. */
object CardContentMapper {
    private val fieldTag = Regex("</?(?:b|strong|i|em|u|s|strike|br|p|div|ul|ol|li|sub|sup)\\s*/?>", RegexOption.IGNORE_CASE)
    private val templateTag = Regex("</?(?:b|strong|i|em|u|s|strike|br|p|div|ul|ol|li|sub|sup)\\s*/?>|<hr\\s+id\\s*=\\s*['\"]?answer['\"]?\\s*/?>", RegexOption.IGNORE_CASE)
    private val entity = Regex("&(?:amp|lt|gt|quot|nbsp|#39);", RegexOption.IGNORE_CASE)
    private val unsafeText = Regex("\\[sound:|\\[/?latex\\]|\\[/?mathjax\\]|\\\\[\\(\\)\\[\\]]|\\$\\$|\\{\\{", RegexOption.IGNORE_CASE)
    private val expression = Regex("\\{\\{([^{}]+)\\}\\}")

    private val allowedTagNames = setOf("b", "strong", "i", "em", "u", "s", "strike", "br", "p", "div", "ul", "ol", "li", "sub", "sup")
    private val anyTag = Regex("<\\s*/?\\s*([A-Za-z][A-Za-z0-9]*)[^>]*>")
    private val characterCode = Regex("&#?[A-Za-z0-9]+;")
    private val numericReference = Regex("&#(?:[xX]([0-9a-fA-F]{1,6})|([0-9]{1,7}));")

    fun map(templates: List<CardTemplate>, mapping: FieldMapping, fields: Map<String, String>): MappedCard? =
        if (rejection(templates, mapping, fields) != null) null
        else MappedCard(fields.getValue(mapping.question), fields.getValue(mapping.answer))

    /**
     * Why a note cannot be shown, or null when it can. The wording names the kind of problem (a tag, a character
     * code, an empty field) and never repeats card text, so a parent can see the cause without exposing the deck.
     */
    fun rejection(templates: List<CardTemplate>, mapping: FieldMapping, fields: Map<String, String>): String? {
        if (templates.size != 1) return "its note type does not have exactly one card template"
        if (mapping.question.isBlank() || mapping.answer.isBlank() || mapping.question == mapping.answer) return "its question and answer fields are not set up"
        val question = fields[mapping.question] ?: return "the question field is missing"
        val answer = fields[mapping.answer] ?: return "the answer field is missing"
        if (question.isBlank()) return "the question is empty"
        if (answer.isBlank()) return "the answer is empty"
        for (value in fields.values) unsafeReason(value)?.let { return it }
        val template = templates.single()
        if (!safeTemplate(template.question, listOf(mapping.question))) return "the card layout is not a plain question"
        val backExpressions = expression.findAll(template.answer).map { it.groupValues[1].trim() }.toList()
        if (backExpressions != listOf(mapping.answer) && backExpressions != listOf("FrontSide", mapping.answer)) return "the card layout is not a plain answer"
        if (!safeTemplate(template.answer, backExpressions)) return "the card layout is not a plain answer"
        return null
    }

    /**
     * Drops the named codes the app allows and numeric codes for ordinary printable characters (an apostrophe is
     * often stored as &#x27; or &#39;). Invalid or control-character codes are left in place so they are rejected.
     */
    private fun withoutAllowedEntities(value: String): String = numericReference.replace(entity.replace(value, "")) { match ->
        val codePoint = match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull(16) ?: match.groupValues[2].toIntOrNull()
        if (codePoint != null && isPlainCharacter(codePoint)) "" else match.value
    }

    private fun isPlainCharacter(codePoint: Int) = Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF &&
        !Character.isISOControl(codePoint) && Character.isDefined(codePoint)

    private fun unsafeReason(value: String): String? {
        unsafeText.find(value)?.let { return "a field contains ${it.value.lowercase()}" }
        val remainder = withoutAllowedEntities(fieldTag.replace(value, ""))
        anyTag.find(remainder)?.let {
            val name = it.groupValues[1].lowercase()
            return if (name in allowedTagNames) "a field has a <$name> tag with extra settings such as a class or style" else "a field contains a <$name> tag"
        }
        characterCode.find(remainder)?.let { return "a field contains the character code ${it.value.take(12)}" }
        if ('<' in remainder || '>' in remainder) return "a field contains a stray < or > character"
        if ('&' in remainder) return "a field contains a stray & character"
        return null
    }

    private fun safeTemplate(value: String, expected: List<String>): Boolean {
        val expressions = expression.findAll(value).map { it.groupValues[1].trim() }.toList()
        if (expressions != expected) return false
        val withoutExpressions = expression.replace(value, "")
        return templateTag.replace(withoutExpressions, "").isBlank()
    }
}
