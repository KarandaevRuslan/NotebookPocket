# NotebookPocket 0.9.3 — Nexus in the Pancakes demonstration

> Historical patch notes for the Nexus-enhanced bundled sample. The current integration API is documented in [`docs/NEXUS.md`](docs/NEXUS.md) and [`docs/API.md`](docs/API.md).

## What was added

The bundled recipe gained a **“04 / Cooking with Nexus”** panel after the step-by-step instructions. Its **“+1 ready”**, **“−1”** and **“Start over”** controls update a counter through `nexus.dispatcher.inline` transactions. A `Nexus.Handler` derives the number of complete servings at **two pancakes per serving**. Two `nexus.observe` subscriptions publish the Store values to Observable; a dependent cell updates the counters, remaining-pancakes total and progress indicator. Changing the egg quantity adjusts the target without resetting the counter.

The panel includes an expandable explanation of `Scope`, `Store`, `Dispatcher`, `Handler` and `observe`, with Russian and English UI strings and adaptive styling. `nexus.scope(invalidation)`, `scope.guard` and `scope.cleanup` demonstrate safe lifecycle management. The original recipe, step navigation and `Pocket.timer` remain intact.

## Updating existing demo documents

On startup, the demo migration updates **only untouched stock versions** of the sample (the older v5 and immediately preceding v6), preserving public metadata. It checks the source HTML and the SHA-256 of each resource. User-modified HTML/CSS/JSON and pending drafts are skipped.

The previous v6 source and resource hashes are retained for migration comparison. The `data-state-version="pancakes-v5"` identifier was deliberately preserved so persisted recipe preferences would not be reset by the upgrade.

If a user has customized the demonstration HTML or attachments, they should create a new notebook through the app's create action to obtain the newer stock sample. The Nexus counter lives only in the current preview session and resets when the preview restarts.

## Installing and testing the historical patch

The original patch ZIP was intended to be extracted into the project root with replacement of the listed files. It was **not** a full source release; its baseline was `NotebookPocket-Nexus-0.1.20(1).zip`.

```powershell
cd web
node --test tests/pancakes-nexus-contract.test.mjs
npm test
cd ..
.\gradlew.bat :app:assembleDebug
```

The historical change touched Android bundled demo assets and tests, not the WebView JavaScript implementation. An already built matching WebView bundle therefore did not require an additional `npm run build` for that patch alone. A fresh checkout must still execute `cd web; npm ci; npm run build; cd ..` before Gradle.

## Validation recorded when the patch was prepared

- Three standalone contract/localization/previous-hash checks passed.
- Syntax checks passed for all 20 JavaScript module cells in the demo.
- A standalone test exercised the real bundled Nexus 0.1.20 Core `Store`, `Handler`, `Dispatcher` and `Scope`.
- The full `npm test`, Android build and actual WebView execution were **not** run in that environment because npm network access and the Android SDK were unavailable. An end-to-end case was added to `web/tests/pancake-demo.test.mjs` for later local/CI execution.

These are historical patch-specific claims, not a claim about the latest build or device compatibility.
