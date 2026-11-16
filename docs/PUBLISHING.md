# Publish NotebookPocket on GitHub

Proposed repository: **`KarandaevRuslan/NotebookPocket`** (public after reviewing staged content).

## 1. Inspect before you publish

This source export does not include generated Vite assets, release APKs, signing keys or your private notebooks. **Always inspect your local tree** before committing: your local working directory might contain additional sensitive files.

In PowerShell, run from the project root:

```powershell
python scripts/check_repository.py
Get-ChildItem -Force
```

Check `git status` and later `git diff --cached --name-only`; ensure no personal data, local paths, credentials or test logs are staged. The initial source export carries a local Nexus tarball: keep `web/vendor/nexus-0.1.20.tgz`; it is required for `npm ci`.

## 2. Create the GitHub repository

Install the [GitHub CLI](https://cli.github.com/), then sign in on your PC:

```powershell
gh auth login
gh auth status
```

You can also use the Windows helper: `powershell -ExecutionPolicy Bypass -File .\scripts\publish-github.ps1` first performs a **dry run**; rerun it with `-Publish` to require an interactive `PUBLISH` confirmation before creating anything.

If the repository does **not** exist yet, from the extracted project root:

```powershell
git init -b main
git add -A
python scripts/check_repository.py
git diff --cached --check
git diff --cached --stat
# Review the staged file list above before committing.
git commit -m "Initial public source: NotebookPocket 0.9.3 + Nexus"
gh repo create KarandaevRuslan/NotebookPocket --public --source=. --remote=origin --push
```

If you prefer GitHub's web UI, create an **empty** public repository named `NotebookPocket` under `KarandaevRuslan` (do not add an auto-generated README/license). Then replace the last command with:

```powershell
git remote add origin https://github.com/KarandaevRuslan/NotebookPocket.git
git push -u origin main
```

If you already have a local `.git` history, do **not** run `git init` or force-push over it; inspect the existing remotes and history first.

## 3. Configure repository settings

- About: `Offline-first Android Observable Notebook editor powered by Notebook Kit 2.x, Acode/CodeMirror and Nexus.`
- Website: optional; leave unset if you have no official app page.
- Topics: `android`, `kotlin`, `jetpack-compose`, `observable`, `notebook`, `code-editor`, `acode`, `codemirror`, `offline-first`.
- Enable **Issues** and **Discussions** as needed; enable **Dependabot alerts** and **private vulnerability reporting**.
- Protect `main`: require CI and pull-request review once the first CI run has completed. Match exact required job names shown in Actions.
- For public release builds, create an environment called `release` with reviewers; add required encrypted signing secrets per [RELEASE.md](RELEASE.md).
- In repository settings, optionally use `docs/brand/social-preview.png` for the GitHub social preview.

## 4. First CI pass

Push the initial branch and check GitHub Actions. Web assets are built on the runner, not committed. If a test fails, investigate it before tagging a release. Do not bypass CI and device gates to produce a public installer.

## 5. Tag for an automatic APK build

After merging the workflow update and confirming a green CI run, validate the
version and push a **new** tag:

```powershell
python scripts/check_release.py v0.9.3
# Only when this tag does not already exist:
git tag -a v0.9.3 -m "NotebookPocket 0.9.3"
git push origin v0.9.3
```

GitHub Actions automatically compiles a **debug-signed** APK and uploads it to the
tag run's **Artifacts** (APK + SHA256SUMS). This does **not** publish a GitHub
Release. Use it for testing only; debug signing is not an upgradeable distribution
identity. No signing secrets are required for this build.

## 6. Prepare an official signed release

For a real installer intended for ongoing upgrades, configure the protected `release`
environment and its signing secrets as described in [RELEASE.md](RELEASE.md).
Run `gh workflow run release.yml --ref v0.9.3` manually on the **existing tag**.
The workflow runs CI and emulator checks, verifies the signed APK and creates a
**draft** release for manual review/publishing. Never commit signing certificates
or credentials; do not push or overwrite an existing version tag.
