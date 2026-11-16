# Audit remediation record

Baseline: the supplied audit dated **16 November 2026**, against version 0.9.2. Prepared changes target **0.9.3 / versionCode 41**, with Room schema v2 unchanged.

This record distinguishes implemented fixes, locally executed checks and checks that still require an Android device or a GitHub runner. It is not a claim that a production release has been published or certified.

| Finding | Implementation | Regression evidence |
| --- | --- | --- |
| P0: incomplete source could exist only in memory | `DraftStore` writes encrypted atomic checkpoints before canonical persistence; incomplete metadata/root markup returns `SaveOutcome.DRAFT`; `DocumentSnapshot` restores it after repository/process recreation | `DurableDraftTest`, `DraftProcessDeathTest` |
| P0: save/read errors were hidden | Read failures render a retryable error rather than an empty document; asynchronous acknowledgements distinguish Saving/Saved/Draft/Failed; retry is explicit | `DurableDraftTest` storage faults and missing-reference case; `DocumentWriteCoordinatorTest` |
| P1: every edit blocked the UI on synchronous ZIP/Room work | Process-owned coalescing writer serializes IO off main; survives routes/Activity recreation; background key clearing follows flush and a foreground epoch guard | Large rapid-edit/coalescing and sequential-write tests; physical typing/IME performance remains a device gate |
| P1: preview could share editor DOM storage | A distinct origin for every document, separate from the editor; protected DOM storage disabled and the old preview origin cleared; resource capability also checks origin | `PreviewIsolationTest`, `preview-storage.test.mjs`, `PreviewOriginInstrumentedTest` |
| P2: cleanup failure after commit looked like a failed operation | Post-commit deletion is best-effort; failure leaves an orphan for subsequent recovery and does not alter the commit acknowledgement | `RevisionFailureTest` |
| P2: no revision recovery | Startup inventory compares Room references with private files; removes stale orphan/temp files and retries cleanup debt; missing live references remain visible errors; deleted-document drafts are reclaimed | `StorageRecoveryTest`, missing-live-revision and failed-write draft cases |
| P3: test cache and reports shipped as source | Ignore rules and repository checks reject caches, generated web assets, reports, private keys/installers and unreviewed images; the source archive excludes historical verification directories and test cache | `RepositoryHygieneTest`, patch-content validation |

The ZIP/collection representation is retained for compatibility. Coalescing reduces redundant repacks and keeps them off main, but attachments are not migrated to a new independent internal store. That optimization needs its own performance measurements and migration design.

## Additional release work

- English README, architecture, license, contribution/security and release documentation.
- Explicit image allowlist for the two existing runtime demo images; no new screenshots/artwork added.
- Linux/Windows web CI, Android lint/unit/build, device regression workflow and checksum/signature-aware draft-release workflow.
- Verified action revisions pinned by SHA; signing secrets are available only in the release job/environment.
- Dependency updates with a refreshed lockfile. The local npm audit after updating reported **0 known advisories**; this is a point-in-time scan, not a permanent guarantee.
- Windows path/JSX fixes are included.

## Locally executed

- 64 Android/Robolectric tests: passed, no skipped tests.
- 81 Node/web tests and 45 Vitest/Acode tests: passed.
- 19 Python structural/hygiene checks: passed.
- Android lint: 0 errors, 33 warnings, 2 informational findings.
- Debug application APK and instrumentation APK: assembled.
- Vite build, offline asset audit, CodeMirror deduplication and Acode provenance checks: passed.
- Workflow syntax/input checks: actionlint passed.
- Android API 28 software emulator: 9 instrumentation tests passed. The separate checkpoint and recovery stages also passed, with `am force-stop` between them. These exercised the production Android Keystore and real WebView storage isolation.

Final source archive validation includes these outcomes and the result of exercising release signing with a disposable test key. Such a key/APK is not a distributable release identity or included artifact.

## Remaining release gates

Local device regressions ran on an Android API 28 software emulator. Run the GitHub Android API 35 device workflow before publishing, and complete the physical-device checks below; the local emulator does not establish compatibility or typing performance on every Android/WebView version.

The manual checklist in [RELEASE.md](RELEASE.md) remains required for keyboard/Cut/Back, orientation, long-document responsiveness, real migration and a signed upgrade over the previous app. No GitHub repository was created, no push/tag was made and no release was published as part of this preparation.
