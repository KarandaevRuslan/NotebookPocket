import {test} from 'node:test';
import assert from 'node:assert/strict';
import {memoryStorage, previewStorage} from '../src/preview/storage.js';

test('a protected WebView with DOM storage disabled still boots with isolated memory state', () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', {configurable:true, get() {throw new Error('SecurityError');}});
  try {
    const first = previewStorage(), second = previewStorage();
    first.setItem('private-input', 'private value');
    assert.equal(first.getItem('private-input'), 'private value');
    assert.equal(second.getItem('private-input'), null);
  } finally {
    if (descriptor) Object.defineProperty(globalThis, 'localStorage', descriptor);
    else delete globalThis.localStorage;
  }
});
test('session state supports the same reset contract as browser storage', () => {
  const storage = memoryStorage();storage.setItem('step', 5);assert.equal(storage.getItem('step'),'5');
  storage.removeItem('step');assert.equal(storage.getItem('step'),null);
});
