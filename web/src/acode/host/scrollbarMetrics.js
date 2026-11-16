/** The inactive Code WebView measures at 0px; that is not proof of no overflow. */
export function scrollbarOverflow(metrics, wordWrap = false) {
  if (!metrics || metrics.clientWidth <= 0 || metrics.clientHeight <= 0) return null;
  return {
    vertical: metrics.scrollHeight > metrics.clientHeight + 1,
    horizontal: !wordWrap && metrics.scrollWidth > metrics.clientWidth + 1,
  };
}
