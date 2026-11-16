import { initIconTooltips } from '../acode/vendor/components/tooltip/index.js';
import {
  executeCommand,
  registerExternalCommand,
  refreshCommandKeymap,
} from '../acode/vendor/cm/commandRegistry.js';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import { RowItem } from '../acode/vendor/components/quickTools/footer.js';
import initQuickTools from '../acode/vendor/handlers/quickToolsInit.js';

export function installEditorToolbar({
  editor,
  insertCell,
  hostCommand,
  native,
}) {
  initQuickTools();
  // Autosave owns persistence. Keep Acode's save command/keymap available,
  // Replace its slot with Acode's existing tilde RowItem so both default
  // sections retain eight equally sized buttons, including arrow alignment.
  for (const row of [quickTools.$row1, quickTools.$row2])
    row
      .querySelectorAll('[data-id="save"]')
      .forEach((button) =>
        button.replaceWith(
          RowItem({
            id: 'tilde',
            icon: 'letters',
            action: 'insert',
            value: '~',
            letters: '~',
          }),
        ),
      );
  initIconTooltips();
  const extra = document.createElement('div');
  extra.className = 'section';
  for (const [name, icon, label, run] of [
    ['notebookInsertJS', 'code', 'JavaScript', () => insertCell('js')],
    ['notebookInsertMD', 'text_format', 'Markdown', () => insertCell('md')],
    [
      'notebookFullscreen',
      'fullscreen',
      'Fullscreen',
      () => hostCommand('toggle-fullscreen'),
    ],
    [
      'notebookKeyboard',
      'keyboard_hide',
      'Keyboard',
      () => native?.toggleKeyboard(),
    ],
    [
      'foldCode',
      'unfold_less',
      'Fold',
      () => executeCommand('foldCode', editor),
    ],
    [
      'unfoldCode',
      'unfold_more',
      'Unfold',
      () => executeCommand('unfoldCode', editor),
    ],
    [
      'foldAll',
      'unfold_less',
      'Fold all',
      () => executeCommand('foldAll', editor),
    ],
    [
      'unfoldAll',
      'unfold_more',
      'Unfold all',
      () => executeCommand('unfoldAll', editor),
    ],
  ]) {
    if (name.startsWith('notebook'))
      registerExternalCommand({
        name,
        description: label,
        exec: run,
      });
    const b = RowItem({
      id: name,
      icon,
      action: 'command',
      value: name,
    });
    b.ariaLabel = label;
    b.title = label;
    extra.append(b);
  }
  quickTools.$row2.append(extra);
  refreshCommandKeymap(editor);

  return () => extra.remove();
}
