import {sitePath} from './site-path.js';
export const styles = Object.freeze({
  liquid_glass: { label: 'Liquid Glass', file: 'liquid-glass' },
  material_design: { label: 'Material 3 Expressive', file: 'material-glass' },
  miuix: { label: 'Miuix', file: 'miuix' },
});

export const styleStorageKey = 'songlass-style';

export function readSavedStyle(storage) {
  try {
    const value = (storage ?? globalThis.localStorage)?.getItem(styleStorageKey);
    return Object.hasOwn(styles, value) ? value : 'liquid_glass';
  } catch {
    return 'liquid_glass';
  }
}

export function saveStyle(value, storage) {
  if (!Object.hasOwn(styles, value)) return false;
  try {
    (storage ?? globalThis.localStorage)?.setItem(styleStorageKey, value);
    return true;
  } catch {
    // Storage may be disabled; the current session still works normally.
    return false;
  }
}

export function screenshot(style, type) {
  if (!Object.hasOwn(styles, style) || !['player', 'appearance'].includes(type)) {
    throw new TypeError('Schermata non disponibile');
  }
  return sitePath(`/assets/screenshots/${styles[style].file}-${type}.png`);
}

// Only the latest decoded selection may commit, including when loading fails.
export function createStyleCoordinator(prepare, commit, recover, initialStyle = 'liquid_glass') {
  let revision = 0;
  let active = Object.hasOwn(styles, initialStyle) ? initialStyle : 'liquid_glass';
  return async (next) => {
    if (!Object.hasOwn(styles, next)) return false;
    const ownRevision = ++revision;
    try {
      await prepare(next);
      if (ownRevision !== revision) return false;
      commit(next);
      active = next;
      return true;
    } catch {
      if (ownRevision === revision) recover(active);
      return false;
    }
  };
}
