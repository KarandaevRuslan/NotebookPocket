import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';

for (const page of ['editor.html', 'preview.html']) {
  test(`${page}: favicon request is disabled and offline entrypoint is local`, async () => {
    const html = await readFile(new URL(`../${page}`, import.meta.url), 'utf8');
    assert.match(html, /<link\s+rel="icon"\s+href="data:,"\s*\/>/);
    assert.match(html, /<script\s+type="module"\s+src="\/src\//);
    assert.doesNotMatch(html, /<script[^>]+src="https?:/);
  });
}
