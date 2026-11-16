import settings from '../acode/host/settings.js';
import { search } from '@codemirror/search';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import actions, {
  cancelQuickToolsModifierInput,
  key,
} from '../acode/vendor/handlers/quickTools.js';
import actionStack from '../acode/vendor/lib/actionStack.js';

export function createEditorSearch({ editor, native }) {
  let searchRestored = false;
  function searchSnapshot() {
    return {
      open: actionStack.has('search-bar'),
      query: quickTools.$searchInput.el?.value || '',
      replace: quickTools.$replaceInput.el?.value || '',
      options: {
        matchCase: settings.value.search.caseSensitive,
        regex: settings.value.search.regExp,
        whole: settings.value.search.wholeWord,
      },
    };
  }
  function emitSearch() {
    native?.searchState(JSON.stringify(searchSnapshot()));
  }
  function restore(raw) {
    if (searchRestored) return;
    searchRestored = true;
    let state;
    try {
      state = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch {
      return;
    }
    if (!state?.open) return;
    const selection = editor.state.selection,
      scroll = editor.scrollSnapshot(),
      focused = document.activeElement;
    settings.value.search = {
      caseSensitive: !!state.options?.matchCase,
      regExp: !!state.options?.regex,
      wholeWord: !!state.options?.whole,
    };
    actions('search');
    quickTools.$searchInput.el.value = String(state.query || '');
    quickTools.$replaceInput.el.value = String(state.replace || '');
    quickTools.$searchInput.el.dispatchEvent(new Event('input'));
    editor.dispatch({
      selection,
      effects: scroll,
    });
    quickTools.$searchInput.el.blur();
    if (focused instanceof HTMLElement && focused !== document.body)
      focused.focus();
  }
  return {
    emit: emitSearch,
    restore,
    reset: () => {
      searchRestored = false;
    },
  };
}
