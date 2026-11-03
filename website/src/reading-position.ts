import { jumpTo } from './use-experience';

let pendingRestore: (() => void) | undefined;

/** Preserve a logical reading group while its controls change arrangement. */
export function preserveReadingPosition(update: () => void) {
  pendingRestore?.();
  const root = document.documentElement;
  const focus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const picker = focus?.closest<HTMLElement>('.style-picker');
  const hit = document.elementFromPoint(innerWidth * .3, Math.min(180, innerHeight * .3));
  const anchor = picker ?? hit?.closest<HTMLElement>('p,h2,article,section,footer');
  const before = anchor?.getBoundingClientRect().top ?? 0;
  const atTop = scrollY < 2;
  const bottomGap = Math.max(0, root.scrollHeight - innerHeight - scrollY);
  const atFooterEnd = Boolean(focus?.closest('#songlass_footer')) && bottomGap < 4;
  const footer = focus?.closest<HTMLElement>('#songlass_footer');
  const anchoring = root.style.overflowAnchor;
  let frame = 0;
  let finished = false;
  const cancel = () => {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(frame);
    root.style.overflowAnchor = anchoring;
    if (pendingRestore === cancel) pendingRestore = undefined;
    removeEventListener('wheel', cancel);
    removeEventListener('touchstart', cancel);
    removeEventListener('keydown', onKey);
  };
  const onKey = (event: KeyboardEvent) => {
    if (['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Home', 'End', ' '].includes(event.key)) cancel();
  };
  const restore = () => {
    const target = atTop ? 0 : atFooterEnd ? root.scrollHeight - innerHeight - bottomGap : anchor?.isConnected ? scrollY + anchor.getBoundingClientRect().top - before : scrollY;
    // Cancel any native anchor animation before restoring the reading group.
    jumpTo(target);
  };
  pendingRestore = cancel;
  root.style.overflowAnchor = 'none';
  footer?.style.removeProperty('min-height');
  try {
    update();
    restore();
    focus?.focus({ preventScroll: true });
  } catch (error) {
    cancel();
    throw error;
  }
  addEventListener('wheel', cancel, { passive: true });
  addEventListener('touchstart', cancel, { passive: true });
  addEventListener('keydown', onKey);
  // ResizeObserver and the fitted footer name may settle after React's commit.
  // Own only these two frames, and stop correcting as soon as the user scrolls.
  frame = requestAnimationFrame(() => {
    restore();
    frame = requestAnimationFrame(() => { restore(); cancel(); });
  });
  return cancel;
}
