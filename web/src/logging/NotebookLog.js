/** Central frontend entry point for non-trace events.
 * Preview never exposes a native JavascriptInterface; its messages are routed
 * through Android's WebChromeClient to the same NotebookPocket Logcat tag.
 * Do not pass notebook content, search terms or clipboard values to this API.
 */
const safeScope = scope => String(scope).toUpperCase().replace(/[^A-Z0-9_.-]/g, '').slice(0, 32);
function write(level, scope, message) {
  const text = `[${safeScope(scope)}] ${String(message).slice(0, 500)}`;
  console[level](text);
}
export const notebookLog = Object.freeze({
  debug: (scope, message) => write('debug', scope, message),
  info: (scope, message) => write('info', scope, message),
  warn: (scope, message) => write('warn', scope, message),
  error: (scope, message) => write('error', scope, message)
});
