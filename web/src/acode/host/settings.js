// NotebookPocket preferences implement Acode's settings service contract.
const events = new Map();
const read = (key, fallback) => {
  try {
    const v = JSON.parse(localStorage.getItem(key));
    return v ?? fallback;
  } catch {
    return fallback;
  }
};
const settings = {
  QUICKTOOLS_GROUP_CAPACITY: 8,
  QUICKTOOLS_GROUPS: 2,
  QUICKTOOLS_ROWS: 2,
  QUICKTOOLS_TRIGGER_MODE_TOUCH: 'touch',
  QUICKTOOLS_TRIGGER_MODE_CLICK: 'click',
  value: {
    quickTools: read('np-acode-quicktools', 1),
    floatingButton: true,
    quickToolsTriggerMode: navigator.maxTouchPoints > 0 ? 'touch' : 'click',
    vibrateOnTap: false,
    touchMoveThreshold: Math.round(1 / devicePixelRatio * 10) / 20,
    quicktoolsItems: [2, 1, 5, 3, 4, 18, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 33, 21, 20, 16, 19, 17, 23, 24, 25, 26, 27, 28, 29, 30, 31],
    search: {
      caseSensitive: false,
      regExp: false,
      wholeWord: false
    },
    showShareButton: false,
    confirmOnExit: false,
    softTab: true,
    tabSize: 2,
    fontSize: '14px',
    textWrap: true,
    lineHeight: 1.4,
    shiftClickSelection: true,
    indentGuides: true,
    wrappingIndent: 'same',
    scrollPastEnd: 'medium',
    scrollbarSize: 20,
    scrollbarHeight: 50
  },
  get(key) {
    return this.value[key];
  },
  on(names, fn) {
    for (const name of Array.isArray(names) ? names : [names]) {
      if (!events.has(name)) events.set(name, new Set());
      events.get(name).add(fn);
    }
    return () => this.off(names, fn);
  },
  off(names, fn) {
    for (const name of Array.isArray(names) ? names : [names]) events.get(name)?.delete(fn);
  },
  update(next = {}, save = true) {
    if (!next || typeof next !== 'object') next = {};
    Object.assign(this.value, next);
    for (const name of Object.keys(next)) {
      for (const event of ['update:' + name, 'update:' + name + ':after']) for (const cb of events.get(event) || []) cb(this.value[name]);
    }
    for (const cb of events.get('update') || []) cb(this.value);
    if (save && Object.hasOwn(next, 'quickTools')) try {
      localStorage.setItem('np-acode-quicktools', JSON.stringify(this.value.quickTools));
    } catch {}
  }
};
export default settings;
