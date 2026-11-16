import {EditorSelection, EditorState, Transaction} from '@codemirror/state';
import {EditorView} from '@codemirror/view';
import {editorDiagnosticLog} from '../../logging/EditorDiagnostics.js';
import {recordPipeline} from '../../logging/EditorPipelineDiagnostics.js';

/** Android may replace the untouched offscreen endpoint with the rendered
 * boundary when a native handle moves. Correct that incoming DOM transaction,
 * leaving CM responsible for selection and DOM synchronization. */
export function preserveOffscreenEndpoint(before, next, viewport) {
  if (before.empty || next.empty || before.eq(next)) return null;
  let from = next.from, to = next.to;
  if (before.from < viewport.from && before.to >= viewport.from && before.to <= viewport.to &&
      next.from >= viewport.from && next.from <= viewport.to && next.to <= viewport.to) {
    from = before.from;
  } else if (before.to > viewport.to && before.from >= viewport.from && before.from <= viewport.to &&
             next.to <= viewport.to && next.to >= viewport.from && next.from >= viewport.from) {
    to = before.to;
  } else return null;
  return next.anchor <= next.head ? EditorSelection.single(from, to) : EditorSelection.single(to, from);
}

/** CM's Chrome block-gap/select-all heuristic always sets anchor=length,
 * head=0, including when the user simply moves the lower handle to EOF.
 * If the DOM already reports the same full range precisely, it also tells
 * us which end is active. Keep that direction instead of revealing BOF.
 */
function fullRangeDOMDirection(view, next) {
  if (next.empty || next.from !== 0 || next.to !== view.state.doc.length || next.head !== 0) return null;
  const selection = document.getSelection();
  if (!selection?.anchorNode || !selection.focusNode ||
      !view.contentDOM.contains(selection.anchorNode) || !view.contentDOM.contains(selection.focusNode)) return null;
  try {
    const anchor = view.posAtDOM(selection.anchorNode, selection.anchorOffset);
    const head = view.posAtDOM(selection.focusNode, selection.focusOffset);
    return anchor === 0 && head === next.to ? EditorSelection.single(anchor, head) : null;
  } catch { return null; }
}

export function nativeSelectionInput(getView) {
  if (!/Android/i.test(navigator.userAgent)) return [];
  let pointerDown = false, pointerScrolled = false, freshPointerUntil = 0;
  const beginPointer = () => { pointerDown = true; pointerScrolled = false; };
  const endPointer = () => { pointerDown = false; freshPointerUntil = pointerScrolled ? 0 : Date.now() + 350; };
  const cancelPointer = () => { pointerDown = false; freshPointerUntil = 0; };
  return [EditorView.domEventObservers({
    pointerdown: beginPointer, pointerup: endPointer, pointercancel: cancelPointer,
    touchstart: beginPointer, touchend: endPointer, touchcancel: cancelPointer,
    scroll() { pointerScrolled = true; pointerDown = false; freshPointerUntil = 0; }
  }), EditorState.transactionFilter.of(tr => {
    const view = getView();
    const started = editorDiagnosticLog.enabled ? performance.now() : 0;
    let coordinateReads = 0;
    const trace = (decision, reason, corrected) => {
      if (!editorDiagnosticLog.enabled || !tr.selection) return;
      recordPipeline('selection-filter',{
        beforeAnchor:tr.startState.selection.main.anchor,beforeHead:tr.startState.selection.main.head,
        nextAnchor:tr.newSelection.main.anchor,nextHead:tr.newSelection.main.head,
        correctedAnchor:corrected?.main.anchor ?? -1,correctedHead:corrected?.main.head ?? -1,
        corrected:!!corrected,decision,reason,userEvent:tr.annotation(Transaction.userEvent)||'none',
        durationMs:performance.now()-started,coordinateReads,
        pointerDown,pointerScrolled,freshPointerMs:Math.max(0,freshPointerUntil-Date.now()),
        hasFocus:!!view?.hasFocus,viewportFrom:view?.viewport.from ?? -1,viewportTo:view?.viewport.to ?? -1,docLength:tr.startState.doc.length
      });
    };
    // Text input, search, explicit model commands, new taps/long presses,
    // collapsed ranges and multiple cursors must pass through unchanged.
    if (tr.docChanged || !tr.selection || tr.annotation(Transaction.userEvent) !== 'select' ||
        tr.startState.selection.ranges.length !== 1 || tr.newSelection.ranges.length !== 1) {
      trace('skip',tr.docChanged?'doc-change':!tr.selection?'no-selection':tr.annotation(Transaction.userEvent)!=='select'?'not-native-select':'multi-range');return tr;
    }
    if (!view?.hasFocus || view.state !== tr.startState) {trace('skip',!view?.hasFocus?'no-focus':'state-mismatch');return tr;}
    if (editorDiagnosticLog.enabled && editorDiagnosticLog.options.selectionMode==='observe') {
      trace('observe','correction-bypassed');return tr;
    }
    const before = tr.startState.selection.main, next = tr.newSelection.main;
    const direction = fullRangeDOMDirection(view, next);
    if (direction) {trace('correct','native-direction',direction);return [tr,{selection:direction,sequential:true}];}
    if (pointerDown || Date.now() < freshPointerUntil) {trace('skip',pointerDown?'pointer-down':'fresh-pointer');return tr;}
    // Chromium can clip the untouched anchor in one selectionchange and move
    // the visible head in the next. A one-endpoint change is therefore not
    // sufficient evidence of a legitimate handle move. Fresh DOM gestures
    // above still allow taps/long presses to replace the selection.
    if (before.empty || next.empty || before.eq(next)) {trace('pass',before.empty||next.empty?'collapsed':'unchanged');return tr;}
    let corrected = preserveOffscreenEndpoint(before, next, view.viewport);
    // CodeMirror keeps an overscan margin. A short document (or one very
    // long wrapped line) may still be fully rendered while its endpoint is
    // outside the actual scrolling window. Check visible coordinates too.
    if (!corrected) {
      const rect = view.scrollDOM.getBoundingClientRect();
      if (rect.height > 2 && rect.width > 2) {
        coordinateReads = 2;
        const from = view.posAtCoords({x:rect.left + 1,y:rect.top + 1}, false);
        const to = view.posAtCoords({x:rect.right - 1,y:rect.bottom - 1}, false);
        if (from != null && to != null && from < to) corrected = preserveOffscreenEndpoint(before, next, {from,to});
      }
    }
    trace(corrected?'correct':'pass',corrected?'offscreen-endpoint':'no-boundary-match',corrected);
    return corrected ? [tr, {selection: corrected, sequential: true}] : tr;
  })];
}
