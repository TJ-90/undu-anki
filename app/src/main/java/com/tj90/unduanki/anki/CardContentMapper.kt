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
    private val expression = Regex("\\{\\{([^{}]+)}}")

    fun map(templates: List<CardTemplate>, mapping: FieldMapping, fields: Map<String, String>): MappedCard? {
        if (templates.size != 1 || mapping.question.isBlank() || mapping.answer.isBlank() || mapping.question == mapping.answer) return null
        val question = fields[mapping.question] ?: return null
        val answer = fields[mapping.answer] ?: return null
        if (question.isBlank() || answer.isBlank() || fields.values.any { !safeField(it) }) return null
        val template = templates.single()
        if (!safeTemplate(template.question, listOf(mapping.question))) return null
        val backExpressions = expression.findAll(template.answer).map { it.groupValues[1].trim() }.toList()
        if (backExpressions != listOf(mapping.answer) && backExpressions != listOf("FrontSide", mapping.answer)) return null
        if (!safeTemplate(template.answer, backExpressions)) return null
        return MappedCard(question, answer)
    }

    private fun safeField(value: String): Boolean {
        if (unsafeText.containsMatchIn(value)) return false
        val remainder = entity.replace(fieldTag.replace(value, ""), "")
        return '<' !in remainder && '>' !in remainder && '&' !in remainder
    }

    private fun safeTemplate(value: String, expected: List<String>): Boolean {
        val expressions = expression.findAll(value).map { it.groupValues[1].trim() }.toList()
        if (expressions != expected) return false
        val withoutExpressions = expression.replace(value, "")
        return templateTag.replace(withoutExpressions, "").isBlank()
    }
}
