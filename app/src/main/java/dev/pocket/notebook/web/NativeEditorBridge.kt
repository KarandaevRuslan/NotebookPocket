package dev.pocket.notebook.web

import android.content.Context

/** Platform capabilities are wired here; the Compose host supplies only current callbacks. */
internal fun nativeEditorBridge(
    context: Context,
    view: () -> EditorActionModeWebView?,
    onChange: (String) -> Unit,
    onCursorSelection: (Int, Int, Int) -> Unit,
    onSearchStateChange: (String) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onReady: () -> Unit,
    onSave: () -> Unit,
    onError: (String) -> Unit,
): EditorBridge =
    EditorBridge(
        context = context,
        onChange = onChange,
        onCursorSelection = onCursorSelection,
        onSearchStateChange = onSearchStateChange,
        onFullscreenChange = onFullscreenChange,
        onKeyboardToggle = { view()?.let(EditorKeyboardController::toggle) },
        onKeepKeyboardAfterCut = { revision ->
            view()?.let { EditorKeyboardController.keepAfterCut(it, revision) }
        },
        onBeginClipboardCut = { revision -> view()?.armKeyboardAfterCut(revision) },
        onCancelClipboardCut = { view()?.cancelKeyboardRequest("beforeinput") },
        onNativeContextMenuDisabled = { disabled ->
            view()?.setNativeContextMenuDisabled(disabled)
        },
        onSystemConfiguration = {
            view()?.systemConfigurationJson() ?: "{\"keyboardHeight\":0,\"hardKeyboardHidden\":2}"
        },
        onReady = onReady,
        onSave = onSave,
        onError = onError,
    )
