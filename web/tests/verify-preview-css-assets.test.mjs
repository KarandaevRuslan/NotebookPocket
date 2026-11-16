import test from 'node:test';
import assert from 'node:assert/strict';
import {linkedPreviewStylesheets} from '../scripts/linked-preview-stylesheets.mjs';

// Reproduces Vite 7's cssCodeSplit:false output from NotebookPocket 0.9.3:
// the preview JS entry has no `css` property, and a separate style.css entry
// points to the stylesheet linked from the generated HTML.
const manifest = {
  'preview.html': {
    file: 'assets/preview-123.js', name: 'preview', src: 'preview.html', isEntry: true,
    dynamicImports: []
  },
  'style.css': {file: 'assets/style-456.css', src: 'style.css'}
};
const previewHtml = `<!doctype html><html><head>
  <script type="module" crossorigin src="./assets/preview-123.js"></script>
  <link rel="stylesheet" crossorigin href="./assets/style-456.css">
</head></html>`;

test('standalone Vite style.css is accepted even without entry.css', () => {
  assert.equal(manifest['preview.html'].css, undefined);
  assert.deepEqual(linkedPreviewStylesheets(previewHtml, manifest), ['assets/style-456.css']);
});

test('a stylesheet only in manifest is insufficient (it must load in Preview)', () => {
  assert.deepEqual(linkedPreviewStylesheets('<script src="./assets/preview-123.js"></script>', manifest), []);
});

test('broken CSS HTML reference is rejected', () => {
  assert.throws(() => linkedPreviewStylesheets(
    previewHtml.replace('style-456.css', 'missing.css'), manifest
  ), /absent from Vite manifest/);
});

test('HTML attribute ordering and duplicate links do not affect CSS discovery', () => {
  const html = `<link href="./assets/style-456.css" crossorigin rel="stylesheet" />` +
    `<link rel="stylesheet" href="./assets/style-456.css" />`;
  assert.deepEqual(linkedPreviewStylesheets(html, manifest), ['assets/style-456.css']);
});

test('remote CSS is not interpreted as a local Preview stylesheet', () => {
  assert.deepEqual(linkedPreviewStylesheets(
    '<link rel="stylesheet" href="https://example.org/style.css">', manifest
  ), []);
});
