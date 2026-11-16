import { manager } from '../acode/host/environment.js';
import settings from '../acode/host/settings.js';
import { setLanguage } from '../acode/host/environment.js';
import { EditorView } from '@codemirror/view';
import { foldEffect, unfoldEffect } from '@codemirror/language';
import createEditorView from '../acode/vendor/cm/createEditorView.ts';
import { getDocText } from '../acode/vendor/cm/editorUtils.ts';
import { createSinglePaneRuntime } from '../acode/vendor/lib/singlePaneRuntime.js';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import actionStack from '../acode/vendor/lib/actionStack.js';
import { syncQuickToolsVisibility } from '../acode/host/editorFile.js';
import { updateOverlayViewport } from '../acode/host/overlayViewport.js';
import {
  editorDiagnosticLog,
  installEditorDiagnostics,
} from '../logging/EditorDiagnostics.js';
import { installEditorPipelineDiagnostics } from '../logging/EditorPipelineDiagnostics.js';
import {
  createEditorConfiguration,
  DEFAULT_PREFERENCES,
  isDark,
} from './configuration.js';
import { createEditorSession } from './session.js';
import { createEditorSearch } from './search.js';
import { createHostCommands } from './commands.js';
import { installEditorToolbar } from './toolbar.js';
import { installEditorLifecycle } from './lifecycle.js';
import { createEditorApi } from './api.js';

/** The Acode adapter is single-editor; all NotebookPocket-owned lifetimes are scoped here. */
export function startEditor({
  host,
  native = window.NativeEditor,
  storage = window.localStorage,
}) {
  globalThis.appSettings = settings;
  // Android EditContext clips long selections. Keep CodeMirror's DOM input path.
  EditorView.EDIT_CONTEXT = false;
  updateOverlayViewport();
  host.classList.add('editor-container');
  const documentInfo = { kind: 'notebook', name: 'notebook.html' };
  let preferences = { ...DEFAULT_PREFERENCES };
  let loading = false,
    destroyed = false,
    fullscreen = false;
  let diagnosticSession, session;
  const runtime = createSinglePaneRuntime(host);
  const getPreferences = () => preferences,
    getDocument = () => documentInfo;
  const isDestroyed = () => destroyed;
  const configuration = createEditorConfiguration({
    runtime,
    getPreferences,
    getDocument,
    onUpdate(update) {
      diagnosticSession?.update(update);
      manager.emit('editor-state-changed', update);
      if (loading) return;
      if (update.docChanged) {
        native?.changed(getDocText(update.state.doc));
        manager.activeFile.isUnsaved = true;
        manager.emit('file-content-changed');
      }
      if (
        update.docChanged ||
        update.selectionSet ||
        update.transactions.some((t) =>
          t.effects.some((e) => e.is(foldEffect) || e.is(unfoldEffect)),
        )
      )
        session?.schedule();
      if (update.geometryChanged || update.viewportChanged)
        runtime.refresh('geometry-update');
    },
  });
  const editor = createEditorView({
    state: configuration.createState(''),
    parent: host,
  });
  runtime.attach(editor);
  diagnosticSession = installEditorDiagnostics(editor);
  const pipelineSession = installEditorPipelineDiagnostics(editor);
  session = createEditorSession({
    editor,
    runtime,
    native,
    storage,
    getDocument,
    isLoading: () => loading,
    setLoading: (value) => {
      loading = value;
    },
    isDestroyed,
  });
  const search = createEditorSearch({ editor, native });
  function setSource(value) {
    const source = String(value);
    if (getDocText(editor.state.doc) === source) return;
    loading = true;
    try {
      runtime.selectionMenu.onSessionChanged();
      editor.setState(configuration.createState(source));
      if (actionStack.has('search-bar'))
        quickTools.$searchInput.el.dispatchEvent(new Event('input'));
      manager.emit('file-loaded');
    } finally {
      loading = false;
    }
  }
  function setPreferences(next = {}) {
    preferences = {
      ...preferences,
      ...next,
    };
    preferences.fontSize = Math.max(
      11,
      Math.min(24, Number(preferences.fontSize) || 14),
    );
    const dark = isDark(preferences);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.documentElement.lang = preferences.language;
    setLanguage(preferences.language);
    document.documentElement.style.setProperty(
      '--np-editor-font-size',
      `${preferences.fontSize}px`,
    );
    settings.value.fontSize = `${preferences.fontSize}px`;
    settings.value.textWrap = preferences.wordWrap;
    manager.activeFile.hideQuickTools = !preferences.showQuickTools;
    configuration.reconfigure(editor);
    syncQuickToolsVisibility(manager.activeFile);
    runtime.refresh();
    for (const input of [
      quickTools.$searchInput.el,
      quickTools.$replaceInput.el,
    ])
      if (input)
        input.placeholder =
          input === quickTools.$searchInput.el
            ? strings.search
            : strings.replace;
    quickTools.$footer
      .querySelectorAll('[data-id]')
      .forEach(
        (b) =>
          (b.ariaLabel =
            strings['quicktools:' + b.dataset.id] ||
            strings[b.dataset.id] ||
            b.ariaLabel),
      );
  }
  function setDocument(name, kind = 'resource') {
    documentInfo.name = String(name || 'file.txt');
    documentInfo.kind = kind === 'notebook' ? 'notebook' : 'resource';
    document.documentElement.dataset.documentKind = documentInfo.kind;
    manager.activeFile.filename = documentInfo.name;
    configuration.configureDocument(editor);
  }
  function setFullscreenState(enabled) {
    fullscreen = !!enabled;
    const button = quickTools.$footer.querySelector(
      '[data-id="notebookFullscreen"]',
    );
    button?.classList.toggle('fullscreen_exit', fullscreen);
    button?.classList.toggle('fullscreen', !fullscreen);
  }
  const commands = createHostCommands({
    editor,
    native,
    getDocument,
    getPreferences,
    saveSession: session.save,
    emitSearch: search.emit,
    isFullscreen: () => fullscreen,
  });
  globalThis.acode = { exec: commands.hostCommand };
  const removeToolbar = installEditorToolbar({ editor, ...commands, native });
  syncQuickToolsVisibility(manager.activeFile);
  const lifecycle = installEditorLifecycle({
    editor,
    host,
    runtime,
    getPreferences,
    setPreferences,
    scheduleSessionSave: session.schedule,
    saveSession: session.save,
    emitSearch: search.emit,
    isDestroyed,
  });
  function destroy() {
    if (destroyed) return;
    pipelineSession.destroy();
    diagnosticSession.destroy();
    editorDiagnosticLog.setEnabled(false);
    session.save();
    destroyed = true;
    lifecycle.destroy();
    session.destroy();
    removeToolbar();
    runtime.destroy();
    quickTools.$footer.remove();
    quickTools.$toggler.remove();
    quickTools.$input.remove();
    editor.destroy();
  }
  return createEditorApi({
    editor,
    host,
    runtime,
    session,
    search,
    lifecycle,
    setDocument,
    setSource,
    setPreferences,
    ...commands,
    getPreferences,
    isDestroyed,
    setFullscreenState,
    destroy,
  });
}
