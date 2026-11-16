package dev.pocket.notebook.data

/**
 * Pure ordering rules shared by folders and notebooks. A drop on an occupied cell means INSERT AT
 * THAT CELL, for BOTH directions.
 */
object NotebookOrder {
    fun <T> movedIndices(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moved(ids: List<String>, moving: String, target: String): List<String> =
        movedIndices(ids, ids.indexOf(moving), ids.indexOf(target))
}
