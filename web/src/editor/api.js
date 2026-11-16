import { search } from '@codemirror/search';
import { getDocText } from '../acode/vendor/cm/editorUtils.ts';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import actions, {
  cancelQuickToolsModifierInput,
  key,
} from '../acode/vendor/handlers/quickTools.js';
import actionStack from '../acode/vendor/lib/actionStack.js';
import { updateSystemConfiguration } from '../acode/host/systemConfiguration.js';
import { editorDiagnosticLog } from '../logging/EditorDiagnostics.js';
import { recordPipeline } from '../logging/EditorPipelineDiagnostics.js';

export function createEditorApi({
  editor,
  host,
  runtime,
  session,
  search,
  lifecycle,
  setDocument,
  setSource,
  setPreferences,
  insertCell,
  hostCommand,
  getPreferences,
  isDestroyed,
  setFullscreenState,
  destroy,
}) {
  return {
    setDocument,
    setSource,
    setPreferences,
    getSource: () => getDocText(editor.state.doc),
    insertCell,
    setSessionKey(id) {
      if (session.setKey(id)) search.reset();
    },
    restoreSession: session.restore,
    restoreSearchState: search.restore,
    search: () => hostCommand('find'),
    focus: () => {
      recordPipeline('focus-api', { operation: 'focus' });
      editor.focus();
    },
    // Cut continuation separately checks native view/window/lifecycle state.
    // CM.hasFocus also requires document.hasFocus, which becomes false during
    // a transient window interruption even if contentDOM still owns editing.
    hasEditingFocus() {
      const active =
        !isDestroyed() &&
        editor.dom.isConnected &&
        !editor.state.readOnly &&
        document.activeElement === editor.contentDOM;
      if (editorDiagnosticLog.enabled)
        recordPipeline('focus-editing-check', {
          active,
          hasFocus: editor.hasFocus,
          documentFocused: document.hasFocus(),
        });
      return active;
    },
    setDiagnosticsEnabled: (enabled) => editorDiagnosticLog.setEnabled(enabled),
    setDiagnosticsOptions: (options) => editorDiagnosticLog.setOptions(options),
    focusForKeyboard() {
      recordPipeline('focus-api', { operation: 'focusForKeyboard' });
      if (actionStack.has('search-bar')) quickTools.$searchInput.el.focus();
      else if (key.ctrl || key.alt || key.meta) quickTools.$input.focus();
      else editor.focus();
    },
    suspendKeyboardFocus() {
      recordPipeline('focus-api', { operation: 'suspendKeyboardFocus' });
      session.save();
      search.emit();
      cancelQuickToolsModifierInput();
      if (document.activeElement instanceof HTMLElement)
        document.activeElement.blur();
    },
    systemConfigurationChanged: updateSystemConfiguration,
    setFullscreenState,
    captureSession: lifecycle.capture,
    refresh: () => {
      editor.requestMeasure();
      runtime.refresh('api-refresh');
    },
    handleBack() {
      if (!actionStack.length) return false;
      actionStack.pop();
      search.emit();
      session.save();
      return true;
    },
    getDiagnostics() {
      const box = host.getBoundingClientRect();
      return {
        sourceLength: editor.state.doc.length,
        diagnosticsEnabled: editorDiagnosticLog.enabled,
        selectionMode: editorDiagnosticLog.options.selectionMode,
        revealMode: editorDiagnosticLog.options.revealMode,
        selectionAnchor: editor.state.selection.main.anchor,
        selectionHead: editor.state.selection.main.head,
        viewportWidth: innerWidth,
        viewportHeight: innerHeight,
        hostHeight: box.height,
        width: box.width,
        height: box.height,
        scrollerHeight: editor.scrollDOM.clientHeight,
        quickToolsHeight: quickTools.$footer.offsetHeight,
        computedFontSize: getComputedStyle(editor.contentDOM).fontSize,
        configuredFontSize: `${getPreferences().fontSize}px`,
        isConnected: editor.dom.isConnected,
        engine: 'Acode DOM CodeMirror',
      };
    },
    destroy,
  };
}
