import { languageForFile } from '../acode/host/resourceLanguage.js';
import { manager } from '../acode/host/environment.js';
import settings from '../acode/host/settings.js';
import { EditorState, Compartment } from '@codemirror/state';
import { EditorView, lineNumbers } from '@codemirror/view';
import { search } from '@codemirror/search';
import { indentUnit } from '@codemirror/language';
import { autocompletion } from '@codemirror/autocomplete';
import { javascriptLanguage } from '@codemirror/lang-javascript';
import { oneDark } from '@codemirror/theme-one-dark';
import createBaseExtensions from '../acode/vendor/cm/baseExtensions.ts';
import createMainEditorExtensions from '../acode/vendor/cm/mainEditorExtensions.ts';
import { handleLineNumberClick } from '../acode/vendor/cm/lineNumberSelection.ts';
import quickToolsModifierInput from '../acode/vendor/cm/quickToolsModifierInput.ts';
import { indentedLineWrapping } from '../acode/vendor/cm/indentedLineWrapping.ts';
import scrollPastEnd, {
  horizontalScrollPastEnd,
} from '../acode/vendor/cm/scrollPastEnd.ts';
import localWordCompletions from '../acode/vendor/cm/localWordCompletions.ts';
import indentGuides from '../acode/vendor/cm/indentGuides.ts';
import tagAutoRename from '../acode/vendor/cm/tagAutoRename.ts';
import rainbowBrackets from '../acode/vendor/cm/rainbowBrackets.ts';
import { createEditorReadOnlyExtension } from '../acode/vendor/cm/editorReadOnly.ts';
import githubLight from '../acode/vendor/cm/themes/githubLight.js';
import { getCommandKeymapExtension } from '../acode/vendor/cm/commandRegistry.js';
import { observableCompletionSource } from '../acode/host/observableCompletions.js';
import {
  editorTooltips,
  completionLayout,
} from '../acode/host/completionLayout.js';
import { notebookFoldControls } from '../acode/host/foldControls.js';
import { nativeSelectionInput } from '../acode/host/nativeSelectionInput.js';

export const DEFAULT_PREFERENCES = Object.freeze({
  dark: false,
  language: 'en',
  theme: 'system',
  fontSize: 14,
  wordWrap: true,
  autocomplete: true,
  showQuickTools: true,
});
export const isDark = (preferences) =>
  preferences.theme === 'dark' ||
  (preferences.theme !== 'light' && preferences.dark);
const themeExtension = (preferences) =>
  isDark(preferences) ? oneDark : githubLight();
const typographyTheme = (preferences) =>
  EditorView.theme({
    '&': { fontSize: `${preferences.fontSize}px` },
    '.cm-scroller': { lineHeight: '1.4' },
  });

export function createEditorConfiguration({
  runtime,
  getPreferences,
  getDocument,
  onUpdate,
}) {
  const syntax = new Compartment(),
    appearance = new Compartment(),
    completion = new Compartment(),
    wrapping = new Compartment(),
    typography = new Compartment();
  function completionOptions() {
    return getPreferences().autocomplete
      ? [
          localWordCompletions(),
          ...(getDocument().kind === 'notebook'
            ? [
                javascriptLanguage.data.of({
                  autocomplete: observableCompletionSource,
                }),
              ]
            : []),
          autocompletion({
            activateOnTyping: true,
            activateOnTypingDelay: navigator.maxTouchPoints > 0 ? 220 : 100,
            ...completionLayout,
            maxRenderedOptions: 100,
            selectOnOpen: true,
          }),
        ]
      : [];
  }
  function createState(doc) {
    return EditorState.create({
      doc,
      extensions: createMainEditorExtensions({
        baseExtensions: [
          editorTooltips(),
          ...createBaseExtensions({ foldGutter: false }),
        ],
        commandKeymapExtension: getCommandKeymapExtension(),
        ...runtime.extensions,
        quickToolsModifierInputExtension: quickToolsModifierInput(),
        searchExtension: search(),
        readOnlyExtension: createEditorReadOnlyExtension(false),
        optionExtensions: [
          nativeSelectionInput(() => manager.editor),
          syntax.of(languageForFile(getDocument().name, getDocument().kind)),
          indentUnit.of('  '),
          EditorState.tabSize.of(2),
          indentGuides({
            highlightActiveGuide: false,
            hideOnBlankLines: false,
          }),
          notebookFoldControls(),
          tagAutoRename(),
          rainbowBrackets(),
          lineNumbers({
            domEventHandlers: {
              click: (view, line, event) =>
                handleLineNumberClick(view, line, event, {
                  shiftClickSelection: settings.value.shiftClickSelection,
                }),
            },
          }),
          appearance.of(themeExtension(getPreferences())),
          completion.of(completionOptions()),
          wrapping.of(
            getPreferences().wordWrap ? indentedLineWrapping('same') : [],
          ),
          typography.of(typographyTheme(getPreferences())),
          scrollPastEnd(0.5),
          horizontalScrollPastEnd(40),
          EditorView.updateListener.of(onUpdate),
        ],
      }),
    });
  }
  function reconfigure(editor) {
    const preferences = getPreferences();
    editor.dispatch({
      effects: [
        appearance.reconfigure(themeExtension(preferences)),
        completion.reconfigure(completionOptions()),
        wrapping.reconfigure(
          preferences.wordWrap ? indentedLineWrapping('same') : [],
        ),
        typography.reconfigure(typographyTheme(preferences)),
      ],
    });
  }
  function configureDocument(editor) {
    const { name, kind } = getDocument();
    editor.dispatch({
      effects: [
        syntax.reconfigure(languageForFile(name, kind)),
        completion.reconfigure(completionOptions()),
      ],
    });
  }
  return { createState, reconfigure, configureDocument };
}
