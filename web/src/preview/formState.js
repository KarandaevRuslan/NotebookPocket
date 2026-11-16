/** A render-scoped form-state controller. Storage is injected; protected forms never read/write it. */
export function createFormState(main, storage) {
  const lifetime = new AbortController();
  let restored, restoredInputKeys, restoredStepControls, restoredStepKeys;
  let storageKey = '',
    protectedMode = false;
  function begin(key, encrypted) {
    storageKey = key;
    protectedMode = Boolean(encrypted);
    restored = new WeakSet();
    restoredInputKeys = new Set();
    restoredStepControls = new WeakSet();
    restoredStepKeys = new Set();
    if (protectedMode)
      try {
        storage.removeItem(storageKey);
      } catch {
        /* Optional storage. */
      }
  }
  // Persist input *state*, not the source code. Keyed by document ID and cell ID.
  function controlKey(control) {
    const cell = control.closest('[data-cell-id]');
    if (!cell) return null;
    const controls = Array.from(
      cell.querySelectorAll('input, select, textarea'),
    );
    const idx = controls.indexOf(control);
    return `${cell.dataset.cellId}:${idx}`;
  }
  function loadState() {
    if (protectedMode) return {};
    try {
      return JSON.parse(storage.getItem(storageKey) || '{}');
    } catch {
      return {};
    }
  }
  function persistControl(control) {
    if (protectedMode || control.closest('[data-np-ephemeral]')) return;
    const key = controlKey(control);
    if (!key) return;
    const state = loadState();
    const value =
      control.type === 'checkbox' || control.type === 'radio'
        ? { checked: control.checked }
        : { value: control.value };
    const dependency = control.closest('form[data-np-default-key]')?.dataset
      .npDefaultKey;
    if (dependency !== undefined) value.defaultKey = dependency;
    state[key] = value;
    try {
      storage.setItem(storageKey, JSON.stringify(state));
    } catch {
      /* WebView may disallow storage or be full. */
    }
  }
  main.addEventListener(
    'input',
    (event) => {
      if (event.target?.matches?.('input,select,textarea'))
        persistControl(event.target);
    },
    { capture: true, signal: lifetime.signal },
  );
  main.addEventListener(
    'change',
    (event) => {
      if (event.target?.matches?.('input,select,textarea'))
        persistControl(event.target);
    },
    { capture: true, signal: lifetime.signal },
  );

  // Inputs.button returns a form carrying a numeric .value; input/change events
  // for the buttons alone are not enough for the standard input-state serializer.
  // Keep step persistence opt-in so third-party notebook controls are untouched.
  function stepKey(form) {
    const id = form.closest('[data-cell-id]')?.dataset.cellId;
    return id ? `${id}:step` : null;
  }
  main.addEventListener(
    'input',
    (event) => {
      if (protectedMode) return;
      const form = event.target?.closest?.('form[data-np-persist-step]');
      if (!form) return;
      const key = stepKey(form);
      if (!key) return;
      const state = loadState();
      state[key] = { value: Number(form.value) };
      try {
        storage.setItem(storageKey, JSON.stringify(state));
      } catch {
        /* Storage optional. */
      }
    },
    { capture: true, signal: lifetime.signal },
  );

  function restoreControls() {
    const state = loadState();
    for (const control of main.querySelectorAll('input,select,textarea')) {
      if (control.closest('[data-np-ephemeral]') || restored.has(control))
        continue;
      restored.add(control);
      const key = controlKey(control);
      // A reactive recompute may replace a control, e.g. changing eggs updates
      // the suggested milk. Restore an input only once per notebook render,
      // otherwise an obsolete saved value overrides the new recommendation.
      if (!key || restoredInputKeys.has(key)) continue;
      const saved = state[key];
      const dependency = control.closest('form[data-np-default-key]')?.dataset
        .npDefaultKey;
      // Defer restoring the milk override until the egg-dependent default has
      // caught up after restoring the eggs input (not the initial 2-egg DOM).
      if (saved?.defaultKey !== undefined && saved.defaultKey !== dependency)
        continue;
      restoredInputKeys.add(key);
      if (!Object.hasOwn(state, key)) continue;
      const next =
        'checked' in saved ? Boolean(saved.checked) : String(saved.value);
      const current = 'checked' in saved ? control.checked : control.value;
      if (current === next) continue;
      if ('checked' in saved) control.checked = next;
      else control.value = next;
      control.dispatchEvent(new Event('input', { bubbles: true }));
    }
    for (const form of main.querySelectorAll('form[data-np-persist-step]')) {
      if (restoredStepControls.has(form)) continue;
      restoredStepControls.add(form);
      const key = stepKey(form);
      if (!key || restoredStepKeys.has(key)) continue;
      restoredStepKeys.add(key);
      const value = state[key]?.value;
      if (
        !Number.isSafeInteger(value) ||
        value < 0 ||
        value >
          Math.min(1000, Math.max(0, Number(form.dataset.npMaxStep ?? 6))) ||
        form.value === value
      )
        continue;
      form.value = value;
      form.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  begin('', false);
  return { begin, restore: restoreControls, destroy: () => lifetime.abort() };
}
