import type Lenis from 'lenis';
let active: Lenis | null = null;

/** One bridge for anchors, style swaps and browser reading restoration. */
export function connectScroller(engine: Lenis) {
  active = engine;
  return () => { if (active === engine) active = null; };
}
export function jumpTo(top: number) {
  const target = Math.max(0, top);
  if (active) { active.resize(); active.scrollTo(target, { immediate: true, force: true }); }
  else window.scrollTo({ top: target, behavior: 'instant' });
}
export function scrollToPosition(top: number, reduced = false) {
  if (reduced) { jumpTo(top); return; }
  if (active) active.scrollTo(Math.max(0, top), { duration: .85, easing: t => 1 - Math.pow(1 - t, 4) });
  else window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
}
