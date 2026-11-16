import test from 'node:test';
import assert from 'node:assert/strict';
import { notebookStateKey, migrateLegacyNotebookState } from '../src/preview-state.js';

function storage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
  };
}

test('0.9.2 form state is migrated only to the active notebook/version', () => {
  const state = storage();
  const html = '<notebook data-state-version="a2">hello</notebook>';
  assert.equal(notebookStateKey('doc', html), 'notebook-pocket:state:doc:a2');
  assert.equal(migrateLegacyNotebookState(state, 'doc', html, '{"c:0":{"value":"5"}}'), true);
  assert.equal(state.getItem(notebookStateKey('doc', html)), '{"c:0":{"value":"5"}}');
  assert.equal(state.getItem(notebookStateKey('other', html)), null);
  assert.equal(state.getItem(notebookStateKey('doc', '<notebook data-state-version="v3">')), null);
  assert.equal(migrateLegacyNotebookState(state, 'doc', html, null, '{"clock":{"running":false}}'), true);
  assert.equal(state.getItem('notebook-pocket:timers:notebook-pocket:state:doc:a2'), '{"clock":{"running":false}}');
});

test('newer per-origin state always wins; malformed and oversized data are rejected', () => {
  const state = storage();
  assert.equal(migrateLegacyNotebookState(state, 'id', '', '{}'), true);
  assert.equal(migrateLegacyNotebookState(state, 'id', '', '{"c:0":{"value":"legacy"}}'), false);
  assert.equal(state.getItem('notebook-pocket:state:id'), '{}');
  state.removeItem('notebook-pocket:state:id');
  for (const bad of ['null', '[]', '', 'not json', '"string"', 'x'.repeat(65537)]) {
    assert.equal(migrateLegacyNotebookState(state, 'id', '', bad), false);
  }
  assert.equal(state.getItem('notebook-pocket:state:id'), null);
});
