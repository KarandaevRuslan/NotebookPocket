import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('Notebook Kit lazy imports remain lazy, CSS is emitted once per entry', async () => {
  const config = await readFile(new URL('../vite.config.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(config, /inlineDynamicImports:\s*true/);
  assert.match(config, /cssCodeSplit:\s*false/);
  assert.match(config, /input:\s*resolve\(import\.meta\.dirname, `\$\{mode\}\.html`\)/);
});

test('Preview only permits self-hosted JS: do not weaken CSP to suppress the error', async () => {
  const html = await readFile(new URL('../preview.html', import.meta.url), 'utf8');
  assert.match(html, /script-src 'self' 'unsafe-eval'/);
  assert.doesNotMatch(html, /script-src[^"']*https?:/);
});
