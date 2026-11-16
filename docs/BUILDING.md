# Build and diagnostics

## Toolchain

Use JDK 17, Node.js 22.16+ (CI pins `.node-version`), Android platform 35 and build-tools 35.0.0. The verified Gradle wrapper is included. Configure `ANDROID_HOME` or a local, untracked `local.properties`.

Build web assets first:

```sh
cd web
npm ci
npm test
npm run test:upstream
npm run build
cd ..
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Use `.\gradlew.bat` on Windows. Source-archive users may need `bash gradlew` if extraction did not preserve executable mode. Existing PowerShell helpers (`build-apk.ps1`, `install-apk.ps1`, `run-app.ps1`) remain available; inspect their parameters with `Get-Help`.

## Device tests

Start a clean emulator, then run:

```sh
./gradlew connectedDebugAndroidTest
bash scripts/process-death-smoke.sh
```

The second command deliberately force-stops the test target between checkpoint and recovery. Use an emulator/test installation, not an installation containing personal work.

## Logs

Read logs directly without creating a file:

```sh
adb logcat -v time -s NotebookPocket:V AndroidRuntime:E '*:S'
```

The existing editor-diagnostic PowerShell scripts can collect additional debug-only traces. Protected previews suppress notebook console messages. Avoid posting document text or passwords in issue logs.

## Network and dependency failures

An HTTP 403 when Gradle downloads a dependency is a repository/proxy access failure, not a Kotlin compilation error. Verify access to the exact URL and the Gradle proxy configuration, then retry with `--refresh-dependencies` after restoring access. Never replace an unavailable dependency with an unrelated JAR. CI reports remain separate from source files under `build/`.
