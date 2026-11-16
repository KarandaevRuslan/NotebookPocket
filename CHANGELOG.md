# Changelog

## Unreleased — Nexus integration and repository launch

- Refresh launcher artwork and GitHub repository documentation, contributor templates and project attribution.
- Expand the built-in Pancakes example into an interactive Nexus tutorial (Store, Dispatcher, Handler and Observable).
- Bundle Nexus 0.1.20 Core locally alongside Observable Notebook Kit; expose `Nexus` and a per-preview `nexus` host in notebook cells.
- Add cell/preview-owned scopes and a Store-to-Observable generator bridge, plus editor completions and an importable counter example.
- Pin the local dependency and preserve its license/provenance; require Node.js 22.16+ to match Nexus.
- Cover transactions, invalidation, cancellation, isolation and the minified production preview with integration checks.

## 0.9.3 — prepared, unreleased

- Preserve incomplete edits in encrypted, atomic device-local checkpoints, independent of portable ZIP validation.
- Load failures remain visible; saves have explicit durable outcomes and retryable status.
- Coalesce and serialize workspace writes off the main thread; flush before background key clearing.
- Separate preview origins by document and from the editor; disable DOM storage for protected previews.
- Treat post-commit file cleanup as retryable debt and recover stale orphan/temporary revisions on startup.
- Add draft, IO-fault, recovery, writer-ordering, WebView isolation and process-restart regressions.
- Fix Windows URL/path handling in the web tests and Acode JSX transformer.
- Update DOMPurify and development tooling to resolve the dependency advisories present in the baseline lockfile.
- Prepare English project documentation, repository hygiene, pinned CI actions and signed draft-release automation.

## 0.9.2

- Previous application version and architecture refactoring baseline.
