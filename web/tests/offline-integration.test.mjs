import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

async function source(rel) {
  return readFile(new URL(`../${rel}`, import.meta.url), 'utf8');
}

test('Observable uses actual npm Inputs and htl instead of CDN for everyday cells', async () => {
  const offline = await source('src/observable-offline-library.js');
  const preview = await source('src/preview/notebookRuntime.js');
  assert.match(offline, /from '@observablehq\/inputs'/);
  assert.match(offline, /from 'htl'/);
  assert.match(offline, /import '@observablehq\/inputs\/dist\/index\.css'/);
  assert.match(offline, /\.\.\.library/);
  assert.match(preview, /new NotebookRuntime\(offlineLibrary\)/);
});

test('Offline Vite replaces duplicate remote Inputs stylesheet import', async () => {
  const cfg = await source('vite.config.mjs');
  assert.match(cfg, /offline-inputs-css/);
  assert.match(cfg, /rule\.remove\(\)/);
});

test('CodeMirror uses fixed viewport and workspace reserves remaining height', async () => {
  const css = await source('src/acode/host/skin.scss');
  assert.match(css, /#editor-shell\{position:fixed;inset:0;/);
  assert.match(css, /#editor\{flex:1 1 0;min-height:0/);
  const route = await readFile(new URL('../../app/src/main/java/dev/pocket/notebook/feature/workspace/NotebookDetailScreen.kt', import.meta.url), 'utf8');
  assert.match(route, /Row\(Modifier\.weight\(1f\)\.fillMaxWidth\(\)\)/);
  assert.match(route, /Box\(\s*Modifier\.weight\(1f\)\s*\.fillMaxWidth\(\)/);
});
