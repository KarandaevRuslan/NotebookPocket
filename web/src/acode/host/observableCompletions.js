// Supported notebook DSL API names. More can be added without a custom parser.
export const observableSymbols = [
  ['display', 'Display a value or DOM node'],
  ['view', 'Create a reactive Observable input'],
  ['md', 'Markdown template literal'],
  ['html', 'HTML template literal'],
  ['Inputs', 'Interactive controls'],
  ['Inputs.range', 'Numeric slider'],
  ['Inputs.number', 'Numeric input'],
  ['Inputs.text', 'Text field'],
  ['Inputs.select', 'Select one value'],
  ['Inputs.radio', 'Radio buttons'],
  ['Inputs.checkbox', 'Checkbox input'],
  ['Inputs.toggle', 'Boolean toggle'],
  ['Inputs.button', 'Action button'],
  ['Inputs.table', 'Display a table'],
  ['FileAttachment', 'Read a notebook or shared resource'],
  ['Pocket', 'App language, locale, clock and scoped timers'],
  ['Pocket.language', 'Current app language: ru or en'],
  ['Pocket.theme', 'Current app theme: light or dark'],
  ['Pocket.dark', 'Whether the app is using dark theme'],
  ['Pocket.now', 'Current date and time as Date'],
  ['Pocket.timer', 'Countdown timer scoped to the current notebook'],
  ['pocketNow', 'Reactive current date and time'],
  ['Nexus', 'Offline Nexus 0.1.20 core API'],
  ['Nexus.Dispatcher', 'Independent transactional dispatcher'],
  ['Nexus.Handler', 'Base class for transactional reactions'],
  ['Nexus.Store', 'Transactional state handle'],
  ['Nexus.Event', 'Transactional event source'],
  ['Nexus.Scope', 'Owner of stores, handlers and cleanup callbacks'],
  ['nexus', 'Nexus host scoped to the current preview'],
  ['nexus.version', 'Bundled Nexus version'],
  ['nexus.dispatcher', 'Dispatcher shared by this notebook'],
  ['nexus.scope', 'Create a scope; pass invalidation for cell cleanup'],
  ['nexus.observe', 'Reactive Store values: nexus.observe(scope, store)'],
  ['width', 'Reactive notebook width'],
  ['invalidation', 'Cell cancellation promise']
];

export function observableCompletionSource(context) {
  const token = context.matchBefore(/[\w$.]*/);
  if (!token || (token.from === token.to && !context.explicit)) return null;
  const prefix = token.text.toLowerCase();
  const options = observableSymbols
    .filter(([name]) => name.toLowerCase().startsWith(prefix))
    .map(([label, detail]) => ({label, type: label.includes('.') ? 'property' : 'function', detail}));
  if (!options.length) return null;
  return {from: token.from, options, validFor: /^[\w$.]*$/};
}

