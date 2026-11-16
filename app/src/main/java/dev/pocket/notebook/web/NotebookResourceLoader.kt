package dev.pocket.notebook.web

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import dev.pocket.notebook.domain.NotebookResourceAccess
import java.io.ByteArrayInputStream

/** A capability bound to one preview. There is no route for another notebook or disk path. */
internal class NotebookResourceLoader(
    private val repo: NotebookResourceAccess,
    private val id: String,
    private val scope: String,
    private val originHost: String = EDITOR_HOST,
) {
    fun read(uri: Uri): WebResourceResponse? {
        if (uri.scheme != "https" || uri.host != originHost) return null
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != "notebook-resources" || segments[1] != scope)
            return null
        return runCatching {
                val path = segments.drop(2).joinToString("/")
                val bytes = repo.attachment(id, path)
                val extension = path.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                val mime =
                    when (extension) {
                        "png" -> "image/png"
                        "jpg",
                        "jpeg" -> "image/jpeg"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        "svg" -> "image/svg+xml"
                        "avif" -> "image/avif"
                        "json" -> "application/json"
                        "txt",
                        "md" -> "text/plain"
                        "csv" -> "text/csv"
                        "tsv" -> "text/tab-separated-values"
                        else ->
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                ?: "application/octet-stream"
                    }
                WebResourceResponse(
                    mime,
                    null,
                    200,
                    "OK",
                    mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
                    ByteArrayInputStream(bytes),
                )
            }
            .getOrNull()
    }
}
