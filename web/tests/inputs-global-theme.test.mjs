import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('Observable Inputs 2.x theme loads for every Preview, not for the demo alone', async () => {
  const entry = await readFile(new URL('../src/preview.js', import.meta.url), 'utf8');
  const theme = await readFile(new URL('../src/preview/inputs-theme.css', import.meta.url), 'utf8');
  const runner = await readFile(new URL('../src/preview/notebookRuntime.js', import.meta.url), 'utf8');
  const buildCheck = await readFile(new URL('../scripts/verify-vite-assets.mjs', import.meta.url), 'utf8');
  assert.match(entry, /import '\.\/preview\/inputs-theme\.css'/);
  assert.match(runner, /root\.className = 'cell'/);
  assert.match(theme, /body\.preview-page #notebook/);
  assert.match(theme, /form\[class\*="inputs-"\]/);
  assert.match(theme, /input\[type="range"\]/);
  assert.match(theme, /input\[type="radio"\]/);
  assert.match(theme, /table\[class\*="inputs-"\]/);
  assert.doesNotMatch(theme, /blini-|pancakes|demo\/|\.pancake/);
  assert.match(buildCheck, /Global Observable Inputs theme missing from built Preview CSS/);
});
