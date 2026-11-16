import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { transform } from 'esbuild';
import { JSDOM } from 'jsdom';

// Run the minified Vite output, replacing only import.meta.url for JSDOM's eval.
// This verifies package interop and startup, not Android WebView layout.
const base = new URL('../dist/', import.meta.url);
const manifest = JSON.parse(await readFile(new URL('.vite/manifest-preview.json', base), 'utf8'));
const entry = manifest['preview.html'].file;
const origin = 'https://preview-nexus.notebook.invalid/assets/web/';
const { code } = await transform(await readFile(new URL(entry, base), 'utf8'), {
  format: 'iife', target: 'es2022',
  define: { 'import.meta.url': JSON.stringify(origin + entry) },
});
const dom = new JSDOM('<!doctype html><main id="notebook"></main>', {
  url: origin + 'preview.html', runScripts: 'outside-only', pretendToBeVisual: true,
});
const w = dom.window, errors = [];
w.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
w.console.error = (...args) => errors.push(args.map(String).join(' '));
w.fetch = () => { throw new Error('Nexus must not request network resources'); };
async function until(check) {
  for (let i = 0; i < 150; i++) {
    if (check()) return;
    await new Promise(resolve => setTimeout(resolve, 20));
  }
  throw new Error('Production preview did not update: ' + errors.join('\n'));
}
const value = () => w.document.querySelector('[data-nexus-result]')?.textContent;
try {
  w.eval(code);
  assert.ok(w.PreviewApi);
  const source = await readFile(new URL('../../examples/nexus-counter.html', import.meta.url), 'utf8');
  await w.PreviewApi.render(source, 'production-nexus-smoke');
  await until(() => value() === '0');
  w.document.querySelector('[data-nexus-increment]').click();
  await until(() => value() === '2');
  assert.equal(w.PreviewApi.resetState(), true);
  await until(() => value() === '0');
  assert.equal(w.document.querySelectorAll('.error, .observablehq--error').length, 0);
  assert.deepEqual(errors, []);
  console.log('Production preview: offline Nexus → Observable update and reset verified.');
} finally {
  w.PreviewApi?.destroy();
  w.close();
}
