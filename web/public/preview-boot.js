window.__npBootStage = 'preview-boot';
console.info('[NP-Boot] preview-boot installed');
// The preview deliberately has no Android JavaScript bridge.
// Failures are rendered in-page, including Vite preload errors.
function showPreviewError(reason) {
  window.__npBootError = String(reason?.stack || reason?.message || reason);
  console.error('[NP-Boot] preview error', window.__npBootError);
  const container = document.getElementById('notebook');
  if (!container) return;
  const node = document.createElement('pre');
  node.className = 'error';
  node.style.whiteSpace = 'pre-wrap';
  node.textContent = String(reason?.stack || reason?.message || reason);
  container.append(node);
}
window.addEventListener('error', event => showPreviewError(event.error || event.message));
window.addEventListener('unhandledrejection', event => showPreviewError(event.reason));
window.addEventListener('vite:preloadError', event => showPreviewError(event.payload));
