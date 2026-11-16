@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.viewer

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.pocket.notebook.R
import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.ResourceText
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.dialogs.FileNameForm
import dev.pocket.notebook.feature.files.resourceChild
import dev.pocket.notebook.feature.files.state.ResourceOpen
import dev.pocket.notebook.settings.EditorPreferences
import dev.pocket.notebook.web.NotebookEditorWebView
import dev.pocket.notebook.web.readEditorSource
import kotlinx.coroutines.*

/** Acode edits an in-memory draft. Only an explicit save replaces the resource, using its hash. */
@Composable
internal fun ResourceTextEditor(
    target: ResourceOpen,
    format: ResourceText,
    initialHash: String,
    protected: Boolean,
    dismiss: () -> Unit,
) {
    val t = LocalPocketTools.current
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val preferences = remember { EditorPreferences(t.context) }
    val session = remember(target) { "resource-" + java.util.UUID.randomUUID() }
    var text by remember(target) { mutableStateOf(format.text) }
    var saved by remember(target) { mutableStateOf(format.text) }
    var hash by remember(target) { mutableStateOf(initialHash) }
    var web by remember(target) { mutableStateOf<WebView?>(null) }
    var saving by remember(target) { mutableStateOf(false) }
    var closePrompt by remember(target) { mutableStateOf(false) }
    var saveAs by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var fullscreen by remember(target) { mutableStateOf(false) }
    var search by remember(target) { mutableStateOf("") }
    var anchor by remember(target) { mutableIntStateOf(0) }
    var head by remember(target) { mutableIntStateOf(0) }
    var scroll by remember(target) { mutableIntStateOf(0) }
    var alive by remember(target) { mutableStateOf(true) }
    DisposableEffect(target) { onDispose { alive = false } }
    val dirty = text != saved
    fun close() {
        if (saving) return
        if (fullscreen) {
            fullscreen = false
            return
        }
        readEditorSource(web, text) { snapshot ->
            if (alive) {
                text = snapshot
                if (snapshot != saved) closePrompt = true else dismiss()
            }
        }
    }
    fun save(copyPath: String? = null, closeAfter: Boolean = false) {
        if (saving) return
        saving = true
        val epoch = t.lockEpoch
        readEditorSource(web, text) { snapshot ->
            if (!alive || epoch != t.lockEpoch) {
                saving = false
                return@readEditorSource
            }
            text = snapshot
            scope.launch {
                try {
                    val bytes =
                        withContext(Dispatchers.IO) {
                            val bytes = format.encode(snapshot)
                            if (epoch != t.lockEpoch) throw NotebookLockedException()
                            if (copyPath == null)
                                t.repo.replaceResource(target.owner, target.path, bytes, hash)
                            else t.repo.putResource(target.owner, copyPath, bytes)
                            bytes
                        }
                    if (!alive || epoch != t.lockEpoch) return@launch
                    t.changed()
                    if (copyPath == null) {
                        hash = NotebookCrypto.sha256(bytes)
                        saved = snapshot
                    }
                    saveAs = false
                    error = null
                    t.message(t.text(R.string.res_saved))
                    if (closeAfter && text == snapshot) dismiss()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error =
                        if (e.message?.contains("editor limit") == true)
                            t.text(R.string.res_text_limit)
                        else t.errorText(e)
                } finally {
                    saving = false
                }
            }
        }
    }
    Dialog(
        onDismissRequest = ::close,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
                securePolicy =
                    if (protected) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
            ),
    ) {
        BackHandler { close() }
        // The dialog is its own Window. Give it the full display (including the
        // landscape rail side), then consume *its own* IME/system insets. The
        // CodeMirror WebView and QuickTools shrink together above the keyboard.
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                modifier =
                    Modifier.fillMaxSize()
                        // In landscape, safeDrawing's horizontal display-cutout inset can
                        // leave a strip opposite the system navigation bar. Reserve only
                        // the actual navigation-bar side; allow the editor to fill the
                        // other edge of this full-screen dialog.
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .imePadding(),
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                topBar = {
                    if (!fullscreen)
                        TopAppBar(
                            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                            title = {
                                Column {
                                    Text(
                                        (if (dirty) "● " else "") +
                                            target.path.substringAfterLast('/'),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        target.path + " · " + format.charset.name(),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(::close) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        t.text(R.string.fs_cancel),
                                    )
                                }
                            },
                            actions = {
                                IconButton({ saveAs = true }, enabled = !saving) {
                                    Icon(Icons.Default.SaveAs, t.text(R.string.res_save_as))
                                }
                                IconButton(
                                    { save() },
                                    enabled = dirty && !saving,
                                    modifier = Modifier.testTag("resource-editor-save"),
                                ) {
                                    Icon(Icons.Default.Save, t.text(R.string.res_save))
                                }
                            },
                        )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    NotebookEditorWebView(
                        id = session,
                        initialSource = text,
                        dark = dark,
                        language = t.language,
                        editorPreferences = preferences,
                        onChanged = { text = it },
                        selectionAnchor = anchor,
                        selectionHead = head,
                        editorScrollTop = scroll,
                        onCursorSelection = { a, h, s ->
                            anchor = a
                            head = h
                            scroll = s
                        },
                        searchStateJson = search,
                        onSearchStateChange = { search = it },
                        fullscreen = fullscreen,
                        onFullscreenChange = { fullscreen = it },
                        fileName = target.path,
                        resourceFile = true,
                        onSaveRequested = { save() },
                        onWebView = { web = it },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (closePrompt)
        AlertDialog(
            onDismissRequest = { closePrompt = false },
            title = { Text(t.text(R.string.res_unsaved_title)) },
            text = { Text(t.text(R.string.res_unsaved_body)) },
            confirmButton = {
                TextButton({
                    closePrompt = false
                    save(closeAfter = true)
                }) {
                    Text(t.text(R.string.res_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton({
                        closePrompt = false
                        dismiss()
                    }) {
                        Text(t.text(R.string.res_discard))
                    }
                    TextButton({ closePrompt = false }) { Text(t.text(R.string.fs_cancel)) }
                }
            },
        )
    if (error != null)
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(t.text(R.string.save_error)) },
            text = { Text(error!!) },
            confirmButton = { TextButton({ error = null }) { Text(t.text(R.string.fs_confirm)) } },
            dismissButton = {
                TextButton({
                    error = null
                    saveAs = true
                }) {
                    Text(t.text(R.string.res_save_as))
                }
            },
        )
    if (saveAs) {
        val name = target.path.substringAfterLast('/')
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val copy = name.substring(0, dot) + " - " + t.text(R.string.res_copy) + name.substring(dot)
        FileNameForm(
            t.text(R.string.res_save_as),
            resourceChild(target.path.substringBeforeLast('/', ""), copy),
            { saveAs = false },
        ) { path ->
            save(copyPath = path)
        }
    }
}
