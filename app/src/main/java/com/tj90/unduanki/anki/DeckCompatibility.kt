package com.tj90.unduanki.anki

data class ModelInfo(
    val id: Long,
    val name: String,
    val fieldNames: List<String>,
    val templates: List<CardTemplate>,
    val cloze: Boolean = false
)

data class DeckNote(val id: Long, val modelId: Long, val fields: List<String>)

data class ModelVerdict(
    val model: ModelInfo,
    val mapping: FieldMapping?,
    val supportedNotes: Int,
    val unsupportedNotes: Int,
    val reason: String?
)

/** The outcome of inspecting every note in a selected deck; one unsupported note blocks the deck. */
data class DeckCompatibility(val noteCount: Int, val verdicts: List<ModelVerdict>) {
    val usable: Boolean get() = noteCount > 0 && verdicts.all { it.mapping != null && it.unsupportedNotes == 0 }

    fun mappingFor(modelId: Long): FieldMapping? = verdicts.firstOrNull { it.model.id == modelId }?.mapping

    /** Plain-language summary for the parent; never includes card text. */
    fun summary(): String = when {
        noteCount == 0 -> "This deck has no notes."
        usable -> "All $noteCount notes can be shown: " +
            verdicts.joinToString { "${it.model.name} (${it.supportedNotes}, ${it.mapping!!.question} / ${it.mapping.answer})" } + "."
        else -> "This deck cannot be used yet. " + verdicts.filter { it.mapping == null || it.unsupportedNotes > 0 }
            .joinToString(" ") { "${it.model.name}: ${it.reason ?: "not supported"}." }
    }
}

object DeckCompatibilityChecker {
    private val expression = Regex("\\{\\{([^{}]+)\\}\\}")

    /** Derives the question/answer fields from the single forward template, or explains why not. */
    fun deriveMapping(model: ModelInfo): Pair<FieldMapping?, String?> {
        if (model.cloze) return null to "cloze deletion notes are not supported"
        if (model.templates.size != 1) {
            return null to "it has ${model.templates.size} card templates; reversed and multi-card notes are not supported"
        }
        val template = model.templates.single()
        val question = expression.findAll(template.question).map { it.groupValues[1].trim() }.toList()
        val answer = expression.findAll(template.answer).map { it.groupValues[1].trim() }.filter { it != "FrontSide" }.toList()
        if (question.size != 1 || answer.size != 1) return null to "the card layout is not one plain question and one plain answer"
        val mapping = FieldMapping(question.single(), answer.single())
        if (mapping.question !in model.fieldNames || mapping.answer !in model.fieldNames) {
            return null to "the card layout uses something other than the note's fields"
        }
        if (mapping.question == mapping.answer) return null to "the question and answer are the same field"
        // The mapper owns the full template safety check (no scripts, conditionals, or extra fields).
        val probe = CardContentMapper.map(model.templates, mapping, mapOf(mapping.question to "q", mapping.answer to "a"))
        return if (probe == null) null to "the card layout contains formatting this app cannot show safely" else mapping to null
    }

    /** "50 × a field contains a <code> tag; 12 × the answer is empty", most common first, never card text. */
    private fun describe(rejections: List<String>): String {
        val counts = rejections.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val shown = counts.take(6).joinToString("; ") { "${it.value} × ${it.key}" }
        return if (counts.size > 6) "$shown; and ${counts.size - 6} other kinds" else shown
    }

    fun evaluate(models: Map<Long, ModelInfo>, notes: List<DeckNote>): DeckCompatibility {
        val verdicts = notes.groupBy { it.modelId }.map { (modelId, group) ->
            val model = models[modelId] ?: ModelInfo(modelId, "Unknown note type", emptyList(), emptyList())
            val (mapping, reason) = if (models.containsKey(modelId)) deriveMapping(model) else null to "the note type could not be read"
            if (mapping == null) {
                ModelVerdict(model, null, 0, group.size, reason)
            } else {
                val rejections = group.mapNotNull { note ->
                    if (note.fields.size != model.fieldNames.size) "the note has a different number of fields than its note type"
                    else CardContentMapper.rejection(model.templates, mapping, model.fieldNames.zip(note.fields).toMap())
                }
                val unsupported = rejections.size
                val supported = group.size - unsupported
                ModelVerdict(
                    model, mapping, supported, unsupported,
                    if (unsupported == 0) null else "$unsupported of ${group.size} notes cannot be shown (${describe(rejections)})"
                )
            }
        }.sortedBy { it.model.name }
        return DeckCompatibility(notes.size, verdicts)
    }
}
