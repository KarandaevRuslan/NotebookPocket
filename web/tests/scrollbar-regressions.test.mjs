import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { scrollbarOverflow } from '../src/acode/host/scrollbarMetrics.js';

const metrics = (height, width, contentHeight, contentWidth) => ({
  clientHeight: height,
  clientWidth: width,
  scrollHeight: contentHeight,
  scrollWidth: contentWidth,
});

test('0x0 parked WebView is not a no-overflow signal', () => {
  assert.equal(scrollbarOverflow(metrics(0, 0, 0, 0)), null);
  assert.deepEqual(scrollbarOverflow(metrics(400, 300, 900, 500)), {
    vertical: true,
    horizontal: true,
  });
  assert.deepEqual(scrollbarOverflow(metrics(400, 300, 900, 500), true), {
    vertical: true,
    horizontal: false,
  });
  assert.deepEqual(scrollbarOverflow(metrics(400, 300, 400, 300)), {
    vertical: false,
    horizontal: false,
  });
});

test('scrollbar can reattach after a stale visible state and cancel pending removal', async () => {
  const previous = {
    window: globalThis.window,
    MutationObserver: globalThis.MutationObserver,
    __scrollbarTag: globalThis.__scrollbarTag,
  };
  class Element {
    constructor(tag, options = {}) {
      this.tag = tag;
      this.style = { ...(options.style || {}) };
      this.dataset = {};
      this.className = options.className || '';
      const classes = new Set(this.className.split(' ').filter(Boolean));
      this.classList = {
        add: (name) => classes.add(name),
        remove: (name) => classes.delete(name),
        contains: (name) => classes.has(name),
      };
      this.children = [];
      this.parent = null;
      for (const child of [...(options.children || []), ...(options.child ? [options.child] : [])])
        this.append(child);
    }
    append(child) {
      child.remove();
      child.parent = this;
      this.children.push(child);
    }
    remove() {
      if (this.parent) this.parent.children = this.parent.children.filter((child) => child !== this);
      this.parent = null;
    }
    get isConnected() { return !!this.parent; }
    getBoundingClientRect() { return { width: 360, height: 640 }; }
    addEventListener() {}
    removeEventListener() {}
  }
  globalThis.window = { addEventListener() {}, removeEventListener() {} };
  globalThis.MutationObserver = class { observe() {} disconnect() {} };
  globalThis.__scrollbarTag = (tag, options) => new Element(tag, options);
  try {
    const source = await readFile(new URL('../src/acode/vendor/components/scrollbar/index.js', import.meta.url), 'utf8');
    const testSource = source
      .replace('import "./style.scss";', '')
      .replace('import tag from "html-tag-js";', 'const tag = globalThis.__scrollbarTag;');
    const { default: ScrollBar } = await import(`data:text/javascript;base64,${Buffer.from(testSource).toString('base64')}`);
    const parent = new Element('div');
    const bar = ScrollBar({ parent, placement: 'right' });
    bar.render();
    assert.equal(bar.isConnected, true);
    assert.equal(bar.visible, true);

    // Native/DOM layout can detach the bar without calling its hide() method.
    bar.remove();
    assert.equal(bar.visible, true);
    bar.render();
    assert.equal(bar.isConnected, true, 'render must reattach a detached bar');

    bar.hide(); // Starts a delayed remove.
    bar.render(); // A new user scroll must cancel that removal.
    await new Promise((resolve) => setTimeout(resolve, 350));
    assert.equal(bar.isConnected, true);
    assert.equal(bar.visible, true);
    bar.destroy();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  }
});

test('Android tab activation requests CodeMirror remeasurement', async () => {
  const lifecycle = await readFile(new URL('../../app/src/main/java/dev/pocket/notebook/web/EditorLifecycleEffects.kt', import.meta.url), 'utf8');
  const runtime = await readFile(new URL('../src/acode/vendor/lib/singlePaneRuntime.js', import.meta.url), 'utf8');
  assert.match(lifecycle, /LaunchedEffect\(web, ready, active\)/);
  assert.match(lifecycle, /postOnAnimation[\s\S]*EditorApi\?\.refresh/);
  assert.match(runtime, /touchmove[\s\S]*onScrollInteraction/);
  assert.match(runtime, /scrollbarOverflow\(metrics/);
});
