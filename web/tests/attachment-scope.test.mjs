import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentResolver } from '../src/attachment-resolver.js';

const scope = 'a03dc6c7-87ad-4f26-b5cd-c9dd3d708bae';
const previewHost = `preview-${'9b8bec911269585ad1b4c6412e93ae97'}.notebook.invalid`;
const previewBase = `https://${previewHost}/notebook-resources/${scope}/`;
const legacyBase = `https://appassets.androidplatform.net/notebook-resources/${scope}/`;

test('0.9.3: preview origin accepts scoped attachments', () => {
  for (const base of [previewBase, legacyBase]) {
    const resolve = attachmentResolver(['images/мой файл.png', 'shared/data.json'], base);
    assert.equal(resolve('images/мой файл.png')?.href,
      `${base}images/${encodeURIComponent('мой файл.png')}`);
    assert.equal(resolve('shared/data.json')?.href, `${base}shared/data.json`);
    assert.equal(resolve('missing'), null);
  }
});

test('attachment scope rejects foreign origins and malformed paths', () => {
  for (const base of [
    `https://evil.example/notebook-resources/${scope}/`,
    `http://${previewHost}/notebook-resources/${scope}/`,
    `https://preview-short.notebook.invalid/notebook-resources/${scope}/`,
    `https://${previewHost}.evil.example/notebook-resources/${scope}/`,
    `https://${previewHost}/other/${scope}/`,
    `https://${previewHost}/notebook-resources/${scope}/../`,
    `https://${previewHost}/notebook-resources/${scope}/?q=1`,
  ]) {
    assert.throws(() => attachmentResolver(['x'], base), /Invalid attachment scope/);
  }
  for (const name of ['../x', '/x', 'a/../x', 'a//b', 'a\\b', 'https://other/x']) {
    assert.equal(attachmentResolver([name], previewBase)(name), null);
  }
});
