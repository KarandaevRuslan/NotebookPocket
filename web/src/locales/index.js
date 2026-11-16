/** WebView translations live in web resources and travel with the offline bundle. */
import english from './en.json';
import russian from './ru.json';
export function messagesFor(language) {
  return language === 'ru' ? russian : english;
}
export function formatMessage(value, replacements = {}) {
  return String(value).replace(/\{([a-z]+)\}/g, (_, key) => String(replacements[key] ?? ''));
}
