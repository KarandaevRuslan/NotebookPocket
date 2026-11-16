import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';

const bootSource = await readFile(new URL('../public/editor-boot.js', import.meta.url), 'utf8');

function startBoot() {
  const handlers = new Map();
  const failures = [];
  const notices = [];
  const window = {
    NativeEditor: {failed: reason => failures.push(reason)},
    addEventListener(name, handler) {
      handlers.set(name, [...(handlers.get(name) || []), handler]);
    }
  };
  const document = {addEventListener() {}};
  const console = {
    info() {}, debug: (...args) => notices.push(args.join(' ')),
    error() {}
  };
  runInNewContext(bootSource, {window, document, console});
  return {handlers, failures, notices};
}

test('ResizeObserver delivery notice does not shut down an otherwise working editor', () => {
  const {handlers, failures, notices} = startBoot();
  let prevented = false;
  handlers.get('error')[0]({
    message: 'ResizeObserver loop completed with undelivered notifications.',
    preventDefault() {prevented = true;}
  });
  assert.equal(failures.length, 0);
  assert.equal(prevented, true);
  assert.equal(notices.length, 1);
});

test('real JavaScript startup errors still reach Android error reporting', () => {
  const {handlers, failures} = startBoot();
  handlers.get('error')[0]({message: 'Uncaught ReferenceError: badVar is not defined'});
  assert.equal(failures.length, 1);
  assert.match(failures[0], /ReferenceError/);
});

