import Checkbox from '../vendor/components/checkbox/index.js';
import actionStack from '../vendor/lib/actionStack.js';
/** Project only Acode's boolean search preferences into this single window.
 * Full IDE settings navigation/file pickers remain outside the editor host.
 */
export default function settingsPage(title, items, callback) {
  const page = document.createElement('dialog');
  page.className = 'acode-search-settings';
  const heading = document.createElement('header');
  heading.textContent = title;
  const close = document.createElement('button');
  close.className = 'icon clearclose';
  close.ariaLabel = strings.close || 'Close';
  close.onclick = hide;
  heading.append(close);
  page.append(heading);
  for (const item of items) {
    const row = Checkbox(item.text, item.checkbox);
    row.onchange = () => callback(item.key, row.checked);
    page.append(row);
  }
  function hide() {
    actionStack.remove('search-settings');
    page.close();
    page.remove();
  }
  return {
    show() {
      app.append(page);
      page.showModal();
      actionStack.push({
        id: 'search-settings',
        action: hide
      });
      page.addEventListener('cancel', hide);
    },
    hide
  };
}
