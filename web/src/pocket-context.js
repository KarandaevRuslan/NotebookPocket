/** Read-only app context and per-notebook timers; no Android bridge is exposed. */
const MAX_DURATION = 7 * 24 * 60 * 60 * 1000;
const validId = id => typeof id === 'string' && /^[\w.-]{1,80}$/.test(id);
function duration(value) {
  if (!Number.isFinite(value) || value < 1 || value > MAX_DURATION) {
    throw new RangeError('Timer duration must be between 1 ms and 7 days');
  }
  return Math.round(value);
}

export function createPocketHost({wall = () => Date.now(), monotonic = () => performance.now()} = {}) {
  let preferences = {theme:'light', dark:false, language:'en', locale:'en', timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone, hourCycle:'h23'};
  let sample;
  const stores = new Map();
  const elapsed = () => sample ? sample.elapsed + monotonic() - sample.received : monotonic();
  const clock = () => sample?.clock ?? {kind:'web', boot:wall() - monotonic()};

  function configure(input = {}) {
    const language = input.language === 'ru' ? 'ru' : 'en';
    let timeZone = input.timeZone || preferences.timeZone;
    let locale = input.locale || language;
    try { new Intl.DateTimeFormat(locale).format(0); } catch { locale = language; }
    try { new Intl.DateTimeFormat(locale, {timeZone}).format(0); }
    catch { timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone; }
    const dark = input.dark === true;
    preferences = Object.freeze({theme:dark ? 'dark' : 'light', dark, language, locale, timeZone, hourCycle:input.hourCycle === 'h12' ? 'h12' : 'h23'});
    syncClock(input);
  }

  function syncClock(input = {}) {
    if (Number.isFinite(input.epochMillis) && Number.isFinite(input.elapsedRealtimeMillis) && input.elapsedRealtimeMillis >= 0) {
      sample = {
        elapsed:input.elapsedRealtimeMillis + Math.max(0, wall() - input.epochMillis),
        received:monotonic(),
        clock:Number.isInteger(input.bootCount) && input.bootCount >= 0
          ? {kind:'native-boot', boot:input.bootCount}
          : {kind:'native-epoch', boot:input.epochMillis - input.elapsedRealtimeMillis}
      };
    }
  }

  function context({key, storage, protectedMode = false}) {
    const namespace = `notebook-pocket:timers:${key}`;
    const scope = `${protectedMode ? 'private' : 'public'}:${key}`;
    if (protectedMode) try { storage?.removeItem(namespace); } catch { /* Optional storage. */ }
    let states = stores.get(scope);
    if (!states) {
      states = new Map();
      if (!protectedMode) try {
        const saved = JSON.parse(storage?.getItem(namespace) || '{}');
        if (saved && typeof saved === 'object' && !Array.isArray(saved)) {
          for (const [id,s] of Object.entries(saved).slice(0,64)) {
            if (!validId(id) || !s || !Number.isFinite(s.duration) || s.duration < 1 || s.duration > MAX_DURATION ||
                !Number.isFinite(s.remaining) || s.remaining < 0 || s.remaining > s.duration || typeof s.running !== 'boolean') continue;
            let remaining = s.remaining;
            if (s.running) {
              const nowClock = clock();
              const tolerance = nowClock.kind === 'native-epoch' ? 60000 : nowClock.kind === 'web' ? 1 : 0;
              const sameClock = s.clock?.kind === nowClock.kind && Number.isFinite(s.clock.boot) &&
                Math.abs(s.clock.boot - nowClock.boot) <= tolerance && Number.isFinite(s.savedElapsed) && elapsed() >= s.savedElapsed;
              remaining = Math.max(0, Math.min(s.duration,
                sameClock && Number.isFinite(s.deadlineElapsed) ? s.deadlineElapsed - elapsed()
                : Number.isFinite(s.deadlineWall) ? s.deadlineWall - wall() : 0));
            }
            states.set(id, {duration:s.duration, remaining, running:s.running && remaining > 0, deadline:elapsed() + remaining});
          }
        }
      } catch { /* Corrupt saved state must never prevent opening a notebook. */ }
      if (stores.size >= 8) stores.delete(stores.keys().next().value);
      stores.set(scope, states);
    }
    const snapshot = state => {
      const remaining = Math.max(0, Math.min(state.duration, state.running ? state.deadline - elapsed() : state.remaining));
      return Object.freeze({durationMs:state.duration, remainingMs:remaining, elapsedMs:state.duration - remaining,
        running:state.running && remaining > 0, finished:remaining === 0});
    };
    const persist = () => {
      if (protectedMode) return;
      const saved = Object.create(null);
      for (const [id,s] of states) {
        const value = snapshot(s);
        saved[id] = {duration:s.duration, remaining:value.remainingMs, running:value.running,
          deadlineElapsed:s.deadline, savedElapsed:elapsed(), deadlineWall:wall() + value.remainingMs, clock:clock()};
      }
      try { storage?.setItem(namespace, JSON.stringify(saved)); } catch { /* Storage may be disabled. */ }
    };
    function timer(id, initialDurationMs = 60000) {
      if (!validId(id)) throw new TypeError('Invalid timer id');
      if (!states.has(id)) {
        if (states.size >= 64) throw new RangeError('Too many timers');
        const ms = duration(initialDurationMs);
        states.set(id, {duration:ms, remaining:ms, running:false, deadline:0});
      }
      const s = states.get(id);
      return Object.freeze({
        snapshot:() => snapshot(s),
        start() {
          const value = snapshot(s);
          if (value.running || value.finished) return value;
          s.remaining = value.remainingMs; s.deadline = elapsed() + s.remaining; s.running = true;
          persist(); return snapshot(s);
        },
        pause() { s.remaining = snapshot(s).remainingMs; s.running = false; persist(); return snapshot(s); },
        reset(ms = s.duration) { s.duration = duration(ms); s.remaining = s.duration; s.running = false; s.deadline = 0; persist(); return snapshot(s); }
      });
    }
    return Object.freeze({...preferences, now:() => new Date(wall()), timestamp:wall, elapsedRealtime:elapsed, timer});
  }
  function resetTimers(key, storage) {
    stores.delete(`private:${key}`); stores.delete(`public:${key}`);
    try { storage?.removeItem(`notebook-pocket:timers:${key}`); } catch { /* Optional storage. */ }
  }
  return Object.freeze({configure, syncClock, context, resetTimers});
}
