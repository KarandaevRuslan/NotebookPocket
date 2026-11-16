# NotebookPocket 0.9.3 — attachment + save/delete patch

This is a **complete source tree** based on `NotebookPocket-0.9.3-complete-source(1).zip`, not a prebuilt APK. The adjacent `audit.md` (in Russian) lists the changes, risks, and outstanding checks.

## Changes

- Fix strict `FileAttachment` base validation for isolated `preview-<32-hex>.notebook.invalid` origins.
- Serialize individual and bulk tree deletions with in-flight document writes; forget pending plaintext only after successful deletion; prevent stale editor events from recreating records. Include notebook IDs in locked subtrees.
- Refresh the active workspace title when a committed source revision changes metadata.
- Best-effort one-time migration of 0.9.2 **unprotected** per-document form/step/timer state; never expose the old editor origin to untrusted notebook JavaScript. Skip protected documents.
- Make urgent flush interrupt an active debounce; request an immediate flush on app pause and workspace exit; use a flush barrier in the fallback export callback.
- Add JVM, Web/Node and Android instrumentation regression test cases.

## Build and test on a machine with Android SDK and internet/npm dependencies

```sh
cd NotebookPocket/web
npm ci
npm test
npm run build
cd ..
./gradlew testDebugUnitTest lintDebug assembleDebug
# When an emulator/device is available:
./gradlew connectedDebugAndroidTest
```

Test upgrading an installed and signed 0.9.2 app to this build before release. Keep the same signing key, and increase `versionCode` for a distribution release as required by the store (this source patch preserves 0.9.3's version settings).

## Not included

Moving the internal editable storage away from whole-ZIP rewrites, and zero-loss guarantees during force-stop before any IO checkpoint, require separate design work. Do not claim those issues are fixed by this patch.

## UI follow-up hotfix (2026-11-16)

- Resource text/image viewers now use edge-to-edge full-screen `Dialog`s with an opaque surface, safe system insets, and IME padding in the dialog window. This keeps the Acode QuickTools toolbar above the keyboard and removes the landscape activity-rail gap.
- First Code entry and return from Preview trigger a CodeMirror remeasurement after two animation frames. Scrollbar teardown/re-attachment is race-safe, zero-sized parked WebViews no longer invalidate the scrollbars, and touch/wheel interactions refresh the overlays.
- Notebook Kit 2.x / Observable Inputs styling moved into `web/src/preview/inputs-theme.css`, which is imported by every Preview entrypoint, not supplied by the Pancakes example. The Vite asset verifier now checks that this stylesheet reached the emitted Preview CSS. Existing per-notebook CSS remains allowed.
- Added Node tests for scrollbar visibility/reattachment and globally included preview styles, plus a source-contract test for resource Dialog insets. Updated a stale reset-preview API test that predated legacy state migration.
- The Acode scrollbar adaptation is recorded in `third_party/acode/full-editor-manifest.json` and in a standalone upstream diff. The upstream manager's protected function bodies remain unchanged.

**Important:** This is a source-only tree. It includes neither prebuilt web assets nor an APK. Regenerate both Vite bundles using `cd web && npm ci && npm run build` before Gradle. Test actual keyboard animations and both landscape rotations on Android; unit/source tests do not prove device UI correctness.

## Web asset verifier fix (2026-11-16)

- Fixed `npm run build` aborting after Vite successfully compiled Preview with
  `AssertionError: Preview must emit local CSS for Notebook Kit Inputs`.
  With `cssCodeSplit: false`, Vite writes `style.css` as a standalone manifest
  record and links it directly from `preview.html`; the Preview JS entry has
  no `css` array. The verifier now checks CSS files **actually linked** from
  Preview HTML and cross-checks the manifest, then scans their contents for
  the global Inputs theme. The CSS source/Preview runtime is unchanged.
- Added regression tests for the actual Vite manifest layout and missing CSS
  link. This is a build-tooling false-negative fix, not a UI runtime change.
