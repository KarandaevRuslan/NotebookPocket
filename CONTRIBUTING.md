# Contributing

1. Use JDK 17, SDK 35 / build-tools 35.0.0 and the Node version in `.node-version`.
2. Install web dependencies with `npm ci`; keep `web/package-lock.json` in sync.
3. Keep changes focused. Data, WebView and lifecycle changes need meaningful regression tests.
4. Run `scripts/verify.sh`, or the individual commands in the README. Run device regressions for storage, encryption or WebView changes.
5. Open a pull request against `main` and include validation results.

Do not submit private notebooks, logs containing user data, signing material, APK/AAB files, generated web bundles or test caches. `.gitignore` rejects new images by default. Add an exact exception only for an intentionally reviewed resource; do not use a blanket exception for an entire artwork directory.

Preserve third-party license notices and the Acode provenance manifest. Changes under `web/src/acode/vendor` must update the corresponding reviewed patch/provenance and pass `check:acode`.

If files were already tracked before the ignore rules were added, `.gitignore` cannot untrack them. Review `git ls-files -ci --exclude-standard` and remove only intended generated files from the index with `git rm --cached`. This leaves local files on disk.
