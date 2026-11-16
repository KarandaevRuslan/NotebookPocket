/** Resolves only registered files of the active notebook; paths never become disk paths. */
export function attachmentResolver(names = [], base = '') {
  const registered = new Set(names);
  // Preview pages moved to a per-notebook origin in 0.9.3. Preserve legacy
  // appassets support for older callers/tests, while accepting only the exact
  // app-generated preview hostname shape (never arbitrary HTTPS origins).
  const validBase = /^https:\/\/(?:appassets\.androidplatform\.net|preview-[0-9a-f]{32}\.notebook\.invalid)\/notebook-resources\/[a-z0-9-]+\/$/;
  if (base && !validBase.test(base)) throw new Error('Invalid attachment scope');
  return (name) => {
    if (!registered.has(name) || !base || typeof name !== 'string' || name.includes('\\') || name.includes(':') || /[\x00-\x1f\x7f]/.test(name) || name.split('/').some(p => !p || p === '.' || p === '..')) return null;
    return {href: base + name.split('/').map(encodeURIComponent).join('/')};
  };
}
