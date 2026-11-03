import {sitePath} from './site-path.js';
import {useI18n,LanguageToggle} from './i18n';
import { Component, Suspense, lazy, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { useGSAP } from '@gsap/react';
import gsap from 'gsap';
import { LiquidGlass } from 'simple-liquid-glass';
import { Material3Provider, Button as ExpressiveButton, createTheme } from '@language-lit/material3-expressive';
import { ArrowUpRight, ArrowDown, ArrowLeft, ArrowRight, Check, ChevronRight, Plus, X, Menu, Music2, Disc3, Users, Folder, AudioLines, SlidersHorizontal, ListMusic, Repeat2 } from 'lucide-react';
import { installReloadReadingRestore } from './motion.js';
import { styles, screenshot, createStyleCoordinator, readSavedStyle, saveStyle } from './style-state.js';
import { sceneState, styleKeys } from './scene-state';
import type { VisualStyle } from './scene-state';
import { useExperience, usePreference, scrollEngine } from './use-experience';
import { preserveReadingPosition } from './reading-position';

const PlayerScene=lazy(()=>import('./PlayerScene'));
const materialTheme=createTheme({reference:{palette:{'primary-40':'#495d92','primary-80':'#b8c7fa','primary-90':'#dae2ff','primary-10':'#10204b','primary-20':'#182e60','primary-30':'#314574'},typeface:{brand:['Google Sans Flex','sans-serif'],plain:['Google Sans Flex','sans-serif']}}});
const editionCopy={
  liquid_glass:{description:'Luce, profondità. Tutto scorre.',body:'Controlli sospesi e superfici che lasciano respirare la musica.',short:'Luce e profondità'},
  material_design:{description:'Più forma. Più carattere.',body:'Forme morbide, contrasti tonali e azioni che si fanno riconoscere.',short:'Forma e carattere'},
  miuix:{description:'Il piacere delle cose semplici.',body:'Gruppi ordinati, spazi compatti e tutto dove te lo aspetti.',short:'Ordine e semplicità'},
};
const nav=[['Interfacce','style_showcase'],['Aspetto','appearance_options'],['Libreria','music_library'],['Informazioni','platform_details']];
const sections=['songlass_intro','style_showcase','appearance_options','music_library','playback_control','sound_control','lyrics_artwork','platform_details','songlass_footer'];
const faqs=[
  ['platform_answer','Su quali dispositivi posso usarla?','Songlass è in sviluppo per Android 13 e versioni successive. Il supporto iOS è sperimentale.'],
  ['network_answer','Serve un account o una connessione?','La riproduzione locale non richiede un account né un server musicale. Le ricerche online di testi e copertine possono contattare servizi esterni e sono attive inizialmente; il recupero delle copertine è limitato alle connessioni non a consumo.'],
  ['selector_answer','Il selettore modifica l’app installata?','Cambia lo stile del sito e le schermate della presentazione. Non modifica l’app sul dispositivo.'],
  ['shared_features_answer','Le funzioni cambiano con l’interfaccia?','Liquid Glass, Material 3 Expressive e Miuix condividono libreria e funzioni. Qui confronti il loro aspetto. La dicitura Material Glass nelle catture originali rimane invariata.'],
  ['demo_answer','Che cosa mostrano le anteprime?','Le schermate Android usano audio dimostrativo sintetico e copertine originali del progetto.'],
];
class SceneBoundary extends Component<{children:ReactNode;onFailure:()=>void},{failed:boolean}> {
  state={failed:false};
  static getDerivedStateFromError(){return {failed:true};}
  componentDidCatch(){document.documentElement.dataset.sceneFallback='true';this.props.onFailure();}
  render(){return this.state.failed?null:this.props.children;}
}
function MaterialSurface({edition,children,className,radius=32,backdrop,tone}:{edition:VisualStyle;children:ReactNode;className:string;radius?:number;backdrop?:string;tone?:'light'|'dark'}) {
  const solid=usePreference('(prefers-reduced-transparency: reduce)');
  const systemDark=usePreference('(prefers-color-scheme: dark)');
  const dark=tone?tone==='dark':systemDark;
  const regular=className==='navigation-material';
  const footer=className==='footer-selector-material';
  // Web glass uses a clear face and a narrow refractive edge.
  // This uses the installed browser renderer, not the native macOS Metal shader.
  return <div data-ui className={`material-surface ${className}`}><LiquidGlass className="glass-effect" effectMode={edition==='liquid_glass'&&!solid?'auto':'off'} material={regular?'frosted':'clear'} radius={radius} blur={regular?5:.7} frost={regular?.12:.02} saturation={108} displacementScale={12} lensProfile="material" lensOptions={{strength:.075,depth:.25,curvature:.06,bend:.6,bendWidth:.08,sheen:.6,sheenWidth:1,specular:1,glow:0,brightness:0}} glassColor={regular?(dark?'rgba(22,18,30,0.72)':'rgba(255,255,255,0.54)'):(footer?'rgba(255,255,255,0.05)':dark?'rgba(24,20,32,0.18)':'rgba(255,255,255,0.10)')} borderColor="rgba(255,255,255,0.30)" aberrationIntensity={0} backdropSelector={backdrop} quality="low" reveal="immediate" aria-hidden="true" onDiagnosticsChange={diagnostics=>{document.documentElement.dataset.glassRenderer=diagnostics.strategy;}}><span className="glass-filler"/></LiquidGlass><div className="material-content">{children}</div></div>;
}
function Brand(){const {t}=useI18n();return <a className="brand-link" href="#songlass_intro" aria-label={t("Songlass, torna all’inizio")}><img src={sitePath('/assets/brand/songlass-logo.svg')} alt="" width="40" height="40"/><span>Songlass</span></a>;}
function StylePicker({edition,onChange,id,descriptive=false}:{edition:VisualStyle;onChange:(style:VisualStyle)=>void;id:string;descriptive?:boolean}) {const {t}=useI18n();
  return <fieldset data-ui id={id==='intro'?'style_selector':id==='footer'?'footer_style_selector':'showcase_style_selector'} className={`style-picker ${descriptive?'picker-index':''}`}>
    <legend>{descriptive?t("Scegli la tua atmosfera"):t("Scegli l’interfaccia")}</legend>
    <div className="style-choices">
      {styleKeys.map((key)=><label className={`style-option ${edition===key?'is-selected':''}`} key={key}>
        <input type="radio" data-style-choice name={`${id}-style`} id={`${id==='intro'?'style':id+'_style'}_${key}`} value={key} checked={edition===key} onChange={event=>{event.currentTarget.focus({preventScroll:true});onChange(key);}}/>
        <span className="choice-content"><span className="choice-name">{styles[key].label}</span>{descriptive&&<span className="choice-description">{t(editionCopy[key].short)}</span>}</span>
        <span className="choice-sign" aria-hidden="true">{edition===key?<Check size={18} strokeWidth={2.6}/>:descriptive?<ChevronRight size={20}/>:<span className="choice-dot"/>}</span>

      </label>)}
    </div>
  </fieldset>;
}
function Screen({edition,type,onOpen,className='',reveal}:{edition:VisualStyle;type:'player'|'appearance';onOpen:(type:'player'|'appearance',el:HTMLElement)=>void;className?:string;reveal?:string}) {const {t}=useI18n();
  return <figure className={`screen-figure ${className}`}>
    <div className="screen-frame" data-reveal={reveal}><img data-screen={type} src={screenshot(edition,type)} width="1080" height="2400" alt={`${type==='player'?t("Player di Songlass"):t("Impostazioni Aspetto")} ${styles[edition].label}`} loading="lazy" decoding="async"/></div>
    <figcaption><a className="image-link" data-screen-link={type} href={screenshot(edition,type)} onClick={e=>{if(e.ctrlKey||e.metaKey||e.shiftKey||e.altKey)return;e.preventDefault();onOpen(type,e.currentTarget);}}>{type==='player'?t("Esplora il player"):t("Esplora l’aspetto")}<ArrowUpRight size={18} aria-hidden="true"/></a></figcaption>
  </figure>;
}
function Header({edition,onToggleMenu,menu}:{edition:VisualStyle;onToggleMenu:()=>void;menu:boolean}) {const {t}=useI18n();
  const header=useRef<HTMLElement>(null);
  const [tone,setTone]=useState<'light'|'dark'>('light');
  const [navigationState,setNavigationState]=useState<'default'|'footer'|'engaged'>('default');
  useLayoutEffect(()=>{
    let frame=0;
    const measure=()=>{
      frame=0;
      const elementHeader=header.current;
      const band=elementHeader?.getBoundingClientRect();if(!elementHeader||!band)return;
      const footer=document.getElementById('songlass_footer')?.getBoundingClientRect();
      const closing=Boolean(footer&&footer.top<innerHeight+96&&footer.bottom>0);
      const engaged=menu||elementHeader.contains(document.activeElement);
      setNavigationState(closing?(engaged?'engaged':'footer'):'default');
      if(edition==='material_design'){setTone(matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light');return;}
      const section=[...document.querySelectorAll<HTMLElement>('main>section')].find(el=>{const r=el.getBoundingClientRect();return r.top<=band.top+band.height/2&&r.bottom>band.top+band.height/2;});
      if(section&&['songlass_intro','style_showcase','appearance_options'].includes(section.id)&&elementHeader.closest('[data-story-enhanced=true]')&&innerWidth>=1000&&!matchMedia('(prefers-reduced-motion:reduce)').matches&&!document.documentElement.hasAttribute('data-large-text')){setTone('dark');return;}
      let element:HTMLElement|null=section??document.body;
      while(element){
        const channels=getComputedStyle(element).backgroundColor.match(/[\d.]+/g)?.map(Number);
        if(channels&&channels.length>=3&&(channels[3]??1)>.8){
          const rgb=channels.slice(0,3).map(c=>{const n=c/255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4;});
          setTone(rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722<.3?'dark':'light');break;
        }
        element=element.parentElement;
      }
    };
    const schedule=()=>{if(!frame)frame=requestAnimationFrame(measure);};
    const preference=matchMedia('(prefers-color-scheme: dark)');
    const footer=document.getElementById('songlass_footer');
    const observer=footer?new IntersectionObserver(schedule,{threshold:0,rootMargin:'0px 0px 96px 0px'}):undefined;
    if(footer)observer?.observe(footer);
    measure();addEventListener('scroll',schedule,{passive:true});addEventListener('resize',schedule);preference.addEventListener('change',schedule);
    document.addEventListener('focusin',schedule);document.addEventListener('focusout',schedule);
    return()=>{observer?.disconnect();cancelAnimationFrame(frame);removeEventListener('scroll',schedule);removeEventListener('resize',schedule);preference.removeEventListener('change',schedule);document.removeEventListener('focusin',schedule);document.removeEventListener('focusout',schedule);};
  },[edition,menu]);
  return <header className="site-header" ref={header} data-material-tone={tone} data-navigation-state={navigationState} aria-hidden={navigationState==='footer'||undefined} inert={navigationState==='footer'}>
    <MaterialSurface edition={edition} className="navigation-material" radius={38} tone={tone}>
      <div className="header-inner"><Brand/><nav className="desktop-nav" aria-label={t("Navigazione principale")}>{nav.map(([label,id])=><a key={id} href={`#${id}`}>{t(label)}</a>)}</nav><a href="#style_selector" className="nav-action">{t("Prova i tre stili")}<ArrowUpRight size={17} aria-hidden="true"/></a><LanguageToggle/><button className="menu-toggle" onClick={onToggleMenu} aria-expanded={menu} aria-controls="mobile-navigation" aria-label={menu?t("Chiudi il menu"):t("Apri il menu")}>{menu?<X size={24}/>:<Menu size={24}/>}</button></div>
    </MaterialSurface>
    <nav id="mobile-navigation" className="mobile-navigation" aria-label={t("Navigazione mobile")} hidden={!menu}>{nav.map(([label,id])=><a key={id} href={`#${id}`} onClick={onToggleMenu}>{t(label)}<ArrowUpRight size={20}/></a>)}</nav>
  </header>;
}
function Library(){const {t}=useI18n();
  const [view,setView]=useState(0);
  const entries=[{name:t("Brani"),icon:Music2,text:t("La tua musica, traccia per traccia. Ritrova un brano e aggiungilo al prossimo ascolto.")},{name:t("Album"),icon:Disc3,text:t("Ascolta un disco dall’inizio alla fine. Le copertine aiutano a ritrovare quello che cerchi.")},{name:t("Artisti"),icon:Users,text:t("Riunisci la musica dei tuoi artisti e passa da un album all’altro.")},{name:t("Generi"),icon:AudioLines,text:t("Esplora la raccolta per genere e segui il suono del momento.")},{name:t("Cartelle"),icon:Folder,text:t("Ritrova la struttura dei file sul dispositivo. La tua organizzazione rimane il punto di partenza.")}];
  return <div className="library-layout"><div className="library-index" role="tablist" aria-label={t("Viste della libreria")} aria-orientation="vertical">{entries.map((entry,i)=><button key={entry.name} role="tab" id={`library-tab-${i}`} aria-controls="library-view" aria-selected={i===view} tabIndex={i===view?0:-1} onClick={()=>setView(i)} onKeyDown={e=>{let next:number;if(e.key==='ArrowDown')next=(i+1)%5;else if(e.key==='ArrowUp')next=(i+4)%5;else if(e.key==='Home')next=0;else if(e.key==='End')next=4;else return;e.preventDefault();setView(next);document.getElementById(`library-tab-${next}`)?.focus();}}><entry.icon size={28} aria-hidden="true"/><span>{entry.name}</span><ArrowUpRight size={28} aria-hidden="true"/></button>)}</div><div className="library-content"><div id="library-view" role="tabpanel" aria-labelledby={`library-tab-${view}`} tabIndex={0} className="library-view"><h3>{entries[view].name},<br/>{t("a modo tuo.")}</h3><p>{entries[view].text}</p></div><div className="library-notes"><article id="playlist_details"><h3>{t("Le tue playlist.")}</h3><p>{t("Crea raccolte intelligenti, ritrova preferiti e cronologia. Organizza gli ascolti anche con regole basate sulla valutazione dei brani.")}</p></article><article><h3>{t("Pronte a partire.")}</h3><p>{t("Importa ed esporta le tue playlist in formato M3U.")}</p></article></div></div></div>;
}
function Footer({edition,choose}:{edition:VisualStyle;choose:(style:VisualStyle)=>void}){const {t}=useI18n();
  const signature=useRef<HTMLDivElement>(null);
  useLayoutEffect(()=>{
    const fit=()=>{
      const parent=signature.current;const word=parent?.querySelector<HTMLElement>('h2');
      if(!parent||!word||!parent.offsetWidth)return;
      const size=parseFloat(getComputedStyle(word).fontSize);const range=document.createRange();range.selectNodeContents(word);
      const width=range.getBoundingClientRect().width;
      const parentStyle=getComputedStyle(parent);
      const widthFit=(parent.clientWidth-parseFloat(parentStyle.paddingLeft)-parseFloat(parentStyle.paddingRight))*.94/width*size;
      const lineHeight=parseFloat(getComputedStyle(word).lineHeight)/size;
      const heightFit=(parent.clientHeight-parseFloat(parentStyle.paddingTop)-parseFloat(parentStyle.paddingBottom))/lineHeight;
      const next=Math.min(widthFit,heightFit);
      if(width&&Number.isFinite(next)&&Math.abs(next-size)>.25)word.style.setProperty('--wordmark-size',`${next}px`);
    };
    const observer=new ResizeObserver(fit);if(signature.current)observer.observe(signature.current);fit();addEventListener('resize',fit);void document.fonts.ready.then(fit);return()=>{observer.disconnect();removeEventListener('resize',fit);};
  },[edition]);
  const groups=[[t("Scopri"),[[t("Interfacce"),'style_showcase'],[t("Aspetto"),'appearance_options'],[t("Libreria"),'music_library'],[t("Playlist"),'playlist_details']]],[t("Ascolta"),[[t("Riproduzione"),'playback_control'],[t("Coda"),'queue_details'],[t("Audio"),'sound_control'],[t("Testi e copertine"),'lyrics_artwork']]],[t("Informazioni"),[[t("Piattaforme"),'platform_answer'],[t("Rete e account"),'network_answer'],[t("Contenuti demo"),'demo_answer'],[t("Torna all’inizio"),'songlass_intro']]]] as const;
  return <footer id="songlass_footer" className="site-footer">
    <div className="footer-art" aria-hidden="true"><img className="footer-landscape footer-landscape-liquid" data-footer-landscape="liquid_glass" src={sitePath('/assets/footer/liquid-glass-landscape.png')} width="1672" height="941" alt="" loading="lazy"/><img className="footer-landscape footer-landscape-miuix" data-footer-landscape="miuix" src={sitePath('/assets/footer/miuix-landscape.png')} width="1672" height="941" alt="" loading="lazy"/></div>
    <div className="footer-graphic" aria-hidden="true"><span/><span/><span/><span/><span/><span/></div>
    <div className="footer-body"><div className="footer-brand"><Brand/><p className="footer-description">{t("La musica sul tuo dispositivo.")}<br/>{t("Tre interfacce tra cui scegliere.")}</p><p className="footer-platforms"><span>{t("Android 13+ in sviluppo.")}</span>{' '}<span>{t("iOS sperimentale.")}</span></p><p className="footer-status">{t("Anteprima del sito · In sviluppo.")}</p><MaterialSurface edition={edition} className="footer-selector-material" radius={38} backdrop=".footer-landscape-liquid"><StylePicker edition={edition} onChange={choose} id="footer"/></MaterialSurface></div><div className="footer-navs">{groups.map(([name,links])=><nav key={name} aria-label={name}><h3>{name}</h3>{links.map(([label,id])=><a key={id} href={`#${id}`}>{t(label)}</a>)}</nav>)}</div></div>
    <div className="footer-signature" ref={signature}><h2 className="footer-wordmark">Songlass</h2></div>
  </footer>;
}
export default function App(){const {t}=useI18n();
  const [edition,setEdition]=useState<VisualStyle>('liquid_glass');
  const [menu,setMenu]=useState(false);
  const [comparisonView,setComparisonView]=useState<'player'|'appearance'>('player');
  const [sceneEnabled,setSceneEnabled]=useState(false);
  const [sceneReady,setSceneReady]=useState(false);
  const [previewType,setPreviewType]=useState<'player'|'appearance'>('player');
  const [announcement,setAnnouncement]=useState('');
  const dialog=useRef<HTMLDialogElement>(null);
  const origin=useRef<HTMLElement|null>(null);
  const hero=useRef<HTMLDivElement>(null);
  const root=useRef<HTMLDivElement>(null);
  const active=useRef<VisualStyle>('liquid_glass');
  const reduced=usePreference('(prefers-reduced-motion: reduce)');
  useExperience();
  const prepared=useRef(new Map<VisualStyle,Promise<void>>());
  const cancelReadingRestore=useRef<(()=>void)|undefined>(undefined);
  useEffect(()=>()=>cancelReadingRestore.current?.(),[]);
  const choose=useMemo(()=>createStyleCoordinator(async (value:VisualStyle)=>{
    let task=prepared.current.get(value);
    if(!task){
      const sources=[screenshot(value,'player'),screenshot(value,'appearance')];
      if(value==='liquid_glass')sources.push(sitePath('/assets/footer/liquid-glass-landscape.png'));
      if(value==='miuix')sources.push(sitePath('/assets/footer/miuix-landscape.png'));
      task=Promise.all(sources.map(src=>new Promise<void>((resolve,reject)=>{const img=new Image();img.src=src;img.decode().then(()=>resolve()).catch(reject);}))).then(()=>{});prepared.current.set(value,task);task.catch(()=>prepared.current.delete(value));
    }
    return task;
  },(value:VisualStyle)=>{
    cancelReadingRestore.current?.();
    cancelReadingRestore.current=preserveReadingPosition(()=>{
      active.current=value;document.documentElement.dataset.style=value;
      flushSync(()=>setEdition(value));
    });
    saveStyle(value);setAnnouncement(value);
    document.documentElement.dataset.styleReady='true';
    if(!matchMedia('(prefers-reduced-motion: reduce)').matches)gsap.fromTo('[data-screen]:not(.hero-fallback)',{opacity:.78},{opacity:1,duration:.22,overwrite:true,clearProps:'opacity'});
    sceneState.invalidate();
  },()=>setAnnouncement('La schermata non è stata caricata. Puoi riprovare.'),'liquid_glass'),[]);
  useLayoutEffect(()=>{
    const saved=readSavedStyle() as VisualStyle;active.current=saved;setEdition(saved);document.documentElement.dataset.style=saved;document.documentElement.dataset.styleReady='true';
  },[]);
  useEffect(()=>{
    const frame=requestAnimationFrame(()=>setSceneEnabled(true));return()=>cancelAnimationFrame(frame);
  },[]);
  useEffect(()=>{
    const header=document.querySelector<HTMLElement>('.site-header');const title=document.querySelector<HTMLElement>('h1');
    const measure=()=>{
      if(header)document.documentElement.style.setProperty('--nav-h',`${header.getBoundingClientRect().height}px`);
      if(title)document.documentElement.toggleAttribute('data-large-text',parseFloat(getComputedStyle(title).fontSize)>innerWidth*.16);
    };
    const observer=new ResizeObserver(measure);if(header)observer.observe(header);if(title)observer.observe(title);measure();addEventListener('resize',measure);return()=>{observer.disconnect();removeEventListener('resize',measure);};
  },[]);
  const onSceneReady=useCallback(()=>{setSceneReady(true);document.documentElement.dataset.webglReady='true';},[]);
  const onSceneFailure=useCallback(()=>{setSceneReady(false);document.documentElement.dataset.webglReady='false';},[]);
  useGSAP(()=>{
    const feedback=(event:PointerEvent)=>{
      if(matchMedia('(prefers-reduced-motion: reduce)').matches)return;
      const control=(event.target as HTMLElement).closest<HTMLElement>('.nav-action,.image-link,.style-option,.menu-toggle');
      const el=control?.matches('.style-option')?control.querySelector<HTMLElement>('.choice-content'):control;
      if(el)gsap.fromTo(el,{scale:.98},{scale:1,duration:.2,ease:'power2.out',overwrite:true,clearProps:'transform'});
    };
    root.current?.addEventListener('pointerdown',feedback);
    return()=>root.current?.removeEventListener('pointerdown',feedback);
  },{scope:root});
  const openPreview=useCallback((type:'player'|'appearance',el:HTMLElement)=>{origin.current=el;setPreviewType(type);scrollEngine?.stop();dialog.current?.showModal();},[]);
  useEffect(()=>{
    if(dialog.current?.open)scrollEngine?.stop();
  },[previewType]);
  useEffect(()=>{
    const onKey=(e:KeyboardEvent)=>{if(e.key==='Escape'&&menu){setMenu(false);document.querySelector<HTMLButtonElement>('.menu-toggle')?.focus();}};
    const onClick=(e:MouseEvent)=>{
      const link=(e.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]');if(!link||e.ctrlKey||e.metaKey||e.altKey||e.shiftKey)return;
      const target=document.getElementById(link.hash.slice(1));if(!target)return;e.preventDefault();cancelReadingRestore.current?.();
      if(target instanceof HTMLDetailsElement)target.open=true;
      const focus=target.matches('fieldset')?target.querySelector<HTMLElement>('input:checked'):target.matches('details')?target.querySelector<HTMLElement>('summary'):target;
      if(!focus?.matches('input,button,a,summary'))focus?.setAttribute('tabindex','-1');
      history.pushState(null,'',link.hash);
      focus?.focus({preventScroll:true});
      const story=target.closest<HTMLElement>('#songlass_intro,#style_showcase,#appearance_options');
      const enhanced=root.current?.dataset.storyEnhanced==='true';
      const top=story&&enhanced?story.offsetTop:target.getBoundingClientRect().top+scrollY-112;
      if(story&&enhanced)requestAnimationFrame(()=>{focus?.focus({preventScroll:true});});
      if(scrollEngine)scrollEngine.scrollTo(Math.max(0,top),{duration:.85,onComplete:()=>focus?.focus({preventScroll:true})});else window.scrollTo({top:Math.max(0,top),behavior:reduced||(story&&enhanced)?'instant':'smooth'});
    };
    document.addEventListener('keydown',onKey);document.addEventListener('click',onClick);
    const initial=document.getElementById(location.hash.slice(1));if(initial instanceof HTMLDetailsElement)initial.open=true;
    return()=>{document.removeEventListener('keydown',onKey);document.removeEventListener('click',onClick);};
  },[menu,reduced]);
  useEffect(()=>{
    let snapshot;try{snapshot=JSON.parse(sessionStorage.getItem('songlass-react-reading')??'null');}catch{/* native restoration */}
    const cleanupRestore=installReloadReadingRestore(snapshot,active.current);
    const save=()=>{
      const id=sections.find(id=>{const e=document.getElementById(id);return e&&e.getBoundingClientRect().bottom>140;})??'songlass_intro';
      try{sessionStorage.setItem('songlass-react-reading',JSON.stringify({url:location.pathname+location.search,width:innerWidth,style:active.current,scrollY,anchor:{id,top:document.getElementById(id)?.getBoundingClientRect().top??0}}));}catch{/* optional persistence */}
    };
    addEventListener('pagehide',save);return()=>{cleanupRestore();removeEventListener('pagehide',save);};
  },[]);
  const heroPointer=(event:React.PointerEvent)=>{
    if(reduced||!matchMedia('(hover: hover) and (pointer: fine)').matches)return;
    const rect=event.currentTarget.getBoundingClientRect();sceneState.setPointer((event.clientX-rect.left)/rect.width-.5,(event.clientY-rect.top)/rect.height-.5);
  };
  return <Material3Provider theme={materialTheme} colorMode="system" className="songlass-app">
    <div ref={root} data-edition={edition} data-scene-ready={sceneReady}>
    <a className="skip-link" href="#main-content">{t("Vai al contenuto")}</a>
    <div className="page-chapters"><Header edition={edition} menu={menu} onToggleMenu={()=>setMenu(!menu)}/>
    <main id="main-content">
      <div className="story-track" aria-hidden="true"><div className="story-pin"><div className={`hero-stage ${sceneReady?'scene-ready':''}`}><div className="scene-mount">{sceneEnabled&&<SceneBoundary onFailure={onSceneFailure}><Suspense fallback={null}><PlayerScene edition={edition} onReady={onSceneReady}/></Suspense></SceneBoundary>}</div></div></div></div>
      <section id="songlass_intro" className="hero" aria-labelledby="hero-heading" ref={hero} onPointerMove={heroPointer} onPointerLeave={()=>sceneState.setPointer(0,0)}>
        <div className="hero-atmosphere" aria-hidden="true"/><div className="hero-copy"><h1 id="hero-heading"><span>{t("La musica.")}</span><span>{t("A modo tuo.")}</span></h1><p className="hero-description">{t("Il tuo lettore musicale locale.")}<br/>{t("Tre interfacce. La stessa passione.")}</p></div>
        <div className="hero-product">
          {styleKeys.filter(key=>key!==edition).map((key,index)=><img key={key} className={`hero-side hero-side-${index===0?'left':'right'}`} src={screenshot(key,'player')} width="1080" height="2400" alt="" aria-hidden="true" decoding="async"/>)}
          <img className="hero-fallback" data-screen="player" src={screenshot(edition,'player')} width="1080" height="2400" alt={`${t("Player di Songlass")}, ${styles[edition].label}`} fetchPriority="high"/>
        </div>
        <div className="hero-selector"><MaterialSurface edition={edition} className="selector-material"><StylePicker edition={edition} onChange={choose} id="intro"/></MaterialSurface><a className="explore-link" href="#style_showcase">{t("Scopri Songlass")}<ArrowDown size={18}/></a><a className="stage-open image-link" href={screenshot(edition,'player')} onClick={e=>{e.preventDefault();openPreview('player',e.currentTarget);}}>{t("Guarda il player")}<ArrowUpRight size={18}/></a></div>
      </section>
      <section id="style_showcase" className="showcase section-shell" aria-labelledby="showcase-heading"><div className="section-heading"><h2 id="showcase-heading" data-reveal="rise">{t("Cambia atmosfera.")}</h2><p data-reveal="slide">{t("La stessa musica, un altro modo di viverla.")}<br/>{t("Scegli uno stile: anche questa pagina si trasforma.")}</p></div><div className="showcase-layout"><div className="showcase-picker"><StylePicker edition={edition} onChange={choose} id="showcase" descriptive/><div className="edition-statement"><h3>{t(editionCopy[edition].description)}</h3><p>{t(editionCopy[edition].body)}</p></div></div><div className="comparison-group" data-comparison-view={comparisonView}><div className="comparison-media" id="comparison-screens"><Screen edition={edition} type="player" onOpen={openPreview} reveal="slide"/><Screen edition={edition} type="appearance" onOpen={openPreview} reveal="media"/></div><div className="comparison-controls" data-ui><button aria-controls="comparison-screens" aria-pressed={comparisonView==='player'} onClick={()=>setComparisonView('player')}><ArrowLeft size={20} aria-hidden="true"/>{t("Player")}</button><button aria-controls="comparison-screens" aria-pressed={comparisonView==='appearance'} onClick={()=>setComparisonView('appearance')}>{t("Aspetto")}<ArrowRight size={20} aria-hidden="true"/></button></div></div></div><p className="media-note">{t("Schermate originali dell’app. Audio dimostrativo e copertine del progetto.")}</p></section>
      <section id="appearance_options" className="appearance-section section-shell" aria-labelledby="appearance-heading"><div className="appearance-copy"><h2 id="appearance-heading" data-reveal="rise">{t("Ti somiglia.")}</h2><p className="section-lead" data-reveal="slide">{t("Chiaro o scuro.")}<br/>{t("Grande quanto serve.")}</p><p>{t("Scegli il tema, regola la dimensione del testo e ritrova un’interfaccia comoda per te.")}</p><div className="appearance-notes"><article data-reveal="rise"><h3>{t("Meno movimento.")}</h3><p>{t("Riduci le animazioni mantenendo tutti i controlli disponibili.")}</p></article><article data-reveal="rise"><h3>{t("Meno trasparenza.")}</h3><p>{t("Preferisci superfici più solide quando vuoi più chiarezza.")}</p></article></div>{edition==='material_design'?<ExpressiveButton className="appearance-action" variant="tonal" size="large" trailingIcon={<ArrowUpRight size={20}/>} onClick={e=>openPreview('appearance',e.currentTarget)}>{t("Esplora l’aspetto")}</ExpressiveButton>:<button className="appearance-action native-action" onClick={e=>openPreview('appearance',e.currentTarget)}>{t("Esplora l’aspetto")}<ArrowUpRight size={20}/></button>}</div><Screen edition={edition} type="appearance" onOpen={openPreview} className="appearance-screen" reveal="media"/></section>
      <section id="music_library" className="library-section section-shell" aria-labelledby="library-heading"><div className="section-heading"><h2 id="library-heading" data-reveal="rise">{t("Tutta la tua musica.")}</h2><p data-reveal="slide">{t("Dai file ai tuoi preferiti.")}<br/>{t("Cinque modi per ritrovare un ascolto.")}</p></div><Library/></section>
      <section id="playback_control" className="playback-section section-shell" aria-labelledby="playback-heading"><h2 id="playback-heading" data-reveal="rise">{t("Lascia scorrere.")}</h2><div className="playback-groups"><article data-reveal="slide"><Repeat2 size={32} aria-hidden="true"/><h3>{t("Gapless.")}</h3><p>{t("Un brano segue l’altro, senza pause aggiunte tra le tracce.")}</p></article><article data-reveal="slide"><AudioLines size={32} aria-hidden="true"/><h3>{t("Crossfade.")}</h3><p>{t("Scegli la dissolvenza tra i brani e configura le transizioni dei comandi.")}</p></article><article id="queue_details" data-reveal="slide"><ListMusic size={32} aria-hidden="true"/><h3>{t("La tua coda.")}</h3><p>{t("Riordina i prossimi brani, aggiungi tracce temporanee e salva la sequenza come playlist.")}</p></article></div></section>
      <section id="sound_control" className="sound-section section-shell" aria-labelledby="sound-heading"><div className="sound-intro"><h2 id="sound-heading" data-reveal="rise">{t("Il tuo suono.")}</h2><p data-reveal="slide">{t("Le tue cuffie. Il tuo equilibrio.")}<br/>{t("Trova il modo in cui vuoi ascoltare.")}</p></div><div className="sound-tools"><article id="sound_equalizer" data-reveal="slide"><h3>{t("Equalizzatore")}</h3><p>{t("Configura il suono secondo le tue preferenze.")}</p><SlidersHorizontal size={26} aria-hidden="true"/></article><article data-reveal="slide"><h3>{t("AutoEQ")}</h3><p>{t("Scegli il profilo per le tue cuffie.")}</p></article><article data-reveal="slide"><h3>{t("ReplayGain")}</h3><p>{t("Gestisci i livelli di riproduzione dei brani.")}</p></article><article data-reveal="slide"><h3>{t("Uscite audio")}</h3><p>{t("Configura le impostazioni per le diverse uscite.")}</p></article></div></section>
      <section id="lyrics_artwork" className="lyrics-section section-shell" aria-labelledby="lyrics_details"><h2 id="lyrics_details" data-reveal="rise">{t("Ogni parola.")}<br/>{t("Ogni copertina.")}</h2><div className="lyrics-copy"><article data-reveal="slide"><h3>{t("Segui il testo.")}</h3><p>{t("Importa i testi, correggi la sincronizzazione e segui le parole evidenziate quando sono disponibili i tempi di ogni parola.")}</p></article><article id="artwork_details" data-reveal="slide"><h3>{t("Riconosci i tuoi album.")}</h3><p>{t("Usa le copertine incorporate nei file o nelle cartelle. Cerca online quelle mancanti e scegli o sostituisci le immagini scaricate.")}</p></article></div></section>
      <section id="platform_details" className="faq-section section-shell" aria-labelledby="faq-heading"><div className="faq-intro"><h2 id="faq-heading" data-reveal="rise">{t("Da sapere.")}</h2><p>{t("La musica resta tua.")}<br/>{t("Qui trovi le risposte utili.")}</p></div><div className="faq-list">{faqs.map(([id,q,a])=><details key={id} id={id} open={id==='platform_answer'?true:undefined}><summary><span>{t(q)}</span><Plus size={22} className="disclosure-sign" aria-hidden="true"/></summary><div className="faq-answer"><p>{t(a)}</p></div></details>)}</div></section>
    </main></div>
    <Footer edition={edition} choose={choose}/>
    <dialog id="screen-preview" ref={dialog} aria-labelledby="preview-heading" data-lenis-prevent onClose={()=>{scrollEngine?.start();origin.current?.focus({preventScroll:true});}} onClick={e=>{if(e.target===dialog.current||(e.target instanceof HTMLElement&&e.target.classList.contains("preview-scroll")))dialog.current?.close();}}><div className="preview-toolbar"><h2 id="preview-heading">{previewType==='player'?t("Player"):t("Aspetto")} · {styles[edition].label}</h2><form method="dialog"><button aria-label={t("Chiudi la schermata")}><X size={24}/></button></form></div><div className="preview-scroll" data-lenis-prevent><img src={screenshot(edition,previewType)} width="1080" height="2400" alt={`${previewType==='player'?t("Player di Songlass"):t("Impostazioni Aspetto")} ${styles[edition].label}`}/></div></dialog>
    <p id="style-announcement" className="visually-hidden" aria-live="polite" aria-atomic="true">{announcement in styles?`${t("Interfaccia selezionata:")} ${styles[announcement as VisualStyle].label}.`:t(announcement)}</p>
    </div>
  </Material3Provider>;
}
