import { useEffect, useSyncExternalStore } from 'react';
import gsap from 'gsap';
import Lenis from 'lenis';
import { connectScroller } from './scroll-controller';
export { jumpTo } from './scroll-controller';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { sceneState } from './scene-state';

gsap.registerPlugin(ScrollTrigger);

export function usePreference(query: string) {
  return useSyncExternalStore(
    listener => { const preference = matchMedia(query); preference.addEventListener('change', listener); return () => preference.removeEventListener('change', listener); },
    () => matchMedia(query).matches,
    () => false,
  );
}

/** Native scrolling; one GSAP clock for spatial progress and pointer response. */
export function useExperience(reduced = false) {
  useEffect(() => {
    const root = document.querySelector<HTMLElement>('.songlass-app');
    if (!root) return;
    const header = root.querySelector<HTMLElement>('.site-header');
    const mount = root.querySelector<HTMLElement>('.hero-stage');
    const fine = matchMedia('(hover:hover) and (pointer:fine)');
    const waves = [...root.querySelectorAll<HTMLElement>('.playback-art')];
    const links = [...root.querySelectorAll<HTMLAnchorElement>('.desktop-nav a[href^="#"]')];
    const observers: IntersectionObserver[] = [];
    const smooth = !reduced && matchMedia('(pointer:fine)').matches ? new Lenis({
      autoRaf: false, lerp: .11, smoothWheel: true, syncTouch: false,
      prevent: node => Boolean(node.closest('dialog,[data-native-scroll]')),
    }) : null;
    const disconnectScroller = smooth ? connectScroller(smooth) : () => {};
    const advanceScroll = (time: number) => smooth?.raf(time * 1000);
    if (smooth) { smooth.on('scroll', ScrollTrigger.update); gsap.ticker.add(advanceScroll); }
    document.documentElement.dataset.scrollEngine = smooth ? 'lenis' : 'native';
    const syncScrollLock = () => {
      if (!smooth) return;
      if (document.hidden || root.querySelector('dialog[open]')) smooth.stop();
      else smooth.start();
    };
    const dialogs = new MutationObserver(syncScrollLock);
    root.querySelectorAll('dialog').forEach(dialog => dialogs.observe(dialog, { attributes:true, attributeFilter:['open'] }));
    const context = gsap.context(() => {
      ScrollTrigger.create({ start: 0, end: 'max', onUpdate: self => {
        document.documentElement.dataset.scrolled = String(self.scroll() > 36);
      } });
      root.querySelectorAll<HTMLElement>('[data-scroll-scene]').forEach(element => {
        const name = element.dataset.scrollScene;
        if (reduced) return;
        gsap.fromTo(element, { [`--${name}-progress`]: 0 }, {
          [`--${name}-progress`]: 1, ease: 'none',
          scrollTrigger: { trigger: element, start: name === 'hero' ? 'top top' : 'top bottom', end: 'bottom top', scrub: .45, invalidateOnRefresh: true },
        });
      });
      links.forEach(link => {
        const section = document.getElementById(link.hash.slice(1));
        if (!section) return;
        ScrollTrigger.create({ trigger: section, start: 'top 45%', end: 'bottom 45%', onToggle: self => {
          if (self.isActive) link.setAttribute('aria-current', 'location');
          else link.removeAttribute('aria-current');
        } });
      });
      const footer = root.querySelector('#songlass_footer');
      if (footer && header) ScrollTrigger.create({ trigger: footer, start: 'top 25%', end: 'bottom top', onToggle: self => {
        header.dataset.footer = String(self.isActive);
        // Keep keyboard navigation available if a visitor focuses the header.
        if (header.contains(document.activeElement)) header.dataset.footer = 'false';
      } });
      if (mount && !reduced) {
        const rotateX = gsap.quickTo(mount, 'rotationX', { duration: .7, ease: 'power3.out' });
        const rotateY = gsap.quickTo(mount, 'rotationY', { duration: .7, ease: 'power3.out' });
        sceneState.setPointer = (x,y) => {
          if (!fine.matches || document.hidden) return;
          rotateX(-y * 5); rotateY(x * 8);
        };
      }
      const visibility = new IntersectionObserver(entries => {
        for (const entry of entries) (entry.target as HTMLElement).dataset.inView = String(entry.isIntersecting && !document.hidden);
      });
      waves.forEach(element => visibility.observe(element)); observers.push(visibility);
      if (!reduced) {
        const reveal = new IntersectionObserver(entries => {
          for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            const element = entry.target as HTMLElement;
            reveal.unobserve(element);
            if (element.dataset.revealPlayed) continue;
            element.dataset.revealPlayed = 'true';
            context.add(() => {
              const media = element.dataset.reveal === 'media';
              gsap.fromTo(element, { y: media ? 24 : 16, opacity: .7 }, {
                y: 0, opacity: 1, duration: media ? 1 : .7, ease: 'power3.out', clearProps: 'transform,opacity',
              });
            });
          }
        }, { threshold: .08 });
        root.querySelectorAll<HTMLElement>('[data-reveal]').forEach(element => reveal.observe(element));
        observers.push(reveal);
        const hero = root.querySelector<HTMLElement>('.hero-copy');
        if (hero && scrollY < 8 && !location.hash && !hero.dataset.revealPlayed) {
          hero.dataset.revealPlayed = 'true';
          gsap.fromTo(hero.querySelectorAll('h1 > span,.hero-description,.hero-actions'), { y: 26, opacity: .7 }, {
            y: 0, opacity: 1, duration: .9, stagger: .09, ease: 'power3.out', clearProps: 'transform,opacity',
          });
          gsap.fromTo('.hero-gallery', { y: 36, rotation: -4, opacity: .65 }, {
            y: 0, rotation: 0, opacity: 1, duration: 1.2, ease: 'power3.out', clearProps: 'transform,opacity',
          });
        }
        gsap.fromTo('.lyric-lines strong', { opacity: .5, y: 12 }, {
          opacity: 1, y: 0, ease: 'none', scrollTrigger: { trigger: '.lyrics-art', start: 'top 75%', end: 'center center', scrub: .4 },
        });
      }
    }, root);
    const onVisibility = () => { syncScrollLock(); waves.forEach(element => {
      const rect = element.getBoundingClientRect();
      element.dataset.inView = String(!document.hidden && rect.bottom > 0 && rect.top < innerHeight);
    }); };
    document.addEventListener('visibilitychange', onVisibility);
    void document.fonts.ready.then(() => { if (root.isConnected) ScrollTrigger.refresh(); });
    return () => {
      observers.forEach(observer => observer.disconnect());
      dialogs.disconnect();
      gsap.ticker.remove(advanceScroll);
      disconnectScroller();
      smooth?.destroy();
      document.documentElement.dataset.scrollEngine = 'native';
      document.removeEventListener('visibilitychange', onVisibility);
      sceneState.setPointer = () => {};
      if (header) delete header.dataset.footer;
      context.revert();
    };
  }, [reduced]);
}
