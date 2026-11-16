import { manager } from '../acode/host/environment.js';
import { EditorSelection } from '@codemirror/state';
import { getAllFolds, restoreFolds } from '../acode/vendor/cm/editorUtils.ts';
import {
  saveEditorSession,
  readEditorSession,
} from '../acode/host/sessionStore.js';

const SESSION_SAVE_DELAY_MS = 100;
export function createEditorSession({
  editor,
  runtime,
  native,
  storage,
  getDocument,
  isLoading,
  setLoading,
  isDestroyed,
}) {
  let documentId = null,
    sessionRestored = false,
    saveTimer = 0;
  function save() {
    clearTimeout(saveTimer);
    if (isDestroyed() || isLoading()) return;
    const { anchor, head } = editor.state.selection.main,
      scrollTop = Math.round(editor.scrollDOM.scrollTop);
    if (getDocument().kind !== 'resource')
      saveEditorSession(
        storage,
        documentId,
        editor.state.doc.length,
        {
          anchor,
          head,
        },
        scrollTop,
        {
          scrollLeft: editor.scrollDOM.scrollLeft,
          folds: getAllFolds(editor.state),
          selection: editor.state.selection.toJSON(),
        },
      );
    native?.cursorSelection(anchor, head, scrollTop);
  }
  function schedule() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, SESSION_SAVE_DELAY_MS);
  }
  function restore(anchor = 0, head = 0, scrollTop = 0) {
    if (sessionRestored) return;
    sessionRestored = true;
    setLoading(true);
    try {
      const saved =
          getDocument().kind === 'resource'
            ? null
            : readEditorSession(storage, documentId, editor.state.doc.length),
        length = editor.state.doc.length;
      restoreFolds(editor, saved?.folds);
      const clamp = (v) => Math.max(0, Math.min(length, Number(v) || 0));
      let selection = EditorSelection.single(
        clamp(saved?.anchor ?? anchor),
        clamp(saved?.head ?? head),
      );
      if (saved?.selection)
        try {
          const next = EditorSelection.fromJSON(saved.selection);
          if (next.ranges.every((r) => r.from >= 0 && r.to <= length))
            selection = next;
        } catch {}
      editor.dispatch({
        selection,
      });
      runtime.selectionMenu.onSessionChanged();
      requestAnimationFrame(() => {
        if (isDestroyed()) return;
        editor.scrollDOM.scrollTop = Math.max(0, saved?.scrollTop ?? scrollTop);
        editor.scrollDOM.scrollLeft = Math.max(0, saved?.scrollLeft || 0);
        runtime.refresh();
      });
    } finally {
      setLoading(false);
    }
  }
  function setKey(id) {
    const next = String(id || '');
    if (documentId === next) return false;
    documentId = next;
    manager.activeFile.id = next;
    sessionRestored = false;
    return true;
  }
  return {
    save,
    schedule,
    restore,
    setKey,
    destroy: () => clearTimeout(saveTimer),
  };
}
