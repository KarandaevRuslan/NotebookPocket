// Chrome/status-bar colors are owned by the Compose host, outside this WebView.
export default function restoreTheme(darken = false) {
  document.body.classList.toggle('acode-overlay-open', darken);
}
