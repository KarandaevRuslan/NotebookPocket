window.__npBootStage = 'editor-boot';
console.info('[NP-Boot] editor-boot installed');
// Runs before the compiled CodeMirror bundle and reports failures to the native
// loading/error overlay (not just the JS console).
function reportEditorFailure(reason) {
  const message = String(reason?.stack || reason?.message || reason || 'Editor bundle could not load');
  console.error('[NotebookPocket CodeMirror]', message);
  window.NativeEditor?.failed(message);
}
// Chromium emits this non-fatal error when resize notifications are deferred
// to the next frame. It must not be treated as a CodeMirror startup failure.
function isResizeObserverLoopNotice(message) {
  return /^ResizeObserver loop (?:completed with undelivered notifications\.|limit exceeded)$/.test(String(message || ''));
}
window.addEventListener('error', event => {
  if (isResizeObserverLoopNotice(event.message)) {
    event.preventDefault();
    console.debug('[NP-Editor] deferred ResizeObserver notification');
    return;
  }
  reportEditorFailure(event.error || event.message);
});
window.addEventListener('unhandledrejection', event => reportEditorFailure(event.reason));
window.addEventListener('vite:preloadError', event => reportEditorFailure(event.payload));
document.addEventListener('error', event => {
  if (event.target?.tagName === 'SCRIPT' || event.target?.tagName === 'LINK') {
    reportEditorFailure('Missing bundled resource: ' + (event.target.src || event.target.href));
  }
}, true);
