/** Validate all locally required JS/CSS assets, including lazy chunks. */
import assert from 'node:assert/strict';
import {readFile, access, readdir} from 'node:fs/promises';
import {resolve, join} from 'node:path';
import {linkedPreviewStylesheets} from './linked-preview-stylesheets.mjs';

const root = resolve(import.meta.dirname, '../dist');
const pageRefs = new Set();
const allFiles = new Set((await readdir(join(root, 'assets'))).map(file => `assets/${file}`));

for (const mode of ['editor', 'preview']) {
  const html = await readFile(join(root, `${mode}.html`), 'utf8');
  const manifest = JSON.parse(await readFile(join(root, '.vite', `manifest-${mode}.json`), 'utf8'));
  const entries = Object.values(manifest).filter(chunk => chunk.isEntry);
  assert.equal(entries.length, 1, `${mode}: expected exactly one JS entry`);
  for (const [name, chunk] of Object.entries(manifest)) {
    for (const asset of [chunk.file, ...(chunk.css ?? []), ...(chunk.assets ?? [])]) {
      if (asset) await access(join(root, asset));
    }
    for (const ref of [...(chunk.imports ?? []), ...(chunk.dynamicImports ?? [])]) {
      assert.ok(manifest[ref], `${mode}: missing imported chunk ${ref} (from ${name})`);
    }
  }
  assert.doesNotMatch(html, /(?:src|href)=["']\/src\//, `${mode}: unbuilt development script`);
  assert.doesNotMatch(html, /<script[^>]*src=["']https?:/i, `${mode}: external startup script`);
  for (const match of html.matchAll(/(?:src|href)=["']\.?\/assets\/([^"']+)["']/g)) {
    const asset = `assets/${match[1]}`;
    pageRefs.add(`${mode}:${asset}`);
    assert.ok(allFiles.has(asset), `${mode}: HTML references missing ${asset}`);
    await access(join(root, asset));
  }
  assert.match(html, /<script\s+[^>]*type="module"[^>]*src="\.?\/assets\//,
    `${mode}: missing locally bundled module`);
  // A remote stylesheet import would still be blocked by Preview's offline
  // CSP. Catch it during build instead of on the customer's Android device.
  for (const asset of allFiles) {
    if (!asset.endsWith('.css')) continue;
    const css = await readFile(join(root, asset), 'utf8');
    assert.doesNotMatch(css, /@import\s+(?:url\()?\s*["']?https?:/i,
      `${asset}: remote CSS import is forbidden in the offline bundle`);
  }
  // The bundled Inputs 2.x theme must be in the actual Preview CSS, not
  // merely present as an unused source file or in the Pancakes demo styles.
  if (mode === 'preview') {
    // With cssCodeSplit:false, Vite emits a top-level `style.css` manifest
    // record and <link rel="stylesheet"> in preview.html; it does NOT put
    // the stylesheet in preview.html's entry.css array. Follow the actual HTML
    // links instead so the verifier checks CSS that Android WebView will load.
    const cssAssets = linkedPreviewStylesheets(html, manifest);
    assert.ok(cssAssets.length, 'Preview must emit local CSS for Notebook Kit Inputs');
    const cssText = (await Promise.all(cssAssets.map(asset => readFile(join(root, asset), 'utf8')))).join('\n');
    assert.ok(cssText.includes('np-input-button-group') && cssText.includes('#notebook'),
      'Global Observable Inputs theme missing from built Preview CSS');
  }
  console.log(`[${mode}] JS entry, lazy chunks and local CSS verified`);
}
console.log(`Verified ${pageRefs.size} HTML resource references and manifest imports.`);

const acodeManifest=JSON.parse(await readFile(new URL('../../third_party/acode/full-editor-manifest.json',import.meta.url),'utf8'));
const acodeRuntime=JSON.parse(await readFile(join(root,'.vite/acode-runtime-modules.json'),'utf8'));
const sourceModules=acodeManifest.files.filter(e=>/\.(js|ts)$/.test(e.local));
for(const entry of sourceModules)assert.ok(Object.hasOwn(acodeRuntime,entry.local),`Acode source is not in the editor bundle: ${entry.local}`);
console.log(`Verified ${sourceModules.length} original/adapted Acode JS/TS files in the built editor graph.`);
