import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {notebookStateKey, clearNotebookState} from '../src/preview-state.js';

function fakeStorage() {
  const records = new Map();
  return {
    setItem: (key, value) => records.set(key, String(value)),
    getItem: key => records.get(key) ?? null,
    removeItem: key => records.delete(key)
  };
}

const recipe = `<notebook data-state-version="pancakes-v1"><title>Recipe</title></notebook>`;

test('Reset clears only the active notebook state, including step progress', () => {
  const storage = fakeStorage();
  const activeKey = notebookStateKey('recipe-1', recipe);
  const otherKey = notebookStateKey('recipe-2', recipe);
  storage.setItem(activeKey, JSON.stringify({'4:0': {value: '3'}, '11:step': {value: 5}}));
  storage.setItem(otherKey, JSON.stringify({'11:step': {value: 2}}));
  assert.equal(clearNotebookState(storage, 'recipe-1', recipe), activeKey);
  assert.equal(storage.getItem(activeKey), null);
  assert.notEqual(storage.getItem(otherKey), null);
});

test('Versioned and unversioned state keys are independent', () => {
  const storage = fakeStorage();
  const oldSource = '<notebook><title>Older</title></notebook>';
  const oldKey = notebookStateKey('recipe-1', oldSource);
  const updatedKey = notebookStateKey('recipe-1', recipe);
  assert.notEqual(oldKey, updatedKey);
  storage.setItem(oldKey, 'old');
  storage.setItem(updatedKey, 'new');
  clearNotebookState(storage, 'recipe-1', recipe);
  assert.equal(storage.getItem(oldKey), 'old');
  assert.equal(storage.getItem(updatedKey), null);
});

test('Preview exposes the reset API and rebuilds Observable cells', () => {
  const code = readFileSync(new URL('../src/preview/application.js', import.meta.url), 'utf8');
  assert.match(code, /return\s*\{\s*render,/);
  assert.match(code, /migrateLegacyState\(documentId, source, formRaw, timersRaw\)/);
  assert.match(code, /setPreferences,\s*resetState,/);
  assert.match(code, /clearNotebookState\(storage, renderedDocumentId, renderedSource\)/);
  assert.match(code, /render\(renderedSource, renderedDocumentId\)/);
});
