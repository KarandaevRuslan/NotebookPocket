import tag from 'html-tag-js';
import 'html-tag-js/polyfill';
import en from '../vendor/lang/en-us.json';
import ru from '../vendor/lang/ru-ru.json';
import {editorDiagnosticLog} from '../../logging/EditorDiagnostics.js';

// Acode's editor modules depend on these app services. This is the host
// boundary: globals are installed before evaluating the original modules.
const listeners = new Map();
const file = {
  type: 'editor',
  id: '',
  editable: true,
  loading: false,
  focused: false,
  wasFocused: false,
  isUnsaved: false
};
export const manager = {
  editor: null,
  activeFile: file,
  on(names, fn) {
    for (const n of Array.isArray(names) ? names : [names]) {
      if (!listeners.has(n)) listeners.set(n, new Set());
      listeners.get(n).add(fn);
    }
  },
  off(names, fn) {
    for (const n of Array.isArray(names) ? names : [names]) listeners.get(n)?.delete(fn);
  },
  emit(name, ...args) {
    for (const fn of listeners.get(name) || []) fn(...args);
  }
};
export function setLanguage(language) {
  globalThis.strings = {...(language === 'ru' ? ru : en),
    notebookInsertJS:'JavaScript', notebookInsertMD:'Markdown',
    notebookFullscreen:language==='ru'?'На весь экран':'Fullscreen',
    notebookKeyboard:language==='ru'?'Клавиатура':'Keyboard'};
}
setLanguage(document.documentElement.lang);
globalThis.tag = tag;
globalThis.editorManager = manager;
globalThis.root = document.getElementById('editor-shell');
globalThis.app = document.body;
// appendOuter is an Acode page API. The floating toggle stays inside this
// WebView's flex shell rather than outside an app-wide wc-page.
root.appendOuter = node => root.append(node);
globalThis.appSettings = null;
globalThis.system = {
  beginClipboardCut() {
    if (!window.NativeEditor?.keepKeyboardAfterCut) return null;
    try {
      const config = JSON.parse(window.NativeEditor.systemConfiguration());
      editorDiagnosticLog.emit('cut-js-begin',{keyboardVisible:config.keyboardVisible===true,keyboardHeight:config.keyboardHeight,revision:config.keyboardRevision});
      if (!(config.keyboardVisible ?? config.keyboardHeight > 100) || !Number.isInteger(config.keyboardRevision)) {editorDiagnosticLog.emit('cut-js-skipped',{reason:'ime-not-visible'});return null;}
      window.NativeEditor.beginClipboardCut?.(config.keyboardRevision);
      return config.keyboardRevision;
    } catch { return null; }
  },
  completeClipboardCut(revision) {
    editorDiagnosticLog.emit('cut-js-complete',{token:revision});
    window.NativeEditor?.keepKeyboardAfterCut?.(revision);
  },
  setNativeContextMenuDisabled(disabled) {
    window.NativeEditor?.setNativeContextMenuDisabled?.(Boolean(disabled));
  }
};
globalThis.cordova = {
  plugins: {
    clipboard: {
      copy(text) {
        if (window.NativeEditor?.copyToClipboard) window.NativeEditor.copyToClipboard(String(text));else navigator.clipboard?.writeText(String(text));
      },
      paste(callback, error) {
        try {
          const deliver=value=>{const text=String(value??'');if(text)callback(text);};
        if (window.NativeEditor?.readClipboard) deliver(window.NativeEditor.readClipboard());else navigator.clipboard.readText().then(deliver, error);
        } catch (e) {
          error?.(e);
        }
      }
    }
  }
};
window.log = (level, ...args) => console[['error', 'warn', 'info', 'debug'].includes(level) ? level : 'info'](...args);
document.addEventListener('beforeinput', event => {
  if (event.inputType !== 'deleteByCut' && event.target === manager.editor?.contentDOM) window.NativeEditor?.cancelClipboardCut?.();
}, true);
