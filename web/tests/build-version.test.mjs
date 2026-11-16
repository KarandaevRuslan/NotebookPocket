import test from 'node:test';
import assert from 'node:assert/strict';
import {cp, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const projectRoot = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const packageVersion = JSON.parse(await readFile(join(projectRoot, 'web', 'package.json'), 'utf8')).version;

test('Android Gradle check and web/package.json agree on the build version', async () => {
  const buildGradle = await readFile(join(projectRoot, 'app', 'build.gradle.kts'), 'utf8');
  assert.match(buildGradle, /JsonSlurper\(\)\.parse\(rootProject\.file\("web\/package\.json"\)\)/);
  assert.match(buildGradle, /versionName\s*=\s*webVersion/);
  assert.match(buildGradle, /build-version\.txt"\)\.readText\(\)\.trim\(\)\s*==\s*webVersion/);

});

test('copy-android.mjs writes the web package version (not a hotfix label)', async () => {
  const tmp = await mkdtemp(join(tmpdir(), 'notebook-web-version-'));
  const web = join(tmp, 'web');
  try {
    await mkdir(join(web, 'dist', 'assets'), {recursive: true});
    await mkdir(join(web, 'dist', '.vite'), {recursive: true});
    await cp(join(projectRoot, 'web', 'copy-android.mjs'), join(web, 'copy-android.mjs'));
    await cp(join(projectRoot, 'web', 'package.json'), join(web, 'package.json'));
    for (const mode of ['editor', 'preview']) {
      await writeFile(join(web, 'dist', `${mode}.html`), `<html>${mode}</html>`);
      await writeFile(join(web, 'dist', 'assets', `${mode}.js`), 'export {}');
      await writeFile(join(web, 'dist', '.vite', `manifest-${mode}.json`),
        JSON.stringify({[mode]: {file: `assets/${mode}.js`, isEntry: true}}));
    }
    const result = spawnSync(process.execPath, [join(web, 'copy-android.mjs')], {
      cwd: web, encoding: 'utf8', timeout: 10_000
    });
    assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
    const stamp = await readFile(join(tmp, 'app', 'src', 'main', 'assets', 'web', 'build-version.txt'), 'utf8');
    assert.equal(stamp.trim(), packageVersion);
  } finally {
    await rm(tmp, {recursive: true, force: true});
  }
});
