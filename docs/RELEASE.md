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

## What happens when you push a tag

For a tag of the form `vX.Y.Z`, the `APK on tag / signed release draft` GitHub Actions
workflow **automatically** validates the version, builds the embedded web assets,
compiles an installable **debug-signed** Android APK and uploads the APK + `SHA256SUMS`
as a GitHub Actions artifact (`NotebookPocket-vX.Y.Z-debug`, retained for 30 days).
No keystore secrets or manual approvals are required for this **test build**.

To create a tag after merging workflow changes and confirming that CI passes:

```powershell
python scripts/check_release.py v0.9.3
# Only if the tag does not already exist and the current source version is 0.9.3:
git tag -a v0.9.3 -m "NotebookPocket 0.9.3"
git push origin v0.9.3
```

Open **Actions → APK on tag / signed release draft → the tag run → Artifacts**
to download the ZIP containing `NotebookPocket-vX.Y.Z-debug.apk` and checksum file.
**This does not create a public GitHub Release.** The APK uses the runner's debug key,
which may change between runs: it is unsuitable for official distributions or reliable
in-place updates of an installed production app. Do not reassign an existing tag;
for the next release, update `package.json`, `package-lock.json`, `versionCode` and
`CHANGELOG.md`, then push a new tag.

## Signed draft release (manual, gated)

After CI and device testing succeed and the protected `release` environment has
all signing secrets, start the workflow **manually on the matching tag**:

```powershell
gh workflow run release.yml --ref v0.9.3
```

The manual run executes CI and emulator tests, waits for `release` environment
approval, builds and verifies the **release-signed APK**, then creates a **draft**
GitHub Release with SHA-256 checksums. This run requires the signing secrets above;
without them, only the automatic debug APK is available.

Review the draft, verify the signing certificate against previous releases, test
in-place upgrades and publish the draft yourself. Never distribute the debug APK
as the official upgrade path.
