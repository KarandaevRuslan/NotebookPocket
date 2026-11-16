package dev.pocket.notebook.feature.files

internal fun resourceChild(directory: String, name: String): String =
    (if (directory.isEmpty()) "" else "$directory/") + name.trim()

internal fun resourceDisplayPath(path: String) = "/$path"

internal fun resourceReference(path: String, shared: Boolean) =
    "FileAttachment(${org.json.JSONObject.quote(if(shared)"shared/$path"else path).replace("\\/","/")})"

internal fun resourceSize(size: Int, language: String): String {
    val units = if (language == "ru") listOf("Б", "КБ", "МБ") else listOf("B", "KB", "MB")
    val level = if (size >= 1024 * 1024) 2 else if (size >= 1024) 1 else 0
    val value = size / Math.pow(1024.0, level.toDouble())
    return java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag(language))
        .apply { maximumFractionDigits = if (level == 0) 0 else 1 }
        .format(value) + " " + units[level]
}
