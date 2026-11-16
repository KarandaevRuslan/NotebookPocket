import { previewStorage } from './storage.js';
import { createPocketHost } from '../pocket-context.js';
import { notebookLog } from '../logging/NotebookLog.js';
import { notebookStateKey, clearNotebookState, migrateLegacyNotebookState } from '../preview-state.js';
import { messagesFor, formatMessage } from '../locales/index.js';
import { createFormState } from './formState.js';
import { updateCellVisibility } from './cellPresentation.js';
import { renderNotebook, errorNode } from './notebookRuntime.js';

export function startPreview({
  main,
  storage = previewStorage(),
  pocketHost = createPocketHost(),
}) {
  const formState = createFormState(main, storage);
  let currentRuntime, observer;
  let storageKey = '',
    protectedMode = false,
    renderedSource = '',
    renderedDocumentId = '',
    uiLanguage = 'en';
  let currentAttachments = { names: [], base: '' };
  function message(key, replacements = {}) {
    return formatMessage(messagesFor(uiLanguage)[key], replacements);
  }

  function setPreferences(options = {}) {
    const { dark = false, language = 'en' } = options;
    pocketHost.configure(options);
    uiLanguage = language === 'ru' ? 'ru' : 'en';
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.documentElement.lang = uiLanguage;
    document.title = message('previewTitle');
  }
  async function render(
    source,
    documentId,
    encrypted = protectedMode,
    attachments = currentAttachments,
  ) {
    currentAttachments = attachments;
    observer?.disconnect();
    currentRuntime?.dispose();
    currentRuntime = undefined;
    // Versioned key prevents unrelated templates from sharing saved inputs.
    renderedSource = source;
    renderedDocumentId = documentId;
    storageKey = notebookStateKey(documentId, source);
    protectedMode = Boolean(encrypted);
    formState.begin(storageKey, protectedMode);
    main.replaceChildren();
    try {
      currentRuntime = renderNotebook({
        main,
        source,
        message,
        attachments,
        context: pocketHost.context({
          key: storageKey,
          storage,
          protectedMode,
        }),
      });
      observer = new MutationObserver(() => {
        updateCellVisibility(main);
        formState.restore();
      });
      observer.observe(main, { subtree: true, childList: true });
      updateCellVisibility(main);
      formState.restore();
    } catch (error) {
      main.append(errorNode(error?.stack || error));
    }
  }

  // Returning to defaults also resets persisted step progress; it does not touch
  // notebook source. Rebuild the real Observable runtime so all dependent cells
  // recompute from the original Inputs defaults.
  function resetState() {
    if (!renderedSource || !renderedDocumentId) return false;
    try {
      clearNotebookState(storage, renderedDocumentId, renderedSource);
      pocketHost.resetTimers(storageKey, storage);
    } catch (error) {
      // Protected forms never persist input data: resetting the runtime still works.
      if (!protectedMode) {
        notebookLog.warn('PREVIEW.STATE', 'Unable to clear saved inputs');
        return false;
      }
    }
    void render(renderedSource, renderedDocumentId);
    notebookLog.info('PREVIEW.STATE', 'Form state reset');
    return true;
  }

  return {
    render,
    // Called solely by the native compatibility reader before the first render.
    migrateLegacyState(documentId, source, formRaw, timersRaw) {
      return !protectedMode && migrateLegacyNotebookState(storage, documentId, source, formRaw, timersRaw);
    },
    setPreferences,
    resetState,
    syncClock: pocketHost.syncClock,
    destroy() {
      observer?.disconnect();
      currentRuntime?.dispose();
      currentRuntime = undefined;
      formState.destroy();
      main.replaceChildren();
    },
  };
}
