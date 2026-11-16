package dev.pocket.notebook.feature.files.state

internal data class ResourceOpen(val owner: String, val path: String, val edit: Boolean = false)
