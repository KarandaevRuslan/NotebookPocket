package dev.pocket.notebook.domain

/** A portable operation would omit a newer durable checkpoint. The UI supplies localized guidance. */
class PendingDraftException : IllegalStateException("Pending draft must be resolved")
