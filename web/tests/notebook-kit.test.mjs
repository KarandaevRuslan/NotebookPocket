import {test} from 'node:test';
import assert from 'node:assert/strict';
import {toCell, toNotebook, transpile} from '@observablehq/notebook-kit';

test('Observable Notebook Kit creates a real dependency graph definition', () => {
  const cell = toCell({id: 3, mode: 'js', value: 'display(people * 80);'});
  const compiled = transpile(cell);
  assert.ok(compiled.inputs?.includes('people'));
  assert.ok(compiled.inputs?.includes('display'));
  assert.match(compiled.body, /people \* 80/);
});

test('top-level JS declarations become named notebook outputs', () => {
  const notebook = toNotebook({title: 'Example', cells: [{id: 1, value: 'const people = 3;'}]});
  assert.equal(notebook.cells[0].mode, 'js');
  assert.ok(transpile(notebook.cells[0]).outputs?.includes('people'));
});
