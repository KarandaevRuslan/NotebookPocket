package dev.pocket.notebook.domain

/** Both outcomes are durable; DRAFT preserves incomplete HTML outside the portable package. */
enum class SaveOutcome {
    COMMITTED,
    DRAFT,
}
