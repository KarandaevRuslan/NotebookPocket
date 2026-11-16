package dev.pocket.notebook.data

/** Public metadata is readable even when the notebook source is encrypted. */
data class NotebookMetadata(
    val title: String,
    val description: String = "",
    val author: String = "",
    val version: String = "",
)

object NotebookMetadataCodec {
    private val notebookTag = Regex("<notebook\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("<title\\b[^>]*>[\\s\\S]*?</title\\s*>", RegexOption.IGNORE_CASE)

    private fun attribute(name: String) =
        Regex("\\sdata-$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)

    private val attributes = listOf("description", "author", "version")

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun unescape(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun titleBeforeCells(source: String): MatchResult? {
        val opening = notebookTag.find(source) ?: return null
        val start = opening.range.last + 1
        val firstCell =
            Regex("<script\\b", RegexOption.IGNORE_CASE).find(source, start)?.range?.first
                ?: source.length
        return titleTag.find(source, start)?.takeIf { it.range.first < firstCell }
    }

    fun read(source: String): NotebookMetadata {
        val rawTitle =
            titleBeforeCells(source)
                ?.value
                .orEmpty()
                .replace(Regex("^<title\\b[^>]*>|</title\\s*>$", RegexOption.IGNORE_CASE), "")
        val tag = notebookTag.find(source)?.value.orEmpty()
        fun get(name: String) =
            attribute(name)
                .find(tag)
                ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                ?.let(::unescape)
                .orEmpty()
        return NotebookMetadata(
            unescape(rawTitle.trim()),
            get("description"),
            get("author"),
            get("version"),
        )
    }

    /**
     * Code edits are raw drafts, and can temporarily have no valid title or even an incomplete
     * notebook tag while typing / undoing. Persist the source verbatim; retain last-known-good
     * public metadata until it becomes valid. This is intentionally different from explicit
     * metadata dialog validation.
     */
    fun forDraft(source: String, previous: NotebookMetadata): NotebookMetadata {
        if (notebookTag.find(source) == null) return previous
        val candidate = read(source)
        return candidate.takeIf { runCatching { validate(it) }.isSuccess } ?: previous
    }

    fun validate(metadata: NotebookMetadata) {
        require(metadata.title.isNotBlank() && metadata.title.length <= 120) {
            "Title must be 1–120 characters"
        }
        require(metadata.description.length <= 500) { "Description must be at most 500 characters" }
        require(metadata.author.length <= 120) { "Author must be at most 120 characters" }
        require(metadata.version.length <= 64) { "Version must be at most 64 characters" }
    }

    fun update(source: String, metadata: NotebookMetadata): String {
        validate(metadata)
        val opening = requireNotNull(notebookTag.find(source)) { "Not an Observable notebook" }
        val oldTag = opening.value
        val base = attributes.fold(oldTag) { acc, name -> acc.replace(attribute(name), "") }
        val updatedTag =
            base.dropLast(1) +
                attributes.joinToString("") { name ->
                    val value =
                        when (name) {
                            "description" -> metadata.description
                            "author" -> metadata.author
                            else -> metadata.version
                        }
                    " data-$name=\"${escape(value)}\""
                } +
                ">"
        var result = source.replaceRange(opening.range, updatedTag)
        val newTitle = "<title>${escape(metadata.title.trim())}</title>"
        val titleMatch = titleBeforeCells(result)
        result =
            if (titleMatch != null) result.replaceRange(titleMatch.range, newTitle)
            else
                result.replaceRange(
                    updatedTag.length + opening.range.first,
                    updatedTag.length + opening.range.first,
                    "\n  $newTitle",
                )
        return result
    }
}
