import {fileURLToPath} from 'node:url';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {build} from 'esbuild';
import {JSDOM} from 'jsdom';

const bundle = await build({entryPoints: [fileURLToPath(new URL('../src/preview/formState.js', import.meta.url))],
  bundle: true, write: false, format: 'iife', globalName: 'FormState', logLevel: 'silent'});
function fixture() {
  const dom = new JSDOM('<main><section data-cell-id="1"><input value="default"><form data-np-persist-step data-np-max-step="6"></form></section></main>', {runScripts: 'outside-only'});
  const w = dom.window, main = w.document.querySelector('main');
  const records = new Map(), calls = [];
  const storage = {
    getItem(key) { calls.push(['read', key]); return records.get(key) ?? null; },
    setItem(key, value) { calls.push(['write', key]); records.set(key, value); },
    removeItem(key) { calls.push(['remove', key]); records.delete(key); }
  };
  w.eval(bundle.outputFiles[0].text);
  const forms = w.FormState.createFormState(main, storage);
  return {dom, w, main, records, calls, forms, close() {forms.destroy(); dom.window.close();}};
}
test('protected forms purge previous plaintext state and never read or persist field values', () => {
  const f = fixture();
  try {
    f.records.set('secret', '{"1:0":{"value":"old secret"}}');
    f.forms.begin('secret', true);
    const input = f.main.querySelector('input');
    input.value = 'private input'; input.dispatchEvent(new f.w.Event('input', {bubbles: true}));
    const step = f.main.querySelector('form');step.value = 3;step.dispatchEvent(new f.w.Event('input', {bubbles: true}));
    f.forms.restore();
    assert.deepEqual(f.calls, [['remove', 'secret']]);
    assert.equal(f.records.size, 0);
    assert.equal(input.value, 'private input');
  } finally { f.close(); }
});
test('public state restores once and reactive replacements retain their computed defaults', () => {
  const f = fixture();
  try {
    f.records.set('public', '{"1:0":{"value":"saved"}}');f.forms.begin('public', false);f.forms.restore();
    assert.equal(f.main.querySelector('input').value, 'saved');
    const next = f.w.document.createElement('input');next.value = 'recomputed';f.main.querySelector('input').replaceWith(next);
    f.forms.restore();assert.equal(next.value, 'recomputed');
    next.value = 'edited';next.dispatchEvent(new f.w.Event('change', {bubbles: true}));
    assert.equal(JSON.parse(f.records.get('public'))['1:0'].value, 'edited');
  } finally { f.close(); }
});
test('destroy detaches persistence listeners', () => {
  const f = fixture();
  try {
    f.forms.begin('public', false);f.forms.destroy();
    f.main.querySelector('input').dispatchEvent(new f.w.Event('input', {bubbles: true}));
    assert.equal(f.records.size, 0);
  } finally { f.close(); }
});
test('native Preview passes the protection flag into the isolated renderer', () => {
  const native = readFileSync(new URL('../../app/src/main/java/dev/pocket/notebook/web/NotebookPreviewWebView.kt', import.meta.url), 'utf8');
  assert.match(native, /window\.PreviewApi\.render\(p\.source, p\.id, \$encrypted(?:,|\))/);
});
