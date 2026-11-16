package dev.pocket.notebook.feature.files

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import dev.pocket.notebook.data.*
import dev.pocket.notebook.data.ArchivePasswordRequired
import dev.pocket.notebook.data.security.SecurityAction
import dev.pocket.notebook.domain.NotebookGateway
import dev.pocket.notebook.i18n.localizedString
import kotlinx.coroutines.*

internal val LocalPocketTools =
    staticCompositionLocalOf<PocketTools> { error("Pocket tools are not installed") }

internal data class TreeClipboard(val ids: List<String>, val cut: Boolean)

internal data class ResourceTransfer(
    val source: ResourceClipboard,
    val target: String,
    val directory: String,
)

internal data class ResourceClipboard(
    val owner: String,
    val paths: List<String>,
    val cut: Boolean,
) {
    constructor(owner: String, path: String, cut: Boolean) : this(owner, listOf(path), cut)
}

internal class PocketTools(
    val repo: NotebookGateway,
    val context: Context,
    val scope: CoroutineScope,
    val refresh: () -> Unit,
    val security: (String, SecurityAction) -> Unit,
) {
    var beforeOperation: suspend () -> Unit = {}
    /** Supplied by the composition root; must serialize deletion with document writes. */
    var deleteItemsSafely: (suspend (List<String>) -> Unit)? = null
    var language by mutableStateOf("en")
    var revision by mutableIntStateOf(0)
    var privateVisible by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var picker = false
    var lockEpoch = 0L
    var selected by mutableStateOf<Set<String>>(emptySet())
    var clipboard by mutableStateOf<TreeClipboard?>(null)
    var confirmResourceTransfer by mutableStateOf<ResourceTransfer?>(null)
    var resourceClipboard by mutableStateOf<ResourceClipboard?>(null)
    var resourceOwner by mutableStateOf<String?>(null)
    var exportIds by mutableStateOf<List<String>?>(null)
    var importRequest by mutableStateOf<Pair<Uri, String?>?>(null)
    var destination by mutableStateOf<List<String>?>(null)
    var confirmTransfer by mutableStateOf<Triple<List<String>, String?, Boolean>?>(null)

    fun text(id: Int) = localizedString(context, language, id)

    fun message(value: String) {
        Toast.makeText(context, value, Toast.LENGTH_LONG).show()
    }

    fun changed() {
        refresh()
        revision++
    }

    fun select(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun perform(action: () -> Unit, success: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    beforeOperation()
                    action()
                }
                changed()
                success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(errorText(e))
            } finally {
                busy = false
            }
        }
    }

    /** Destructive action: discards edits of removed documents instead of requiring their flush. */
    fun performDelete(ids: List<String>, success: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val delete = checkNotNull(deleteItemsSafely) { "Safe deletion handler is missing" }
                    delete(ids)
                }
                changed()
                success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(errorText(e))
            } finally {
                busy = false
            }
        }
    }

    fun errorText(error: Exception): String {
        val ru = language == "ru"
        val m = error.message.orEmpty()
        return when {
            error is dev.pocket.notebook.domain.PendingDraftException ->
                text(dev.pocket.notebook.R.string.document_finish_draft)
            error is NotebookLockedException ->
                if (ru) "Сначала разблокируйте инструкцию или коллекцию"
                else "Unlock the notebook or collection first"
            m.contains("shared resources") ->
                if (ru) "Сначала переместите или удалите общие ресурсы коллекции"
                else "Move or remove shared collection resources first"
            m.contains("without nested") ->
                if (ru) "Папка должна содержать только инструкции, без вложенных папок и коллекций"
                else "The folder must contain notebooks only, without nested folders or collections"
            m.contains("Collections accept") ->
                if (ru) "В коллекцию можно поместить только инструкции"
                else "Collections accept notebooks only"
            m.contains("descendant") || m.contains("itself") || m.contains("depth") ->
                if (ru) "Нельзя вставить папку в себя или потомка. Максимальная глубина — 100."
                else "Cannot paste a folder into itself or a descendant. Maximum depth is 100."
            m.contains("Unsafe resource path") || m.contains("Reserved resource") ->
                if (ru) "Укажите относительный путь без .., пустых сегментов и обратных косых черт"
                else "Use a relative path without .., empty segments or backslashes"
            m.contains("Resource changed") ->
                if (ru)
                    "Файл был изменён, удалён или перемещён. Черновик сохранён в редакторе; используйте «Сохранить как»."
                else
                    "The file changed, was deleted or moved. Your draft is still in the editor; use Save as."
            m.contains("conflict") || m.contains("already exists") ->
                if (ru) "Ресурс с таким именем уже существует"
                else "A resource with this name already exists"
            else ->
                if (ru)
                    "Операция не выполнена. Проверьте пароль, назначение, имена файлов и допустимые размеры."
                else
                    "Operation failed. Check the password, destination, file names and size limits."
        }
    }

    fun copy(ids: List<String>, cut: Boolean) {
        clipboard = TreeClipboard(ids, cut)
        selected = emptySet()
    }

    fun transfer(ids: List<String>, target: String?, cut: Boolean, confirmed: Boolean = false) {
        val destination = repo.allFolders().firstOrNull { it.id == target }
        if (
            !confirmed &&
                destination?.encrypted != true &&
                ids.any { id -> repo.list().any { it.id == id && it.encrypted } }
        ) {
            confirmTransfer = Triple(ids, target, cut)
            return
        }
        perform({ repo.transfer(ids, target, cut) }) {
            this.destination = null
            confirmTransfer = null
            selected = emptySet()
            if (cut) clipboard = null
        }
    }

    fun transferResource(request: ResourceTransfer, confirmed: Boolean = false) {
        if (
            !confirmed &&
                repo.ownerProtected(request.source.owner) &&
                !repo.ownerProtected(request.target)
        ) {
            confirmResourceTransfer = request
            return
        }
        perform({
            repo.transferResources(
                request.source.owner,
                request.source.paths,
                request.target,
                request.directory,
                request.source.cut,
            )
        }) {
            if (request.source.cut && resourceClipboard == request.source) resourceClipboard = null
            confirmResourceTransfer = null
        }
    }

    fun import(uri: Uri, parent: String?, password: CharArray? = null) {
        if (busy) {
            password?.fill('\u0000')
            return
        }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    beforeOperation()
                    repo.importAny(uri, parent, password)
                }
                changed()
                importRequest = null
            } catch (e: ArchivePasswordRequired) {
                importRequest = uri to parent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(errorText(e))
            } finally {
                password?.fill('\u0000')
                busy = false
            }
        }
    }
}
