/** Preview input state is separate from the notebook source and scoped to its ID/version. */
export function notebookStateKey(documentId, source) {
  const version = /<notebook\b[^>]*\bdata-state-version=["']([a-z0-9-]{1,32})["']/i.exec(source)?.[1];
  return `notebook-pocket:state:${documentId}${version ? ':' + version : ''}`;
}

/** Clear only the active document's saved form values and step progress. */
export function clearNotebookState(storage, documentId, source) {
  const key = notebookStateKey(documentId, source);
  storage.removeItem(key);
  return key;
}

/** Import one bounded 0.9.2 form-state object; never overwrite a newer per-origin state. */
export function migrateLegacyNotebookState(storage, documentId, source, formRaw, timersRaw = null) {
  const key = notebookStateKey(documentId, source);
  let imported = false;
  for (const [name, raw, limit] of [
    [key, formRaw, 2048],
    [`notebook-pocket:timers:${key}`, timersRaw, 64],
  ]) {
    if (typeof raw !== 'string' || raw.length > 65536) continue;
    try {
      const parsed = JSON.parse(raw);
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed) || Object.keys(parsed).length > limit) continue;
      if (storage.getItem(name) !== null) continue; // Never overwrite newer preview state.
      storage.setItem(name, raw);
      imported = true;
    } catch { /* Corrupt old state is not restored. */ }
  }
  return imported;
}
