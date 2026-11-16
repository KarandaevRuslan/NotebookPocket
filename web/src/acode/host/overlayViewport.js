/** Acode runs full-screen; our WebView also starts hidden at size zero.
 * Device logs show vh/dvh-based limits staying at zero even after the fixed
 * shell and visual viewport have their real size. Overlay limits must use
 * that host geometry. This never changes editor height, scroll or selection.
 */
export function updateOverlayViewport(shell = document.getElementById('editor-shell')) {
  const rect = shell?.getBoundingClientRect();
  const visual = window.visualViewport;
  const left = Math.max(rect?.left || 0, visual?.offsetLeft || 0);
  const top = Math.max(rect?.top || 0, visual?.offsetTop || 0);
  const right = Math.min(rect?.width > 0 ? rect.right : window.innerWidth,
    visual?.width > 0 ? (visual.offsetLeft || 0) + visual.width : window.innerWidth);
  const bottom = Math.min(rect?.height > 0 ? rect.bottom : window.innerHeight,
    visual?.height > 0 ? (visual.offsetTop || 0) + visual.height : window.innerHeight);
  const width = right - left, height = bottom - top;
  // Retain the last usable limits during the hidden/zero-size transition.
  if (!(width > 0 && height > 0)) return;
  const style = document.documentElement.style;
  for (const [key, value] of [['width', width], ['height', height]]) {
    const name = '--editor-ui-' + key, next = value + 'px';
    if (style.getPropertyValue(name) !== next) style.setProperty(name, next);
  }
}
