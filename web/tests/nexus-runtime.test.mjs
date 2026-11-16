import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { JSDOM } from 'jsdom';
import { observableCompletionSource } from '../src/acode/host/observableCompletions.js';

// Exercise exactly the CommonJS-to-browser interop used by the production preview.
const bundle = await build({
  stdin: {
    contents: `import './src/preview.js';
      export { Nexus, createNexusHost } from './src/preview/nexusRuntime.js';
      export { library } from '@observablehq/notebook-kit/runtime';`,
    resolveDir: fileURLToPath(new URL('../', import.meta.url)),
  },
  bundle: true, write: false, format: 'iife', globalName: 'NexusTest',
  platform: 'browser', loader: { '.css': 'empty' }, external: ['https://*'],
  logLevel: 'silent',
  plugins: [{
    name: 'keep-optional-builtins-lazy',
    setup(b) {
      b.onResolve({ filter: /.*/ }, a => a.kind === 'dynamic-import' ? { path: a.path, external: true } : null);
    },
  }],
});
const example = await readFile(new URL('../../examples/nexus-counter.html', import.meta.url), 'utf8');

function preview() {
  const dom = new JSDOM('<!doctype html><main id="notebook"></main>', {
    url: 'https://preview-nexus.notebook.invalid/assets/web/preview.html',
    runScripts: 'outside-only', pretendToBeVisual: true,
  });
  dom.window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  dom.window.fetch = () => { throw new Error('Network must not be used by Nexus'); };
  dom.window.eval(bundle.outputFiles[0].text);
  return dom;
}

async function until(check) {
  for (let i = 0; i < 150; i++) {
    if (check()) return;
    await new Promise(resolve => setTimeout(resolve, 20));
  }
  throw new Error('Nexus preview did not update');
}

function host(window, onError = error => { throw error; }) {
  return window.NexusTest.createNexusHost({
    observe: window.NexusTest.library.Generators().observe, onError,
  });
}

test('Nexus committed values bridge into real Observable generators; aborted writes stay private', async () => {
  const dom = preview();
  const owner = host(dom.window), n = owner.context;
  const scope = n.scope(), store = scope.store(1);
  const values = n.observe(scope, store);
  try {
    assert.equal(n.version, '0.1.20');
    assert.equal((await values.next()).value, 1);
    assert.throws(() => n.dispatcher.inline(ctx => { ctx.set(store, 99); throw new Error('abort'); }), /abort/);
    assert.equal(n.dispatcher.inline(ctx => ctx.read(store)), 1);
    n.dispatcher.inline(ctx => ctx.set(store, 2));
    assert.equal((await values.next()).value, 2);
    await values.return();
    // No active bridge remains to prevent Store teardown.
    scope.dispose();
    assert.equal(scope.active, false);
    assert.throws(() => n.dispatcher.inline(ctx => ctx.read(store)), /registered/);
  } finally { owner.dispose(); dom.window.close(); }
});

test('scope invalidation, queued-command cancellation and both bridge teardown orders work', async () => {
  const dom = preview();
  const errors = [], owner = host(dom.window, error => errors.push(error)), n = owner.context;
  let invalidate;
  const scope = n.scope(new Promise(resolve => { invalidate = resolve; }));
  const store = scope.store(0), values = n.observe(scope, store);
  try {
    assert.equal((await values.next()).value, 0);
    const pending = scope.submit(ctx => ctx.set(store, 10)).catch(error => error);
    invalidate();
    await until(() => !scope.active);
    assert.match(String(await pending), /cancel|Scope|disposed/i);
    await values.return(); // source disposed before the consumer
    assert.equal(errors.length, 0);
    owner.dispose();
    assert.throws(() => n.scope(), /disposed/);
    assert.throws(() => n.observe(scope, store), /active scope/);
  } finally { owner.dispose(); dom.window.close(); }
});

test('preview hosts are isolated and cleanup failure does not block unrelated scopes', () => {
  const dom = preview();
  const a = host(dom.window), b = host(dom.window);
  const sa = a.context.scope(), sb = b.context.scope(), store = sb.store(7);
  try {
    assert.notEqual(a.context.dispatcher, b.context.dispatcher);
    assert.throws(() => a.context.observe(sa, store), /this preview/);
    assert.throws(() => a.context.scope(123), /invalidation Promise/);
    let fail = true;
    const good = a.context.scope(), bad = a.context.scope();
    bad.cleanup(() => { if (fail) throw new Error('retry'); });
    assert.throws(() => a.dispose(), /cleanup failed/);
    assert.equal(good.active, false);
    assert.equal(sa.active, false);
    assert.equal(sb.active, true);
    assert.equal(b.context.dispatcher.inline(ctx => ctx.read(store)), 7);
    fail = false;
    assert.doesNotThrow(() => a.dispose());
  } finally { a.dispose(); b.dispose(); dom.window.close(); }
});

