package dev.pocket.notebook.app.navigation

/** Route arguments, not mutable singleton globals, identify an open notebook. */
object Routes {
    const val LIBRARY = "library"
    const val WORKSPACE = "workspace/{id}"
    const val SETTINGS = "settings"

    fun workspace(id: String): String = "workspace/$id"
}
