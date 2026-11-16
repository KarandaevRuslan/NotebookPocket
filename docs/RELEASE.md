# Publishing a release

See also [initial GitHub publication](PUBLISHING.md) and [branding](BRANDING.md).

## Repository setup

Create the GitHub repository under your account (suggested name: `NotebookPocket`). Review staged files before the first push. Keep `main` protected with CI checks and pull-request review. Enable private vulnerability reporting and Dependabot alerts.

Create a GitHub Actions environment named **release** and configure required reviewers. Add these environment secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the release keystore, without line wrapping |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Keep a separate, secure backup of the keystore. Existing installed apps need the same signing identity to receive updates. Never commit keys or passwords. Without signing environment variables, a local `assembleRelease` produces an unsigned package; it is not the signed distribution artifact.

## Versioning

`web/package.json` is the semantic-version source. Android `versionName` and the generated web stamp use it. Update the lockfile root version as well, increment Android `versionCode`, and add release notes in `CHANGELOG.md`. The prepared next version is 0.9.3 / versionCode 41. Room remains schema v2.

`scripts/check_release.py v0.9.3` checks tag/version agreement. Do not rewrite published tags.

## Required validation

- [ ] CI succeeds on Linux and Windows.
- [ ] Android device regressions pass, including process force-stop recovery and WebView origin/storage isolation.
- [ ] Incomplete title/root text survives closing and reopening the app after a durable-save indication.
- [ ] Failed-write state and retry are understandable; a failed read does not open an empty editor.
- [ ] Long documents remain responsive while typing; Saving becomes Saved/Draft saved after IO.
- [ ] Source/preview/resource switching, orientation, IME, Cut and Back work on the target device/WebView version.
- [ ] Password lock/unlock, protected collection attachments, import/export and existing-library migration work.
- [ ] A signed upgrade over the previous signed APK preserves data.
- [ ] Dependency scan has no unresolved high/critical advisories and staged files contain no private/generated material.

The checklist is intentionally not pre-checked by generated reports. Local Robolectric tests are not proof of physical-device behavior.

## Draft release workflow

After validation, create and push the matching annotated version tag. The `Draft signed release` workflow runs CI and device regressions, waits for the `release` environment, builds the signed APK and creates a **draft** GitHub release with SHA-256 checksums. It does not publish the draft.

You may rerun a tag workflow with `gh workflow run release.yml --ref v0.9.3`. A branch dispatch does not create a release. Review notes, signature, checksums and installation before manually publishing the draft in GitHub.
