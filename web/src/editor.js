/** Native WebView entry point. Construction and native API are wired in editor/application. */
import './acode/host/environment.js';
import './acode/vendor/handlers/editorWorkaround.js';
import './acode/host/skin.scss';
import { startEditor } from './editor/application.js';
import { messagesFor } from './locales/index.js';

window.__npBootStage = 'Acode editor bundle';
window.EditorApi = startEditor({ host: document.getElementById('editor') });
if (!window.NativeEditor)
  window.EditorApi.setSource(
    `<!doctype html>\n<notebook>\n <title>${messagesFor(document.documentElement.lang).newNotebook}</title>\n</notebook>\n`,
  );
window.__npBootStage = 'Acode editor ready';
window.NativeEditor?.ready();
