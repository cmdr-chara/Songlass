import { useEffect, useSyncExternalStore } from 'react';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';
import { storyCopy } from './story-pose';
import { sceneState } from './scene-state';

gsap.registerPlugin(ScrollTrigger);
const noop = () => {};
export function usePreference(query: string) {
  return useSyncExternalStore(
    listener => { const m = matchMedia(query); m.addEventListener('change',listener); return () => m.removeEventListener('change',listener); },
    () => matchMedia(query).matches,
    () => false,
  );
}
export let scrollEngine: Lenis | undefined;
export function jumpTo(top: number) {
  if (scrollEngine) scrollEngine.scrollTo(Math.max(0,top), { immediate: true, force: true });
  else window.scrollTo({top:Math.max(0,top),behavior:'instant'});
}
export function useExperience() {
  useEffect(() => {
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const fine = matchMedia('(hover: hover) and (pointer: fine)');
    const mount = document.querySelector<HTMLElement>('.scene-mount');
    let parallaxFrame = 0;
    // Pointer tilt stays on the compositor; measured scroll progress drives
    // the shared WebGL scene separately, without React renders.
    const updateParallax = () => {
      parallaxFrame = 0;
      if (!mount || reduced.matches || !fine.matches || !sceneState.visible || document.hidden) return;
      const x = sceneState.pointerX;
      const y = sceneState.pointerY;
      mount.style.transform = `perspective(1100px) translate3d(${x*12}px,${y*8}px,0) rotateX(${-y*2}deg) rotateY(${x*3}deg)`;
    };
    const scheduleParallax = () => { if (!parallaxFrame) parallaxFrame = requestAnimationFrame(updateParallax); };
    sceneState.setPointer = (x,y) => { sceneState.pointerX=x;sceneState.pointerY=y;scheduleParallax(); };
    let cleanScroll = noop;
    const configure = () => {
      cleanScroll();
      scrollEngine = undefined;
      sceneState.reduced = reduced.matches;
      sceneState.storyEnabled=!reduced.matches&&innerWidth>=1000&&innerHeight>=700&&!document.documentElement.hasAttribute('data-large-text');
      cancelAnimationFrame(parallaxFrame);parallaxFrame=0;
      sceneState.pointerX=0;sceneState.pointerY=0;
      mount?.style.removeProperty('transform');
      document.documentElement.dataset.motion = reduced.matches ? 'reduced' : 'full';
      if (reduced.matches || !fine.matches) return;
      const lenis = new Lenis({ lerp:.095, smoothWheel:true, syncTouch:false, anchors:false });
      scrollEngine = lenis;
      const tick = (time:number) => lenis.raf(time*1000);
      lenis.on('scroll',ScrollTrigger.update);
      gsap.ticker.add(tick);
      document.documentElement.dataset.scrollEngine = 'lenis';
      cleanScroll = () => { gsap.ticker.remove(tick); lenis.destroy(); document.documentElement.dataset.scrollEngine = 'native'; };
    };
    configure(); reduced.addEventListener('change',configure); fine.addEventListener('change',configure);
    const owned: gsap.core.Animation[] = [];
    const finish = (el:HTMLElement) => { el.dataset.revealPlayed='true'; gsap.set(el,{clearProps:'transform,opacity,visibility'}); };
    const waiting = new Set<HTMLElement>();
    const observer = new IntersectionObserver(entries => {
      let stagger=0;
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const el = entry.target as HTMLElement;
        observer.unobserve(el); waiting.delete(el); el.dataset.revealPlayed='true';
        if (reduced.matches || entry.boundingClientRect.bottom<=0 || document.hidden) { finish(el); continue; }
        const type=el.dataset.reveal;
        const side=el.closest('.screen-figure')?.matches(':nth-child(2)')?1:-1;
        const tween=gsap.fromTo(el,{opacity:type==='media'?.78:.72,x:type==='slide'?side*12:0,y:type==='media'?16:type==='rise'?12:0},{opacity:1,x:0,y:0,duration:type==='media'?.74:.62,delay:Math.min(stagger,.24),ease:'power3.out',onComplete:()=>finish(el)});
        owned.push(tween); stagger+=.08;
        document.dispatchEvent(new CustomEvent('songlass-motion',{detail:{type:'reveal',kind:type,section:el.closest('section')?.id}}));
      }
    },{threshold:0,rootMargin:'0px 0px 96px 0px'});
    const restored = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming|undefined;
    document.querySelectorAll<HTMLElement>('[data-reveal]').forEach(el => {
      if (el.dataset.revealPlayed==='true') return;
      if (reduced.matches || restored?.type==='reload' || el.getBoundingClientRect().top<innerHeight) {finish(el);return;}
      // CSS is visible. Only future groups are prepared, and every owned state is released below.
      el.style.opacity='.72'; waiting.add(el); observer.observe(el);
    });
    const sweep = () => {
      for (const el of waiting) if (el.getBoundingClientRect().bottom<0) {observer.unobserve(el);waiting.delete(el);finish(el);}
    };
    const clearOwned = () => { observer.disconnect(); owned.forEach(a=>a.kill()); waiting.forEach(finish); waiting.clear(); document.querySelectorAll<HTMLElement>('[data-reveal]').forEach(finish); };
    const onReduced = () => { if (reduced.matches) { heroTween?.progress(1);heroTween?.kill();clearOwned(); sceneState.pointerX=0;sceneState.pointerY=0;sceneState.invalidate(); } };
    reduced.addEventListener('change',onReduced);
    addEventListener('scroll',sweep,{passive:true});
    // Animate native title lines so React can update their language without stale split wrappers.
    let heroTween:gsap.core.Tween|undefined;
    const title=document.querySelector<HTMLElement>('h1');
    if (title && !title.dataset.heroPlayed && !reduced.matches && scrollY<8 && !location.hash && restored?.type!=='reload') {
      title.dataset.heroPlayed='true';
      const lines=title.querySelectorAll(':scope > span');
      const elements=[...lines,...document.querySelectorAll<HTMLElement>('.hero-description,.hero-selector,.hero-stage')];
      heroTween=gsap.fromTo(elements,{y:index=>index<2?20:12,opacity:.78},{y:0,opacity:1,duration:.72,stagger:.04,ease:'power3.out',clearProps:'transform,opacity'});
      document.dispatchEvent(new CustomEvent('songlass-motion',{detail:{type:'hero'}}));
    }
    const hero=document.getElementById('songlass_intro');
    const compare=document.getElementById('style_showcase');
    const appearance=document.getElementById('appearance_options');
    const track=document.querySelector<HTMLElement>('.story-track');
    let measureFrame=0;let stations=[0,1,2];let storyEnd=0;
    const app=document.querySelector<HTMLElement>('[data-edition]');
    const updateStory=()=>{
      const y=scrollY;
      const segment=y<stations[1]?0:1;
      const progress=Math.max(0,Math.min(2,segment+(y-stations[segment])/Math.max(1,stations[segment+1]-stations[segment])));
      if(Math.abs(progress-sceneState.progress)>.00001){sceneState.progress=progress;sceneState.invalidate();}
      document.documentElement.dataset.storyScene=progress<.55?'player':progress<1.55?'compare':'appearance';
      const enhanced=sceneState.storyEnabled&&app?.dataset.sceneReady==='true';
      if(app)app.dataset.storyEnhanced=String(enhanced);
      const alphas=storyCopy(progress);
      [hero,compare,appearance].forEach((section,index)=>{
        if(!section)return;
        const alpha=y>=storyEnd?0:alphas[index];
        section.inert=Boolean(enhanced&&alpha<.98);
        if(enhanced&&alpha<.01)section.setAttribute('aria-hidden','true');else section.removeAttribute('aria-hidden');
        app?.style.setProperty(['--hero-alpha','--compare-alpha','--appearance-alpha'][index],String(alpha));
      });
      app?.style.setProperty('--story-exit',`${Math.max(0,y-stations[2])}px`);
    };
    const measureStory=()=>{
      measureFrame=0;
      if(!hero||!compare||!appearance||!track)return;
      stations=[hero.offsetTop,compare.offsetTop,appearance.offsetTop];
      const height=appearance.offsetTop+appearance.offsetHeight;storyEnd=height;
      if(track.style.height!==`${height}px`)track.style.height=`${height}px`;
      sceneState.storyEnabled=!reduced.matches&&innerWidth>=1000&&innerHeight>=700&&!document.documentElement.hasAttribute('data-large-text');
      updateStory();sceneState.invalidate();ScrollTrigger.refresh();
    };
    const scheduleMeasure=()=>{if(!measureFrame)measureFrame=requestAnimationFrame(measureStory);};
    const sizes=new ResizeObserver(scheduleMeasure);
    [hero,compare,appearance].forEach(el=>{if(el)sizes.observe(el);});
    const flags=new MutationObserver(scheduleMeasure);flags.observe(document.documentElement,{attributes:true,attributeFilter:['data-style','data-large-text']});if(app)flags.observe(app,{attributes:true,attributeFilter:['data-scene-ready']});
    addEventListener('scroll',updateStory,{passive:true});addEventListener('resize',scheduleMeasure);reduced.addEventListener('change',scheduleMeasure);
    void document.fonts.ready.then(scheduleMeasure);measureStory();
    return () => {cancelAnimationFrame(parallaxFrame);sceneState.setPointer=noop;mount?.style.removeProperty('transform');heroTween?.kill();cancelAnimationFrame(measureFrame);sizes.disconnect();flags.disconnect();[hero,compare,appearance].forEach(el=>{if(el){el.inert=false;el.removeAttribute('aria-hidden');}});removeEventListener('scroll',updateStory);removeEventListener('resize',scheduleMeasure);reduced.removeEventListener('change',scheduleMeasure);cleanScroll();scrollEngine=undefined;clearOwned();reduced.removeEventListener('change',configure);fine.removeEventListener('change',configure);reduced.removeEventListener('change',onReduced);removeEventListener('scroll',sweep);};
  },[]);
}
