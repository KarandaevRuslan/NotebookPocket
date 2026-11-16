// Computation/style-only cells have no visible output and must not create separator rows.
export function updateCellVisibility(main) {
  for (const cell of main.children) {
    const hasOutput = Array.from(cell.childNodes).some((node) =>
      node.nodeType === 3
        ? Boolean(node.textContent.trim())
        : node.nodeType === 1 &&
          !['STYLE', 'SCRIPT', 'TEMPLATE'].includes(node.tagName),
    );
    cell.classList.toggle('cell-output-empty', !hasOutput);
    for (const form of cell.querySelectorAll('form')) {
      if ([...form.classList].some((name) => name.startsWith('inputs-')))
        form.classList.toggle(
          'np-input-button-group',
          [...form.children].some((node) => node.tagName === 'BUTTON'),
        );
    }
  }
}
