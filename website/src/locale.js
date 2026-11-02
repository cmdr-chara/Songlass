/**
 * @param {readonly string[]} languages
 * @param {string | null | undefined} [saved]
 * @param {string | null | undefined} [explicit]
 */
export function chooseLocale(languages = [], saved, explicit) {
  if (['en', 'it'].includes(explicit)) return explicit;
  if (['en', 'it'].includes(saved)) return saved;
  for (const language of languages) {
    const base = String(language).toLowerCase().split(/[-_]/)[0];
    if (['en', 'it'].includes(base)) return base;
  }
  return 'en';
}
export function pathLocale(path, base = import.meta.env?.BASE_URL ?? '/') {
  if (!path.startsWith(base)) return undefined;
  return /^\/(en|it)(?:\/|$)/.exec('/' + path.slice(base.length))?.[1];
}
