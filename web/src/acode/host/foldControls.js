import {codeFolding, foldGutter} from '@codemirror/language';

function label(folded) {
  const ru = document.documentElement.lang === 'ru';
  return folded ? (ru ? 'Развернуть блок' : 'Unfold block') : (ru ? 'Свернуть блок' : 'Fold block');
}

export function notebookFoldControls() {
  return [foldGutter({markerDOM(open) {
    const marker = document.createElement('span');
    marker.className = `icon ${open ? 'keyboard_arrow_down' : 'keyboard_arrow_right'}`;
    marker.title = label(!open);
    marker.setAttribute('aria-label', marker.title);
    return marker;
  }}), codeFolding({
    preparePlaceholder(state, range) {
      return Math.max(1, state.doc.lineAt(range.to).number - state.doc.lineAt(range.from).number);
    },
    placeholderDOM(_view, unfold, lines) {
      const placeholder = document.createElement('span');
      placeholder.className = 'cm-foldPlaceholder';
      placeholder.textContent = `… ${lines}`;
      placeholder.title = label(true);
      placeholder.setAttribute('aria-label', `${label(true)} (${lines})`);
      placeholder.setAttribute('role', 'button');
      placeholder.tabIndex = 0;
      placeholder.onclick = unfold;
      placeholder.onkeydown = event => {
        if (event.key === 'Enter' || event.key === ' ') unfold(event);
      };
      return placeholder;
    }
  })];
}
