/** Protected WebViews have DOM storage disabled. Never turn that native policy into a boot error. */
export function previewStorage() {
  try { return globalThis.localStorage; } catch { return memoryStorage(); }
}
export function memoryStorage() {
  const values = new Map();
  return {getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)};
}
