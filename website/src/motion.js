// Native reload restoration may run against fallback-font geometry. A matching
// page snapshot supplies one final correction, after its real fonts are ready.
export function readingRestoreTarget(snapshot, context, anchorDocumentTop) {
  if (!snapshot || context.navigationType !== 'reload' || context.hash ||
    snapshot.url !== context.url || snapshot.style !== context.style ||
    !Number.isFinite(snapshot.width) || snapshot.width <= 0 ||
    !Number.isFinite(snapshot.scrollY) || snapshot.scrollY < 0) return null;
  if (Math.abs(snapshot.width - context.width) <= 1) return snapshot.scrollY;
  if (!snapshot.anchor?.id || !Number.isFinite(snapshot.anchor.top) || !Number.isFinite(anchorDocumentTop)) return null;
  return Math.max(0, anchorDocumentTop - snapshot.anchor.top);
}

export function installReloadReadingRestore(snapshot, style) {
  const navigationType = performance.getEntriesByType('navigation')[0]?.type;
  const url = location.pathname + location.search;
  if (navigationType !== 'reload' || location.hash || snapshot?.url !== url) return () => {};
  let cancelled = false;
  let pageShown;
  const inputEvents = ['wheel', 'touchstart', 'pointerdown', 'keydown'];
  const release = () => {
    inputEvents.forEach(type => removeEventListener(type, cancel, true));
    removeEventListener('pageshow', pageShown);
  };
  const cancel = () => { cancelled = true; release(); };
  inputEvents.forEach(type => addEventListener(type, cancel, { capture: true, passive: true }));
  const shown = document.readyState === 'complete' ? Promise.resolve() : new Promise(resolve => {
    pageShown = () => resolve();
    addEventListener('pageshow', pageShown, { once: true });
  });
  void Promise.all([document.fonts.ready, shown]).then(() => new Promise(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(resolve));
  })).then(() => {
    if (cancelled) return;
    const anchor = snapshot.anchor?.id ? document.getElementById(snapshot.anchor.id) : undefined;
    const anchorTop = anchor ? anchor.getBoundingClientRect().top + scrollY : undefined;
    const target = readingRestoreTarget(snapshot, {
      navigationType,
      url: location.pathname + location.search,
      hash: location.hash,
      width: innerWidth,
      style: document.documentElement.dataset.style,
    }, anchorTop);
    if (style === document.documentElement.dataset.style && target !== null) {
      scrollTo({ top: target, behavior: 'instant' });
    }
    release();
  });
  return cancel;
}
