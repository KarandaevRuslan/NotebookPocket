import { startPreview } from './preview/application.js';
import { notebookLog } from './logging/NotebookLog.js';
import './styles.css';
// Included in every Preview bundle, not injected by the Pancakes demo.
import './preview/inputs-theme.css';

notebookLog.info('PREVIEW.BOOT', 'Observable module loaded');
window.__npBootStage = 'preview bundle';
window.PreviewApi = startPreview({ main: document.getElementById('notebook') });
window.__npBootStage = 'preview ready';
notebookLog.info('PREVIEW.BOOT', 'PreviewApi ready');
window.PreviewApi.setPreferences(window.__appPreferences);
if (window.__initialPreview)
  window.PreviewApi.render(
    window.__initialPreview.source,
    window.__initialPreview.id,
  );
