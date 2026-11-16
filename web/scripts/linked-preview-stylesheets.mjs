/** Return manifest-verified local CSS actually linked by the built Preview HTML. */
import assert from 'node:assert/strict';

export function linkedPreviewStylesheets(html, manifest) {
  // Vite emits the combined style sheet as a separate `style.css` record when
  // build.cssCodeSplit=false; JS entry.css can legitimately be absent.
  const emitted = new Set(Object.values(manifest).flatMap(chunk =>
    [chunk.file, ...(chunk.css ?? [])].filter(asset => asset?.endsWith('.css'))
  ));
  const linked = [...html.matchAll(/<link\b[^>]*>/gi)]
    .filter(([tag]) => /\brel\s*=\s*["']stylesheet["']/i.test(tag))
    .map(([tag]) => tag.match(/\bhref\s*=\s*["']\.?\/(assets\/[^"']+\.css)["']/i)?.[1])
    .filter(Boolean);
  for (const asset of linked) {
    assert.ok(emitted.has(asset), `Preview stylesheet is absent from Vite manifest: ${asset}`);
  }
  return [...new Set(linked)];
}