test('real imported notebook mixes Nexus Handler, OJS subscription and JavaScript 2.0 outputs', async () => {
  const dom = preview(), w = dom.window, d = w.document;
  try {
    await w.PreviewApi.render(example, 'nexus-example');
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '0');
    const oldButton = d.querySelector('[data-nexus-increment]');
    oldButton.click();
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '2');
    oldButton.click();
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '4');
    assert.equal(d.querySelectorAll('.error, .observablehq--error').length, 0);
    assert.equal(w.PreviewApi.resetState(), true);
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '0');
    oldButton.click(); // a late foreign event after reset cannot modify the new preview
    d.querySelector('[data-nexus-increment]').click();
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '2');
    await w.PreviewApi.render(example, 'another-document', true);
    await until(() => d.querySelector('[data-nexus-result]')?.textContent === '0');
    assert.equal(w.localStorage.length, 0); // Nexus state has no persistent storage
  } finally { w.PreviewApi.destroy(); dom.window.close(); }
});

test('preview rerender, empty source, and destroy release cell-owned Nexus resources', async () => {
  const dom = preview(), w = dom.window;
  const source = `<notebook><title>Lifecycle</title><script id="1" type="module">
    const scope = nexus.scope(invalidation);
    const store = scope.store(1);
    scope.cleanup(() => { window.nexusCleanupCount = (window.nexusCleanupCount || 0) + 1; });
    window.nexusProbe = {scope, store, dispatcher: nexus.dispatcher};
    display("ready");
  </script></notebook>`;
  try {
    await w.PreviewApi.render(source, 'first');
    await until(() => w.nexusProbe);
    const first = w.nexusProbe;
    await w.PreviewApi.render('<notebook><title>Empty</title></notebook>', 'empty');
    assert.equal(first.scope.active, false);
    assert.equal(w.nexusCleanupCount, 1);
    assert.throws(() => first.dispatcher.inline(ctx => ctx.read(first.store)), /registered/);
    await w.PreviewApi.render(source, 'second');
    await until(() => w.nexusProbe !== first);
    const second = w.nexusProbe;
    assert.notEqual(first.dispatcher, second.dispatcher);
    w.PreviewApi.destroy();
    assert.equal(second.scope.active, false);
    await Promise.resolve();
    assert.equal(w.nexusCleanupCount, 2);
  } finally { w.PreviewApi.destroy(); dom.window.close(); }
});

test('editor suggests Nexus core and preview bridge APIs', () => {
  const result = observableCompletionSource({
    matchBefore: () => ({ from: 0, to: 6, text: 'nexus.' }), explicit: true,
  });
  assert.ok(result.options.some(o => o.label === 'nexus.scope'));
  assert.ok(result.options.some(o => o.label === 'nexus.observe'));
  assert.ok(result.options.some(o => o.label === 'Nexus.Dispatcher'));
});

test('Observable input recomputation disposes the old Nexus scope and reconnects its dependent generator', async () => {
  const dom = preview(), w = dom.window, d = w.document;
  const source = `<notebook><title>Recompute</title>
    <script id="1" type="module">
      const seed = view(html\`<input type="number" value="1" data-nexus-seed>\`);
    </script>
    <script id="2" type="module">
      const scope = nexus.scope(invalidation);
      const store = scope.store(seed);
      window.nexusProbe = {scope, store};
    </script>
    <script id="3" type="application/vnd.observable.javascript">
      value = nexus.observe(scope, store)
    </script>
    <script id="4" type="module">
      display(html\`<output data-recomputed>\${value}</output>\`);
    </script></notebook>`;
  try {
    await w.PreviewApi.render(source, 'recompute');
    await until(() => d.querySelector('[data-recomputed]')?.textContent === '1');
    const oldScope = w.nexusProbe.scope;
    const input = d.querySelector('[data-nexus-seed]');
    input.value = '5';
    input.dispatchEvent(new w.Event('input', { bubbles: true }));
    await until(() => d.querySelector('[data-recomputed]')?.textContent === '5');
    assert.equal(oldScope.active, false);
    assert.equal(w.nexusProbe.scope.active, true);
    assert.equal(d.querySelectorAll('.error, .observablehq--error').length, 0);
  } finally { w.PreviewApi.destroy(); dom.window.close(); }
});
