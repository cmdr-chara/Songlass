/** Resolve site-owned paths for a domain root or project Pages deployment. */
export function sitePath(path, base = import.meta.env?.BASE_URL ?? '/') {
  return `${base.replace(/\/?$/, '/')}${path.replace(/^\//, '')}`;
}
