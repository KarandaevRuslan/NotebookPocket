# NotebookPocket architecture

> Android / Kotlin / Compose + embedded Acode/CodeMirror editor + Observable Notebook Kit 2.x preview. This document describes the code in the repository, not a planned rewrite.

## 1. System at a glance

```text
                         ANDROID APPLICATION (:app)
  ┌──────────────────────────────────────────────────────────────────┐
  │ Compose routes / ViewModels         State & application services │
  │ Library | Workspace | Resources  →  NotebookAppState            │
  │                                   →  DocumentWriteCoordinator   │
  │                                   →  NotebookGateway            │
  └───────────────────────┬──────────────────────────┬───────────────┘
                          │                          │
                    Editor WebView              Repository services
                   Acode + CodeMirror           / domain ports
                          │                          │
                     EditorBridge             Room metadata + VFS
                          │                  Atomic encrypted drafts
             Preview WebView (separate)        Revisioned file store
             Observable Notebook Kit          ZIP package/import/export
             Nexus 0.1.20 bridge              Password/session security
                          │                          │
                   Scoped attachments ←── resource access contract
```

### Dependencies

- `domain/` defines interfaces and data contracts; `data/` implements persistent stores and operations.
- `feature/` and `app/` contain screen state, routing and UI composition.
- `NotebookRepository` is a compatibility facade; `RepositoryServices` composes narrower implementations.
- `web/` is a Vite project embedded as local assets inside Android APK; there is no runtime Node.js requirement.

## 2. Editor and preview are different trust zones

**Editor:** Acode editor components adapted to the app and CodeMirror 6 run inside a trusted editor WebView. `EditorBridge` translates editor messages and commands to Android state. UI-specific lifecycle and keyboard handling are Android concerns.

**Preview:** JavaScript inside notebook cells is intentionally executable. It runs in a **separate WebView** without the editor's native JS bridge. Preview resources are served through a scoped Android resource loader. Preview origins are document-specific, isolated from the editor origin. Password-protected previews disable DOM storage; requests outside the supported offline resource boundary are restricted.

This reduces cross-document state sharing but **is not a general arbitrary-code sandbox**: code can still hang its renderer, consume memory and interact with its notebook DOM. The Android app does not request `INTERNET` permission. Do not imply that an imported notebook is harmless simply because it is offline.

## 3. Save pipeline and error states

```text
CodeMirror change
  → WorkspaceViewModel (current editable text + state)
  → DocumentWriteCoordinator (debounce, ordering, IO worker)
  → encrypted AtomicFile checkpoint [before canonical write]
  → document package validation + revisioned file writer
  → Room reference/metadata update
  → acknowledged result: Saved / Draft saved / Failed
```

- The editor must not treat a callback as an acknowledgement of durable persistence.
- Incomplete notebook markup can be stored in a **device-local encrypted draft** without replacing the last valid portable package.
- Checkpoints are encrypted with Android Keystore AES-GCM, stored under `noBackupFilesDir` and not exported. Portable password encryption is separate.
- A successful canonical save replaces the reference to the active file revision. A background recovery pass detects and cleans safe orphan/temporary files. Room + filesystem are not one atomic transaction.
- File and library operations coordinate with pending writes; tests cover ordering, failures and process-restoration conditions.

## 4. Document model and resources

- Room stores catalog structure, order, metadata and references. Document HTML and attachments live in file-backed packages. Archives are also used for import/export and shared resources in collections.
- `DocumentFileStore`, `Revision`, `StorageRecovery` and the package utilities provide disk durability and recovery.
- Resource ownership is either **notebook-specific** or **shared within a collection**. Preview accesses a narrowed resource provider rather than the whole repository.
- Package rewrites can still be expensive for large embedded assets; granular internal storage/caching remains a potential performance improvement.

## 5. Notebook runtime integration

- `web/src/preview/notebookRuntime.js` initializes the Observable Notebook Kit compiler/runtime and binds notebook cells to the document-specific view.
- `web/src/preview/nexusRuntime.js` exposes **`Nexus`** (the bundled core API) and **`nexus`** (a preview lifecycle-aware host) to cells. Scope lifetime is associated with preview/cell invalidation; `nexus.observe` bridges Nexus stores into Observable reactivity.
- `web/src/preview/inputs-theme.css` provides preview-wide styles for Observable Inputs; it is not tied to the built-in sample.
- `web/src/attachment-resolver.js` validates scoped attachment paths/domains before a notebook resolves its resources.
- `web/vendor/nexus-0.1.20.tgz` is a **local npm dependency** (`file:vendor/...`), installed by `npm ci`, then bundled by Vite. Its source/package provenance is documented in `third_party/nexus`.

The built-in **Pancakes / Блины** demo and `examples/nexus-counter.html` exercise these APIs. See [docs/NEXUS.md](docs/NEXUS.md).

## 6. Build and CI

```text
npm ci → verify CodeMirror/Acode provenance → Vite editor+preview builds
       → check built assets → copy into app/src/main/assets/web/
       → Gradle verifyWebAssets → Kotlin/Compose APK
```

The generated WebView bundle is deliberately ignored by Git; it must be rebuilt before an Android build. CI tests JavaScript and Kotlin, checks license/source hygiene and prepares artifacts. A separate emulator workflow covers real WebView/Android process interactions. The release workflow signs an APK only when environment secrets are configured, then creates a **draft** GitHub Release.

### Primary source directories

| Concern | Source |
| --- | --- |
| Navigation, screens | `app/src/main/java/dev/pocket/notebook/app`, `feature` |
| Domain contracts | `app/src/main/java/dev/pocket/notebook/domain` |
| Storage, drafts, encryption | `app/src/main/java/dev/pocket/notebook/data` |
| WebView integration | `app/src/main/java/dev/pocket/notebook/web` |
| Acode editor | `web/src/editor`, `web/src/acode` |
| Observable & Nexus runtime | `web/src/preview`, `web/vendor` |
| Tests | `app/src/test`, `app/src/androidTest`, `web/tests`, `tests` |

## 7. Release & security requirements

Do not publish a binary solely because unit tests passed. Before tagging, verify: IME and insets in landscape, first-open editor scrollbars, offline attachment resolution, Nexus invalidation, encrypted-draft recovery after force-stop, lock/unlock behavior, import/export and signed-upgrade compatibility. See [docs/RELEASE.md](docs/RELEASE.md) and [SECURITY.md](SECURITY.md).

## 8. Attribution

The editor was adapted from **Acode** (not a wholly original editor implementation); the notebook execution layer builds on **Observable Notebook Kit 2.x**. Third-party files remain governed by their upstream licenses and are tracked in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
