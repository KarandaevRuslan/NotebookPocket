import {cp, mkdir, rm, writeFile, access, readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const out = new URL('../app/src/main/assets/web/', import.meta.url);
await rm(out, {recursive: true, force: true});
await mkdir(out, {recursive: true});
await cp(new URL('./dist/', import.meta.url), out, {recursive: true});
// app/build.gradle.kts verifies the web bundle against the package version.
// Version is defined in package.json and checked by the Android Gradle task.
const {version} = JSON.parse(await readFile(new URL('./package.json', import.meta.url), 'utf8'));
if (!version || typeof version !== 'string') throw new Error('Missing web/package.json version');
await writeFile(new URL('../app/src/main/assets/web/build-version.txt', import.meta.url), `${version}\n`);
console.log(`Copied Vite build to ${fileURLToPath(out)}`);

// Validate that both independent bundles (and their CSS) made it into the APK.
for (const mode of ['editor', 'preview']) {
  const manifest = JSON.parse(await readFile(new URL(`./dist/.vite/manifest-${mode}.json`, import.meta.url), 'utf8'));
  for (const chunk of Object.values(manifest)) {
    for (const asset of [chunk.file, ...(chunk.css ?? []), ...(chunk.assets ?? [])]) {
      if (asset) await access(new URL(asset, out));
    }
  }
}
console.log('Verified both editor and preview bundles in Android assets.');
