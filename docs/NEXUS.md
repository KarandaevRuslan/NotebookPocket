# Nexus integration in NotebookPocket

[Nexus](https://github.com/KarandaevRuslan/Nexus) **Core 0.1.20** is bundled into NotebookPocket and available offline inside every notebook. [Observable Notebook Kit](https://github.com/observablehq/notebook-kit) **2.9.1** still parses, compiles and executes cells. Nexus adds transactional Stores, Events, Handlers and managed scopes; it does **not** replace Observable's dependency graph.

This page is a practical introduction. For the complete list of NotebookPocket-provided globals, timer methods, device preferences and attachment behavior, see the **[host API reference](API.md)**.

## 1. Where Nexus comes from

The repository declares the dependency as a **local npm tarball**, not an inline source copy and not a package fetched from the npm registry:

```json
"nexus": "file:vendor/nexus-0.1.20.tgz"
```

`npm ci` installs it into `web/node_modules/nexus`; `web/src/preview/nexusRuntime.js` imports `nexus/core` and Vite includes the browser-compatible code in the APK. No installation or network connection is required on Android. For provenance, version verification and license, see [`third_party/nexus/README.md`](../third_party/nexus/README.md).

## 2. The notebook-visible API

Two objects are registered as Notebook Kit built-ins:

| Built-in | Purpose |
| --- | --- |
| `Nexus` | Frozen object containing upstream exports from `nexus/core`: `Dispatcher`, `Handler`, `Scope`, `Store`, `Event`, error types and related Core facilities. |
| `nexus.version` | The bundled version, `"0.1.20"`. |
| `nexus.dispatcher` | One dispatcher for the **current preview render**. |
| `nexus.scope(invalidation)` | Create a scope automatically released when the Observable cell is invalidated. |
| `nexus.scope()` | Create a scope managed until explicit disposal or preview teardown. |
| `nexus.observe(scope, store)` | Observable generator emitting the Store's initial and committed values. |

`nexus.scope()` and `nexus.observe()` are **NotebookPocket integration helpers**. Methods such as `scope.store`, `scope.guard`, `scope.cleanup`, `scope.register` and `dispatcher.inline` belong to Nexus itself. They are documented by the [Nexus framework](https://github.com/KarandaevRuslan/Nexus).

## 3. Write transactions in JavaScript 2.0 cells

Define scope and state in a module cell:

```js
const scope = nexus.scope(invalidation);
const counter = scope.store(0);
```

In a subsequent module cell, create a button and bind a guarded event handler:

```js
const button = html`<button type="button">+1</button>`;
const increment = scope.guard(() => {
  nexus.dispatcher.inline(ctx => {
    ctx.set(counter, ctx.read(counter) + 1);
  });
});
button.addEventListener('click', increment);
scope.cleanup(() => button.removeEventListener('click', increment));
display(button);
```

The dispatcher changes a Store **inside a transaction**. `scope.guard` prevents late callbacks from executing after cleanup; `scope.cleanup` removes the DOM listener. The scope is tied to `invalidation` so reactive cell replacement does not leave orphaned handlers.

## 4. Derive state with `Nexus.Handler`

```js
const servings = scope.store(0);
class ComputeServings extends Nexus.Handler {
  run(ctx) {
    ctx.set(servings, Math.floor(ctx.read(counter) / 2));
  }
}
scope.register(new ComputeServings(), {
  on: { any: [counter] },
  initial: true,
  execution: 'inline'
});
```

Nexus updates `servings` after the relevant committed changes. Side effects should be scheduled through Nexus's effect facilities when appropriate. Resource/DOM cleanup belongs in the owning scope. Core also provides `scope.submit(...)` and `scope.submitAndWait(...)` for commands, with scope-aware cancellation.

## 5. Bridge Nexus Stores into Observable

A Store is a stable object. Reading it once via `dispatcher.inline` does **not** turn its value into an Observable dependency. Instead, use `nexus.observe(...)` inside an **Observable JavaScript (OJS)** cell:

```html
<script id="4" type="application/vnd.observable.javascript">
readyServings = nexus.observe(scope, servings)
</script>
```

A dependent `type="module"` cell can then use the named reactive value:

```js
display(html`<p>Ready servings: ${readyServings}</p>`);
```

In a regular JavaScript 2.0 cell, `const value = nexus.observe(scope, counter)` simply assigns the **generator object**; it does not automatically unwrap its emitted values. Keep the OJS bridge for a named reactive output.

The bridge verifies that the scope is active and belongs to the current Nexus host and that the Store belongs to the same dispatcher. A generator subscription is released when Observable stops it or its owning scope is disposed. Very rapid state transitions may be coalesced; use a Nexus Handler if every intermediate change matters.

## 6. Lifetime, isolation and persistence

Each preview render owns its dispatcher and managed scopes. Cell invalidation, document switch, Preview Reset or teardown dispose scopes and subscriptions. For directly constructed `new Nexus.Dispatcher()` or other upstream instances, authors must manage their own cleanup.

**Nexus Store state is memory-only** in this integration. Neither `localStorage` nor Room nor notebook drafts automatically serialize it. If a notebook needs persisted user inputs, distinguish that from the current form-state persistence and `Pocket.timer` behavior described in [API.md](API.md#2-pocket-app-settings-clock-and-timers).

Only browser-compatible `nexus/core` enters the preview; Frida adapters are not available. Nexus does **not** grant native Android access or add a JavaScript bridge, Internet permission or relaxed CSP.

## 7. Tutorials and working examples

- **Bundled Pancakes recipe:** `app/src/main/assets/demo/pancakes/notebook.html`. Its “Cooking with Nexus” section increments/decrements the finished-pancakes counter, uses a Handler to derive servings, and renders progress through two OJS subscriptions. Changing recipe ingredients does not reset the current count.
- **Importable counter:** [`examples/nexus-counter.html`](../examples/nexus-counter.html). It demonstrates one Store, a derived Store, an OJS bridge and a dependent module cell.
- **Upstream framework:** [KarandaevRuslan/Nexus](https://github.com/KarandaevRuslan/Nexus).

The Pancakes sample uses `nexus.scope(invalidation)` and releases event handlers through `scope.guard`/`scope.cleanup`. Its Nexus counter is deliberately reset when preview execution is restarted. The existing `Pocket.timer` in that recipe remains a separate API. Existing customized sample notebooks are not overwritten by the bundled demo-upgrade mechanism.

## 8. Developer checks

```sh
cd web
npm ci
npm test
npm run test:upstream
npm run build
npm run test:built-preview
```

The local Nexus tarball is committed, so `npm ci` does not need to download Nexus from GitHub Releases. An archived **historical** validation report lives in [NEXUS-VALIDATION.md](NEXUS-VALIDATION.md); its dated results should not be treated as the status of the current branch.
