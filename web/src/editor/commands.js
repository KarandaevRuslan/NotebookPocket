import { manager } from '../acode/host/environment.js';
import { search } from '@codemirror/search';
import { getDocText } from '../acode/vendor/cm/editorUtils.ts';
import actions, {
  cancelQuickToolsModifierInput,
  key,
} from '../acode/vendor/handlers/quickTools.js';
import commandPalette from '../acode/vendor/palettes/commandPalette/index.js';
import { messagesFor } from '../locales/index.js';
import { insertColor } from '../acode/host/insertColor.js';

export function createHostCommands({
  editor,
  native,
  getDocument,
  getPreferences,
  saveSession,
  emitSearch,
  isFullscreen,
}) {
  function insertCell(mode) {
    if (getDocument().kind !== 'notebook') return false;
    const text = getDocText(editor.state.doc),
      index = text.lastIndexOf('</notebook>'),
      at = index < 0 ? text.length : index;
    const id =
      Math.max(
        0,
        ...Array.from(
          text.matchAll(/<script\b[^>]*\bid\s*=\s*["'](\d+)["']/g),
          (m) => Number(m[1]),
        ),
      ) + 1;
    const snippet =
      mode === 'md'
        ? `  <script id="${id}" type="text/markdown">\n    ## ${messagesFor(getPreferences().language).newSection}\n  </script>\n`
        : `  <script id="${id}" type="module">\n    display(1 + 2);\n  </script>\n`;
    editor.dispatch({
      changes: {
        from: at,
        insert: snippet,
      },
      selection: {
        anchor: at + snippet.indexOf('\n') + 5,
      },
      scrollIntoView: true,
      userEvent: 'input',
    });
    editor.focus();
  }
  function hostCommand(name) {
    if (name === 'find' || name === 'replace') {
      actions('search');
      emitSearch();
      return true;
    }
    if (name === 'command-palette') {
      commandPalette();
      return true;
    }
    if (name === 'save') {
      if (getDocument().kind === 'resource') {
        native?.changed(getDocText(editor.state.doc));
        native?.save?.();
        return true;
      }
      native?.changed(getDocText(editor.state.doc));
      saveSession();
      manager.activeFile.isUnsaved = false;
      manager.emit('save-file');
      return true;
    }
    if (name === 'toggle-fullscreen') {
      native?.setFullscreen(!isFullscreen());
      return true;
    }
    if (name === 'goto') {
      const line = window.prompt(
        strings['go to line'] || 'Go to line',
        String(
          editor.state.doc.lineAt(editor.state.selection.main.head).number,
        ),
      );
      if (line) editor.gotoLine(...String(line).split('.'));
      return true;
    }
    if (name === 'insert-color') {
      void insertColor(editor);
      return true;
    }
    return false;
  }
  return { insertCell, hostCommand };
}
