# Security

## Reporting

For a vulnerability, use the repository's private vulnerability-reporting feature if enabled. Maintainers should enable it before making the repository public. Do not include real passwords, signing keys or private notebook contents in a public issue. Ordinary build failures and reproducible non-sensitive bugs may be reported with the issue template.

## Data and execution boundaries

- The application works offline and does not request Android Internet access.
- Only the trusted editor has a native JavaScript bridge.
- Previews use per-document origins; protected previews disable DOM storage.
- Portable password keys are session-only. Local checkpoints use a separate Android Keystore AES-GCM key and are excluded from backup/export.
- Incomplete source is preserved as a checkpoint, while export/copy is blocked until the pending draft is resolved.
- Archive paths, sizes, file counts and authenticated contents are validated before use.

Notebook cells intentionally execute JavaScript. They can inspect their own document/resources, manipulate their preview and consume renderer resources. Treat imported notebooks as executable content; this is not a general-purpose hostile-code sandbox.

## Release checks

Dependency scanning runs in CI. Signed releases require separate signing secrets and the protected `release` environment. Do not publish a release with failed recovery/isolation checks, unresolved high/critical dependency advisories, or an incomplete manual device checklist. Security-related behavior changes are documented in `CHANGELOG.md` and `docs/AUDIT.md`.
