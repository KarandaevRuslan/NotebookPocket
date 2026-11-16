# Nexus 0.1.20 integration — historical validation record

**Date:** 16 November 2026. **Environment used for this report:** Linux, Node.js 24.22.1. The project's minimum Node.js version was raised to **22.16.0** for the bundled Nexus package.

> **Historical snapshot.** The numbers below document the integration snapshot in which they were recorded. The repository and CI tests have changed since then. Do **not** present these counts as the current main-branch or release status; consult the latest GitHub Actions run for that.

| Check | Recorded outcome |
| --- | --- |
| `npm ci --ignore-scripts --no-audit --no-fund` | Passed: Nexus installed from its local tarball; other dependency versions were retained. |
| `npm run build` | Passed: editor and preview bundles, local-asset audit and copy to Android assets. |
| `npm run test:built-preview` | Passed: the actual minified preview executed the example, updated a Nexus value and reset its state. |
| `node --test tests/nexus-runtime.test.mjs` | 7/7 passed. |
| `npm test` | 101 tests: 98 passed, 3 pre-existing failures (listed below). |
| `npm run test:upstream` | 45/45 passed. |
| `python3 scripts/check_repository.py` | Passed. |
| `python3 -m unittest discover -s tests -p '*Test.py'` | 22/22 passed. |
| `node tests/ResourceLocalizationTest.mjs` | Passed. |
| `node tests/WorkspaceUXTest.mjs` | Passed. |
| APK build / Android unit, lint and device tests | Not run in this validation environment; Android SDK 35 / JDK 17 were not configured. |

The new checks exercised the real Nexus package and Observable runtime under JSDOM, including Store initial/committed values, transaction rollback, queued-command cancellation, either subscription cleanup order, preview isolation, cleanup error handling, input-triggered recomputation, document reset and teardown, protected-preview behavior and editor completions.

## Failures observed before the Nexus integration

The following three tests were also run against the extracted **pre-integration** `NotebookPocket(6).zip` source with the same dependencies. All three failures were reproduced there, so this report did not classify them as newly introduced by Nexus:

| File / test | Observation at that time |
| --- | --- |
| `web/tests/acode-host-runtime.test.mjs` — `original scrollbar drag changes scroll without changing a long selection` | JSDOM did not find the scrollbar; `assert.ok(bar)` failed. |
| `web/tests/pancake-demo.test.mjs` — `official Observable step buttons use modern grouped styles and remain interactive` | The assertion expected `display: flex`, but received `block`. |
| `web/tests/protected-state.test.mjs` — `native Preview passes the protection flag into the isolated renderer` | An old regex expected `$payload.source`; the Kotlin source already passed `p.source` after assigning `var p = $payload`. |

These tests and modules were not modified as part of the initial Nexus integration. This record did **not** claim that the entire JavaScript suite was green or that an APK had been validated. Subsequent CI fixes must be evaluated against their own runs.

## Package provenance

Nexus 0.1.20 was compiled from a provided source archive and installed via `web/vendor/nexus-0.1.20.tgz`. The full-build limitation of that archive and the local package's provenance are described in [`third_party/nexus/README.md`](../third_party/nexus/README.md). Only `nexus/core` was imported into the browser preview. No Frida adapter, network permissions, CSP weakening or native preview bridge was added.

The source distribution contained the project files and vendored Nexus tarball, but not `node_modules`, generated bundles, local settings, report logs or APK artifacts. For present-day build instructions, consult [BUILDING.md](BUILDING.md) and the [Nexus integration guide](NEXUS.md).
