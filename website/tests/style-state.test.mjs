import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createStyleCoordinator, screenshot, styles, readSavedStyle, saveStyle, styleStorageKey } from '../src/style-state.js';
import { readingRestoreTarget } from '../src/motion.js';

test('la selezione più recente prevale anche se la precedente si carica dopo', async () => {
  const pending = new Map(); const committed = [];
  const select = createStyleCoordinator(value => new Promise(resolve => pending.set(value, resolve)), value => committed.push(value), () => {});
  const first = select('material_design'); const last = select('miuix');
  pending.get('miuix')(); assert.equal(await last, true);
  pending.get('material_design')(); assert.equal(await first, false);
  assert.deepEqual(committed, ['miuix']);
});
test('un caricamento fallito conserva lo stile precedente e permette un nuovo tentativo', async () => {
  let fail = false; const recovered = []; const committed = [];
  const select = createStyleCoordinator(async () => { if (fail) throw new Error('offline'); }, value => committed.push(value), value => recovered.push(value));
  await select('miuix'); fail = true; assert.equal(await select('material_design'), false);
  assert.deepEqual(recovered, ['miuix']); fail = false; assert.equal(await select('material_design'), true);
  assert.deepEqual(committed, ['miuix', 'material_design']);
});
test('nomi e percorsi Material Design mantengono il binding material-glass', () => {
  assert.equal(styles.material_design.label, 'Material 3 Expressive');
  assert.equal(screenshot('material_design', 'appearance'), '/assets/screenshots/material-glass-appearance.png');
  assert.throws(() => screenshot('invented', 'player'), TypeError);
});

test('una preferenza valida persiste e i valori sconosciuti tornano a Liquid Glass', () => {
  const values = new Map();
  const storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) };
  assert.equal(readSavedStyle(storage), 'liquid_glass');
  assert.equal(saveStyle('miuix', storage), true);
  assert.equal(values.get(styleStorageKey), 'miuix');
  assert.equal(readSavedStyle(storage), 'miuix');
  assert.equal(saveStyle('unavailable', storage), false);
  assert.equal(readSavedStyle(storage), 'miuix');
  values.set(styleStorageKey, 'removed-style');
  assert.equal(readSavedStyle(storage), 'liquid_glass');
});

test('storage disabilitato non impedisce la selezione', () => {
  const blocked = { getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); } };
  assert.equal(readSavedStyle(blocked), 'liquid_glass');
  assert.equal(saveStyle('miuix', blocked), false);
});

test('un intento fallito più recente impedisce il commit tardivo di quello precedente', async () => {
  const pending = new Map(); const committed = []; const recovered = [];
  const select = createStyleCoordinator(value => new Promise((resolve, reject) => pending.set(value, { resolve, reject })), value => committed.push(value), value => recovered.push(value), 'miuix');
  const first = select('material_design'); const last = select('liquid_glass');
  pending.get('liquid_glass').reject(new Error('offline'));
  assert.equal(await last, false);
  pending.get('material_design').resolve();
  assert.equal(await first, false);
  assert.deepEqual(committed, []);
  assert.deepEqual(recovered, ['miuix']);
});

test('il reload ripristina lo scroll solo per la stessa pagina e lo stesso stile', () => {
  const saved = { url: '/?lang=it', width: 1440, style: 'miuix', scrollY: 4187, anchor: { id: 'music_library', top: 56 } };
  const context = { navigationType: 'reload', url: '/?lang=it', width: 1440, style: 'miuix', hash: '' };
  assert.equal(readingRestoreTarget(saved, context), 4187);
  for (const override of [{ navigationType: 'navigate' }, { navigationType: 'back_forward' }, { url: '/' }, { style: 'liquid_glass' }, { hash: '#sound_control' }]) {
    assert.equal(readingRestoreTarget(saved, { ...context, ...override }), null);
  }
  for (const invalid of [null, {}, { ...saved, scrollY: Infinity }, { ...saved, scrollY: -1 }, { ...saved, width: 0 }]) {
    assert.equal(readingRestoreTarget(invalid, context), null);
  }
});

test('un reload a larghezza diversa mantiene l’ancora disponibile senza riusare un offset obsoleto', () => {
  const saved = { url: '/', width: 1440, style: 'miuix', scrollY: 4187, anchor: { id: 'music_library', top: 56 } };
  const context = { navigationType: 'reload', url: '/', width: 390, style: 'miuix', hash: '' };
  assert.equal(readingRestoreTarget(saved, context), null);
  assert.equal(readingRestoreTarget(saved, context, 6242), 6186);
  assert.equal(readingRestoreTarget(saved, context, 24), 0);
  assert.equal(readingRestoreTarget({ ...saved, anchor: undefined }, context, 6242), null);
});
