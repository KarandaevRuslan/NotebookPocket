import {tooltips} from '@codemirror/view';
import {editorDiagnosticLog, selectionDiagnosticFields} from '../../logging/EditorDiagnostics.js';

// CodeMirror owns positioning/flipping; this host supplies the usable part of
// the WebView, excluding QuickTools and the IME/visual viewport. The portal
// avoids clipping by the editor container and keeps CodeMirror theme classes.
export function completionSpace(view) {
  const rect = view.scrollDOM.getBoundingClientRect();
  const viewport = window.visualViewport;
  const left = viewport?.offsetLeft || 0;
  const top = viewport?.offsetTop || 0;
  const space = {
    left: Math.max(rect.left, left) + 4,
    right: Math.min(rect.right, left + (viewport?.width || innerWidth)) - 4,
    top: Math.max(rect.top, top) + 4,
    bottom: Math.min(rect.bottom, top + (viewport?.height || innerHeight)) - 4
  };
  if(editorDiagnosticLog.enabled)editorDiagnosticLog.sample('completion-space',{...selectionDiagnosticFields(view),x:space.left,y:space.top,width:space.right-space.left,height:space.bottom-space.top},120);
  return space;
}

export function editorTooltips() {
  return tooltips({parent: document.body, position: 'fixed', tooltipSpace: completionSpace});
}

export const completionLayout = {aboveCursor: true};
