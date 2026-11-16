import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');
const vm = read('app/src/main/java/dev/pocket/notebook/feature/workspace/state/WorkspaceViewModel.kt');
const nav = read('app/src/main/java/dev/pocket/notebook/app/navigation/TopLevelDestination.kt');
const toolbar = read('app/src/main/java/dev/pocket/notebook/feature/workspace/components/WorkspaceToolbar.kt');
const route = read('app/src/main/java/dev/pocket/notebook/feature/workspace/NotebookDetailScreen.kt');
assert.match(vm, /savedState\["mode"\]\s*\?:\s*1/);
assert.match(toolbar, /WorkspaceModePicker\(/);
const modePicker = read('app/src/main/java/dev/pocket/notebook/feature/workspace/components/WorkspaceModePicker.kt');
assert.match(modePicker, /if \(compact\) strings\.previewCompact else strings\.preview/);
assert.match(modePicker, /Modifier\.fillMaxWidth\(\)/);
assert.match(nav, /TopLevelDestination\(Routes\.WORKSPACE,\s*strings\.workspace/);
assert.match(route, /ResetPreviewDialog\(/);
for (const key of ['workspace','reset_preview','reset_preview_title','reset_preview_description','reset_preview_failed']) {
  assert.ok(read('app/src/main/res/values/strings.xml').includes(`name="${key}"`));
  assert.ok(read('app/src/main/res/values-ru/strings.xml').includes(`name="${key}"`));
}
console.log('Workspace UX: preview first, truthful destination, reset confirmation, locales OK');
