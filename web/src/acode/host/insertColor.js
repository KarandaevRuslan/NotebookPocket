import pickColor from '../vendor/dialogs/color.js';
import {getColorRange, isValidColor} from '../vendor/utils/color/regex.js';
import 'vanilla-picker/dist/vanilla-picker.csp.css';
import {editorDiagnosticLog, selectionDiagnosticFields} from '../../logging/EditorDiagnostics.js';
let pending = false;

/** Original Acode dialog and color-range semantics, with a captured host
 * document so an external notebook reload cannot receive a stale edit. */
export async function insertColor(editor) {
  if (pending || document.querySelector('.acode-colour-dialog')) {editorDiagnosticLog.emit('colour-skipped',{reason:'already-open'});return;}
  if(editorDiagnosticLog.enabled)editorDiagnosticLog.emit('colour-request',selectionDiagnosticFields(editor));
  pending = true;
  const documentAtOpen = editor.state.doc;
  const range = getColorRange();
  const selection = editor.state.selection;
  const focused = editor.hasFocus;
  const stored = localStorage.__picker_last_picked;
  const seed = range ? documentAtOpen.sliceString(range.from, range.to) :
    (typeof stored === 'string' && isValidColor(stored) ? stored : '#ffffff');
  // Acode's selection-menu finally restores editor focus synchronously.
  // Open after that completes, then let the dialog own focus.
  await Promise.resolve();
  try {
    const value = await pickColor(seed, () => {editorDiagnosticLog.emit('colour-onhide');if (focused && editor.dom.isConnected) editor.focus(); });
    editorDiagnosticLog.emit('colour-confirmed');
    if (!value || editor.state.doc !== documentAtOpen) return;
    const {from, to} = range || selection.main;
    editor.dispatch({changes: {from, to, insert: value}, selection: {anchor: from + value.length}, userEvent:'input'});
  } catch (error) {
    editorDiagnosticLog.emit(error?.message === 'cancelled'?'colour-cancelled':'colour-failed',{reason:error?.name || 'Error'});
    if (error?.message !== 'cancelled') {
      // Constructor/mount failures are not cancellation. Keep the failure
      // observable without logging the source/selected colour.
      console.error('[NotebookEditor] Colour dialog failed', error?.name || 'Error', String(error?.stack || '').split('\n').slice(1).join('\n'));
      const alert = document.createElement('div');
      alert.className = 'editor-ui-error';alert.setAttribute('role','alert');
      alert.textContent = document.documentElement.lang === 'ru' ? 'Не удалось открыть выбор цвета. Ошибка записана в журнал редактора.' : 'Colour picker failed to open. See the editor log.';
      const close=document.createElement('button');close.textContent='×';close.onclick=()=>alert.remove();alert.append(close);document.body.append(alert);
    }
  } finally {
    pending = false;
  }
}
