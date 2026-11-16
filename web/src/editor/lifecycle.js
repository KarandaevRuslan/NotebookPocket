import settings from '../acode/host/settings.js';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import actions, {
  cancelQuickToolsModifierInput,
  key,
} from '../acode/vendor/handlers/quickTools.js';
import keyboardHandler from '../acode/vendor/handlers/keyboard.js';
import windowResize from '../acode/vendor/handlers/windowResize.js';
import { updateOverlayViewport } from '../acode/host/overlayViewport.js';
import {
  recordResize,
  recordPipeline,
} from '../logging/EditorPipelineDiagnostics.js';

export function installEditorLifecycle({
  editor,
  host,
  runtime,
  getPreferences,
  setPreferences,
  scheduleSessionSave,
  saveSession,
  emitSearch,
  isDestroyed,
}) {
  const offSettings = settings.on('update', () => {
    emitSearch();
    if (
      editor &&
      parseInt(settings.value.fontSize) !== getPreferences().fontSize
    )
      setPreferences({
        fontSize: parseInt(settings.value.fontSize),
      });
  });
  editor.scrollDOM.addEventListener('scroll', scheduleSessionSave, {
    passive: true,
  });
  const onResize = (trigger) => {
    recordResize(editor, host, trigger);
    updateOverlayViewport();
    windowResize();
  };
  const onWindowResize = () => onResize('window-resize');
  const onVisualResize = () => onResize('visual-resize');
  window.addEventListener('resize', onWindowResize);
  window.visualViewport?.addEventListener('resize', onVisualResize);
  const resizeObserver = new ResizeObserver(() => onResize('host-resize'));
  resizeObserver.observe(host);
  const onSettledResize = () => {
    if (isDestroyed()) return;
    recordPipeline('pipeline-resize-settled');
    editor.requestMeasure();
    runtime.refresh('settled-resize');
    runtime.selectionMenu.onStateChanged();
  };
  windowResize.on('resize', onSettledResize);
  const onPageHide = () => {
    saveSession();
    emitSearch();
  };
  window.addEventListener('pagehide', onPageHide);
  // Persist Acode's own query state, including replace/navigation actions. This
  // does not maintain a second query, match list, selection or decoration model.
  quickTools.$footer.addEventListener('input', emitSearch);
  const emitAfterEvent = () => queueMicrotask(emitSearch);
  quickTools.$footer.addEventListener('click', emitAfterEvent);
  quickTools.$footer.addEventListener('touchend', emitAfterEvent);
  document.addEventListener('keydown', keyboardHandler);
  document.dispatchEvent(new Event('deviceready'));
  return {
    capture: onPageHide,
    destroy() {
      offSettings();
      editor.scrollDOM.removeEventListener('scroll', scheduleSessionSave);
      quickTools.$footer.removeEventListener('input', emitSearch);
      quickTools.$footer.removeEventListener('click', emitAfterEvent);
      quickTools.$footer.removeEventListener('touchend', emitAfterEvent);
      resizeObserver.disconnect();
      windowResize.off('resize', onSettledResize);
      window.removeEventListener('resize', onWindowResize);
      window.visualViewport?.removeEventListener('resize', onVisualResize);
      window.removeEventListener('pagehide', onPageHide);
      document.removeEventListener('keydown', keyboardHandler);
    },
  };
}
