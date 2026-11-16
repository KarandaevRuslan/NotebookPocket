import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';

const root = new URL('../../app/src/main/assets/demo/pancakes/', import.meta.url);
const read = path => readFile(new URL(path, root));
const source = (await read('notebook.html')).toString();

test('Nexus Pancakes uses five unique cells and valid JS syntax', () => {
  const cells = [...source.matchAll(/<script\s+id="(\d+)"\s+type="([^"]+)">([\s\S]*?)<\/script>/g)];
  const ids = cells.map(match => Number(match[1]));
  assert.equal(cells.length, 22);
  assert.equal(new Set(ids).size, 22);
  assert.deepEqual([...ids].sort((a,b)=>a-b), Array.from({length:22},(_,i)=>i+1));
  for (const [, id, type, body] of cells) {
    if (type !== 'module') continue;
    const result = spawnSync(process.execPath, ['--check', '--input-type=module'], {
      input: body, encoding: 'utf8',
    });
    assert.equal(result.status, 0, `Cell ${id}: ${result.stderr}`);
  }
  assert.match(source, /nexus\.scope\(invalidation\)/);
  assert.match(source, /class PancakeServings extends Nexus\.Handler/);
  assert.match(source, /nexus\.dispatcher\.inline\(/);
  assert.match(source, /pancakesReady = nexus\.observe\(pancakeScope, pancakeDoneStore\)/);
  assert.match(source, /servingsReady = nexus\.observe\(pancakeScope, pancakeServingsStore\)/);
  assert.match(source, /pancakeScope\.cleanup\(/);
});

test('English and Russian localize all new Nexus lesson strings', async () => {
  const en = JSON.parse(await read('i18n/en.json'));
  const ru = JSON.parse(await read('i18n/ru.json'));
  assert.deepEqual(Object.keys(en).sort(), Object.keys(ru).sort());
  for (const key of Object.keys(en).filter(key => key.startsWith('nexus'))) {
    assert.ok(en[key].length > 0 && ru[key].length > 0, key);
    assert.match(source, new RegExp(`L\\.${key}\\b`));
  }
});

test('v6 migration snapshot hashes match the original resource set', async () => {
  const hashes = JSON.parse(await read('upgrade/v6-resources.json'));
  assert.equal(Object.keys(hashes).length, 5);
  for (const [file, oldHash] of Object.entries(hashes)) {
    const currentHash = createHash('sha256').update(await read(file)).digest('hex');
    if (file === 'images/pancakes.png' || file === 'images/preparation.png') {
      assert.equal(currentHash, oldHash, 'unchanged images');
    } else {
      assert.notEqual(currentHash, oldHash, 'new text or styles');
    }
  }
  const v6Source = (await read('upgrade/v6-source.html')).toString();
  assert.doesNotMatch(v6Source, /pancakeScope = nexus\.scope/);
  assert.match(source, /data-state-version="pancakes-v5"/, 'form persistence key stays stable');
});
