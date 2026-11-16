import core from 'nexus/core';

// Only the browser-compatible core enters the preview; Frida needs a native host.
export const Nexus = Object.freeze({ ...core });

/** One owner per preview. All managed scopes are also released on cell invalidation. */
export function createNexusHost({ observe, onError = console.error }) {
  const dispatcher = new Nexus.Dispatcher({ onError });
  const scopes = new Set();
  let disposed = false;

  function release(scope) {
    scope.dispose();
    scopes.delete(scope);
  }

  const context = Object.freeze({
    version: '0.1.20',
    dispatcher,
    scope(invalidation) {
      if (disposed) throw new Error('Nexus preview is disposed');
      if (invalidation !== undefined && typeof invalidation?.then !== 'function') {
        throw new TypeError('nexus.scope expects an invalidation Promise');
      }
      const scope = dispatcher.scope();
      scopes.add(scope);
      if (invalidation !== undefined) {
        Promise.resolve(invalidation).then(() => release(scope)).catch(onError);
      }
      return scope;
    },
    // A Store is a stable handle; Observable cannot detect its changes by identity.
    // Own the bridge in the source scope so either teardown order is safe.
    observe(scope, store) {
      if (disposed || !scopes.has(scope) || !scope.active) {
        throw new Error('nexus.observe expects an active scope from nexus.scope()');
      }
      if (!(store instanceof Nexus.Store) || store.owner !== dispatcher) {
        throw new TypeError('nexus.observe expects a Store from this preview dispatcher');
      }
      return observe((change) => {
        class ObserveStore extends Nexus.Handler {
          run(ctx) {
            const value = ctx.read(store);
            ctx.effect(() => change(value));
          }
        }
        const registration = scope.register(new ObserveStore(), {
          on: { any: [store] },
          initial: true,
          execution: 'inline',
        });
        return () => registration.dispose();
      });
    },
  });

  return {
    context,
    dispose() {
      disposed = true;
      const errors = [];
      // A failed user cleanup must not prevent unrelated scopes from closing.
      for (const scope of [...scopes].reverse()) {
        try { release(scope); } catch (error) { errors.push(error); }
      }
      if (errors.length) throw new AggregateError(errors, 'Nexus preview cleanup failed');
    },
  };
}
