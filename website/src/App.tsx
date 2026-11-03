import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { ArrowUpRight, ArrowDown, Check, Download, Plus, X, Menu, Music2, Disc3, Users, Folder, AudioLines, Moon, Sun, Monitor, Play, Pause, Heart } from 'lucide-react';
import { sitePath } from './site-path.js';
import { useI18n, LanguageToggle } from './i18n';
import { installReloadReadingRestore } from './motion.js';
import { styles, screenshot, createStyleCoordinator, readSavedStyle, saveStyle } from './style-state.js';
import { sceneState, styleKeys } from './scene-state';
import type { VisualStyle } from './scene-state';
import { useExperience, usePreference } from './use-experience';
import { preserveReadingPosition } from './reading-position';
import { scrollToPosition } from './scroll-controller';
import ListeningLab from './ListeningLab';

import HeroGallery from './HeroGallery';
import EditionPlayer from './EditionPlayer';
const repository = 'https://github.com/cmdr-chara/Songlass';
const release = `${repository}/releases/latest`;
const editionCopy = {
  liquid_glass: { title: 'La luce si fa spazio.', body: 'Comandi essenziali, profondità leggera. Il vetro tiene gli strumenti sopra la musica.', short: 'Luce e profondità', traits: [['Il contenuto respira.', 'La copertina dà il tono al player. I comandi restano liberi, con una barra sottile e un piccolo cursore verticale.'], ['Gli strumenti galleggiano.', 'Una capsula traslucida raccoglie le azioni. La superficie lascia passare il paesaggio, mantenendo nitide le etichette.'], ['Un gesto, una risposta.', 'Le superfici accompagnano il movimento con un rilascio morbido e una pressione leggera.']] },
  material_design: { title: 'La forma segue il ritmo.', body: 'Superfici tonali e forme espressive. Anche il pulsante play cambia carattere quando lo tocchi.', short: 'Forma e carattere', traits: [['Le azioni prendono forma.', 'Play e pausa vivono in una forma ampia che cambia angoli. I comandi laterali hanno superfici tonali circolari.'], ['Il progresso ha un ritmo.', 'Durante l’ascolto, il tratto percorso diventa un’onda. In pausa e con movimento ridotto, torna una linea ferma.'], ['Una risposta elastica.', 'Le azioni importanti si assestano con una piccola molla. Gli strumenti si raccolgono in un gruppo connesso.']] },
  miuix: { title: 'Tutto al posto giusto.', body: 'Superfici neutre e strumenti ordinati. Un’interfaccia precisa, familiare, senza fretta.', short: 'Ordine e precisione', traits: [['Superfici tranquille.', 'La copertina resta al centro di un player neutro. Gruppi distinti e angoli continui danno ordine ai controlli.'], ['Controlli ben definiti.', 'Una barra più spessa, un cursore rotondo e un play ben riconoscibile. Gli altri comandi restano leggeri.'], ['Il movimento è vicino.', 'Selezioni rapide, una pressione contenuta e strumenti separati. Ogni gesto risponde nel punto in cui lo fai.']] },
};
const navigation = [['Interfacce', 'style_showcase'], ['Libreria', 'music_library'], ['Il suono', 'sound_control'], ['Informazioni', 'platform_details']];
const sectionIds = ['songlass_intro', 'style_showcase', 'appearance_options', 'music_library', 'playback_control', 'sound_control', 'lyrics_artwork', 'platform_details', 'songlass_footer'];
const faqs = [
  ['platform_answer', 'Su quali dispositivi posso usarla?', 'Songlass è in sviluppo per Android 13 e versioni successive. Il supporto iOS è sperimentale.'],
  ['network_answer', 'Serve un account o una connessione?', 'La riproduzione locale non richiede un account né un server musicale. Le ricerche online di testi e copertine possono contattare servizi esterni e sono attive inizialmente; il recupero delle copertine è limitato alle connessioni non a consumo.'],
  ['selector_answer', 'Il selettore modifica l’app installata?', 'Cambia lo stile del sito e le schermate della presentazione. Non modifica l’app sul dispositivo.'],
  ['shared_features_answer', 'Le funzioni cambiano con l’interfaccia?', 'Liquid Glass, Material 3 Expressive e Miuix condividono libreria e funzioni. Qui confronti il loro aspetto. La dicitura Material Glass nelle catture originali rimane invariata.'],
  ['demo_answer', 'Che cosa mostrano le anteprime?', 'Le schermate Android usano audio dimostrativo sintetico e copertine originali del progetto.'],
];
type ScreenType = 'player' | 'appearance';
type OpenPreview = (type: ScreenType, origin: HTMLElement) => void;

function Brand() {
  const { t } = useI18n();
  return <a className="brand-link" href="#songlass_intro" aria-label={t('Songlass, torna all’inizio')}><img src={sitePath('assets/brand/songlass-logo.svg')} alt="" width="36" height="36" /><span>Songlass</span></a>;
}
function DownloadLink({ className = '', children }: { className?: string; children?: ReactNode }) {
  const { t } = useI18n();
  return <a className={`action action-primary ${className}`} href={release}>{children ?? t('Scarica per Android')}<ArrowUpRight size={20} aria-hidden="true" /></a>;
}
function StylePicker({ edition, onChange, id, descriptive = false }: { edition: VisualStyle; onChange: (style: VisualStyle) => void; id: string; descriptive?: boolean }) {
  const { t } = useI18n();
  return <fieldset data-ui id={id === 'intro' ? 'style_selector' : id === 'footer' ? 'footer_style_selector' : 'showcase_style_selector'} className={`style-picker ${descriptive ? 'picker-index' : ''}`}>
    <legend>{descriptive ? t('Scegli la tua atmosfera') : t('Scegli l’interfaccia')}</legend>
    <div className="style-choices">{styleKeys.map(key => <label key={key} className={`style-option ${edition === key ? 'is-selected' : ''}`}>
      <input type="radio" data-style-choice name={`${id}-style`} id={`${id === 'intro' ? 'style' : `${id}_style`}_${key}`} value={key} checked={edition === key} onChange={event => { event.currentTarget.focus({ preventScroll: true }); onChange(key); }} />
      <span className={`choice-material choice-material--${key}`} aria-hidden="true"><span /></span><span className="choice-content"><span className="choice-name">{styles[key].label}</span>{descriptive && <span className="choice-description">{t(editionCopy[key].short)}</span>}</span>
      <span className="choice-sign" aria-hidden="true">{edition === key ? <Check size={20} /> : <ArrowUpRight size={20} />}</span>
    </label>)}</div>
  </fieldset>;
}
function Header({ menu, setMenu, theme, setTheme }: { menu: boolean; setMenu: (open: boolean) => void; theme: string; setTheme: (value: string) => void }) {
  const { t } = useI18n();
  const systemDark = usePreference('(prefers-color-scheme: dark)');
  const dark = theme === 'dark' || (theme === 'system' && systemDark);
  return <header className="site-header"><div className="header-inner"><Brand /><nav className="desktop-nav" aria-label={t('Navigazione principale')}>{navigation.map(([label, id]) => <a href={`#${id}`} key={id}>{t(label)}</a>)}</nav><div className="header-actions"><LanguageToggle /><button className="theme-toggle" type="button" onClick={() => setTheme(dark ? 'light' : 'dark')} aria-label={t(dark ? 'Passa al tema chiaro' : 'Passa al tema scuro')}>{dark ? <Sun size={19} aria-hidden="true" /> : <Moon size={19} aria-hidden="true" />}</button><a className="header-download" href={release}>{t('Scarica')}<Download size={17} aria-hidden="true" /></a><button className="menu-toggle" onClick={() => setMenu(!menu)} aria-expanded={menu} aria-controls="mobile-navigation" aria-label={t(menu ? 'Chiudi il menu' : 'Apri il menu')}>{menu ? <X /> : <Menu />}</button></div></div>
    <nav id="mobile-navigation" className="mobile-navigation" aria-label={t('Navigazione mobile')} hidden={!menu}>{navigation.map(([label, id]) => <a key={id} href={`#${id}`} onClick={() => setMenu(false)}>{t(label)}<ArrowUpRight size={22} aria-hidden="true" /></a>)}<a href={release}>{t('Scarica per Android')}<Download size={22} aria-hidden="true" /></a></nav>
  </header>;
}
function Screen({ edition, type, onOpen, className = '' }: { edition: VisualStyle; type: ScreenType; onOpen: OpenPreview; className?: string }) {
  const { t } = useI18n();
  const label = type === 'player' ? t('Esplora il player') : t('Esplora l’aspetto');
  return <figure className={`screen-figure ${className}`} data-reveal="media"><a className="screen-frame" href={screenshot(edition, type)} aria-label={`${label} · ${styles[edition].label}`} onClick={event => { if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return; event.preventDefault(); onOpen(type, event.currentTarget); }}><img data-screen={type} src={screenshot(edition, type).replace('.png', '.webp')} width="1080" height="2400" alt={`${type === 'player' ? t('Player di Songlass') : t('Impostazioni Aspetto')} ${styles[edition].label}`} loading="lazy" decoding="async" /><span className="screen-zoom" aria-hidden="true"><ArrowUpRight size={22} /></span></a><figcaption>{label}<span>{styles[edition].label}</span></figcaption></figure>;
}
function HeroArtwork() {
  const scene = 'liquid-glass';
  return <div className="hero-art-plane" aria-hidden="true"><picture><source media="(max-width: 740px)" srcSet={sitePath(`assets/footer/${scene}-hero-mobile.webp`)} /><img src={sitePath(`assets/footer/${scene}-hero.webp`)} width="1280" height="720" alt="" decoding="async" fetchPriority="high" /></picture></div>;
}

function EditionLandscape({ edition, className }: { edition: VisualStyle; className: string }) {
  return <div className={className} data-landscape={edition} aria-hidden="true">
    <img data-landscape-image="liquid" src={sitePath('assets/footer/liquid-glass-landscape.webp')} alt="" width="1672" height="941" loading="lazy" />
    <img data-landscape-image="miuix" src={sitePath('assets/footer/miuix-landscape.webp')} alt="" width="1672" height="941" loading="lazy" />
  </div>;
}
function ScreenshotReference({ edition, type, onOpen }: { edition: VisualStyle; type: ScreenType; onOpen: OpenPreview }) {
  const { t } = useI18n();
  return <a className="screenshot-reference" href={screenshot(edition, type)} onClick={event => { if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return; event.preventDefault(); onOpen(type, event.currentTarget); }}><img src={screenshot(edition, type).replace('.png', '.webp')} width="1080" height="2400" alt="" loading="lazy" /><span>{t(type === 'player' ? 'Player nell’app' : 'Aspetto nell’app')}<ArrowUpRight size={18} aria-hidden="true" /></span></a>;
}
function Library({ reducedMotion }: { reducedMotion: boolean }) {
  const { t } = useI18n();
  const [view, setView] = useState(0);
  const horizontal = usePreference('(max-width: 640px)');
  useEffect(() => {
    if (reducedMotion) return;
    const panel = document.querySelector<HTMLElement>('.demo-collection');
    const animation = panel?.animate([{ opacity: .65, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 300, easing: 'cubic-bezier(.2,.8,.2,1)' });
    return () => animation?.cancel();
  }, [view, reducedMotion]);
  const entries = [
    { name: 'Brani', icon: Music2, text: 'La tua musica, traccia per traccia. Ritrova un brano e aggiungilo al prossimo ascolto.' },
    { name: 'Album', icon: Disc3, text: 'Ascolta un disco dall’inizio alla fine. Le copertine aiutano a ritrovare quello che cerchi.' },
    { name: 'Artisti', icon: Users, text: 'Riunisci la musica dei tuoi artisti e passa da un album all’altro.' },
    { name: 'Generi', icon: AudioLines, text: 'Esplora la raccolta per genere e segui il suono del momento.' },
    { name: 'Cartelle', icon: Folder, text: 'Ritrova la struttura dei file sul dispositivo. La tua organizzazione rimane il punto di partenza.' },
  ];
  const artwork = [sitePath('assets/brand/songlass-logo.svg'), sitePath('assets/footer/liquid-glass-landscape.webp'), sitePath('assets/footer/miuix-landscape.webp')];
  const titles = ['Morning Signal', 'Glass Horizons', 'After Hours'];
  const rows = view === 2 ? ['Songlass Demo'] : view === 3 ? ['Ambient', 'Electronic'] : view === 4 ? ['Music / Demo Sessions', 'Music / Sound Studies'] : titles;
  return <div className="library-layout"><div className="library-copy"><div className="library-index" role="tablist" aria-label={t('Viste della libreria')} aria-orientation={horizontal ? 'horizontal' : 'vertical'}>{entries.map((entry, i) => <button role="tab" key={entry.name} id={`library-tab-${i}`} aria-controls="library-view" aria-selected={view === i} tabIndex={view === i ? 0 : -1} onClick={() => setView(i)} onKeyDown={event => { let next; if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (i + 1) % entries.length; else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (i + entries.length - 1) % entries.length; else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = entries.length - 1; else return; event.preventDefault(); setView(next); document.getElementById(`library-tab-${next}`)?.focus(); }}><entry.icon size={26} aria-hidden="true" /><span>{t(entry.name)}</span><ArrowUpRight size={24} aria-hidden="true" /></button>)}</div><p className="library-explanation">{t(entries[view].text)}</p></div>
    <div className="library-preview" data-ui data-reveal="media" id="library-view" role="tabpanel" aria-labelledby={`library-tab-${view}`} tabIndex={0}><div className="library-preview-heading"><h3>{t(entries[view].name)}</h3><span>{t('Raccolta dimostrativa')}</span></div><div className={`demo-collection ${view === 1 ? 'album-collection' : ''}`}>{rows.map((name, i) => <div key={name} className="demo-track"><img src={artwork[i % artwork.length]} width="96" height="96" alt="" loading="lazy" /><div><strong>{name}</strong><span>{view > 1 ? t('Libreria locale') : 'Songlass Demo'}</span></div>{view < 2 && <span className="track-duration">{['1:00', '0:48', '1:12'][i]}</span>}</div>)}</div><div className="demo-now-playing"><img src={artwork[0]} alt="" width="48" height="48" loading="lazy" /><div><strong>Morning Signal</strong><span>Songlass Demo</span></div><a href="#sound_control" aria-label={t('Ascolta lo studio sonoro')}><Play size={19} fill="currentColor" aria-hidden="true" /></a></div></div>
  </div>;
}
function AppearancePreferences({ theme, setTheme, calm, setCalm, solid, setSolid }: { theme: string; setTheme: (value: string) => void; calm: boolean; setCalm: (value: boolean) => void; solid: boolean; setSolid: (value: boolean) => void }) {
  const { t } = useI18n();
  return <div className="site-preferences" data-ui><fieldset><legend>{t('Prova il tema del sito')}</legend><div className="theme-choices">{[['system', 'Sistema', Monitor], ['light', 'Chiaro', Sun], ['dark', 'Scuro', Moon]].map(([value, label, Icon]) => { const ThemeIcon = Icon as typeof Sun; return <label key={value as string}><input type="radio" name="site-theme" value={value as string} checked={theme === value} onChange={() => setTheme(value as string)} /><ThemeIcon size={20} aria-hidden="true" /><span>{t(label as string)}</span></label>; })}</div></fieldset><label className="motion-control"><span>{t('Movimento ridotto sul sito')}</span><input type="checkbox" checked={calm} onChange={event => setCalm(event.target.checked)} /><span className="switch-track" aria-hidden="true" /></label><label className="motion-control"><span>{t('Superfici opache sul sito')}</span><input type="checkbox" checked={solid} onChange={event => setSolid(event.target.checked)} /><span className="switch-track" aria-hidden="true" /></label></div>;
}
function PlaybackArt() {
  const { t } = useI18n();
  const [animate, setAnimate] = useState(true);
  return <div className="playback-art" data-reveal="media" data-animate={animate} data-ui><div className="wave-flow" aria-hidden="true">{Array.from({ length: 54 }, (_, i) => <span key={i} style={{ '--bar': .18 + .72 * Math.abs(Math.sin(i * .29)) * (.55 + .45 * Math.cos(i * .14)), '--delay': `${-i * .075}s` } as CSSProperties} />)}</div><div className="flow-labels"><span>Morning Signal</span><span>Glass Horizons</span></div><div className="flow-bottom"><span>{t('Un ascolto senza interruzioni.')}</span><button onClick={() => setAnimate(!animate)} aria-label={t(animate ? 'Ferma l’animazione' : 'Avvia l’animazione')}>{animate ? <Pause size={20} /> : <Play size={20} />}</button></div></div>;
}
function Footer({ edition, selection, choose }: { edition: VisualStyle; selection: VisualStyle; choose: (style: VisualStyle) => void }) {
  const { t } = useI18n();
  return <footer id="songlass_footer" className="site-footer closing-footer" data-scroll-scene="footer">
    <EditionLandscape edition={edition} className="closing-landscape" />
    <div className="closing-top"><div className="closing-copy"><h2>{t('Fai spazio')}<br />{t('alla tua musica.')}</h2><p>{t('Un lettore locale. Un mondo tutto tuo.')}</p><DownloadLink /><span className="release-note">{t('Android 13+ · In sviluppo')}</span></div><div className="closing-picker"><StylePicker edition={selection} onChange={choose} id="footer" /></div></div>
    <div className="closing-bottom"><a className="closing-brand" href="#songlass_intro"><img src={sitePath('assets/brand/songlass-logo.svg')} alt="" width="44" height="44" /><span>Songlass</span></a><nav className="closing-links" aria-label={t('Link del progetto')}><a href={repository}>GitHub<ArrowUpRight size={16} aria-hidden="true" /></a><a href={`${repository}/blob/main/docs/PRIVACY.md`}>{t('Privacy')}<ArrowUpRight size={16} aria-hidden="true" /></a><a href={`${repository}/blob/main/LICENSE`}>{t('Licenza')}<ArrowUpRight size={16} aria-hidden="true" /></a><a href="#songlass_intro">{t('Torna all’inizio')}<ArrowUpRight size={16} aria-hidden="true" /></a></nav></div>
    <div className="closing-meta"><span>© 2026 Songlass</span><span>{t('Sorgente consultabile. Musica personale.')}</span><span>{t('iOS sperimentale.')}</span></div>
  </footer>;
}

export default function App() {
  const { t } = useI18n();
  const [edition, setEdition] = useState<VisualStyle>('liquid_glass');
  const [selection, setSelection] = useState<VisualStyle>('liquid_glass');
  const [menu, setMenu] = useState(false);
  const [previewType, setPreviewType] = useState<ScreenType>('player');
  const [announcement, setAnnouncement] = useState('');
  const [theme, setTheme] = useState('system');
  const [playerAppearance, setPlayerAppearance] = useState<'light' | 'dark'>('dark');
  const [calm, setCalm] = useState(false);
  const [solid, setSolid] = useState(false);
  const reducedTransparency = usePreference('(prefers-reduced-transparency: reduce)') || solid;
  const dialog = useRef<HTMLDialogElement>(null);
  const origin = useRef<HTMLElement | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const active = useRef<VisualStyle>('liquid_glass');
  const reduced = usePreference('(prefers-reduced-motion: reduce)') || calm;
  const prepared = useRef(new Map<VisualStyle, Promise<void>>());
  const cancelReadingRestore = useRef<(() => void) | undefined>(undefined);
  useExperience(reduced);
  useEffect(() => () => cancelReadingRestore.current?.(), []);
  const choose = useMemo(() => createStyleCoordinator(async (value: VisualStyle) => {
    let task = prepared.current.get(value);
    if (!task) {
      const landscape = sitePath(value === 'miuix' ? 'assets/footer/miuix-landscape.webp' : 'assets/footer/liquid-glass-landscape.webp');
      task = Promise.all([screenshot(value, 'player').replace('.png', '.webp'), screenshot(value, 'appearance').replace('.png', '.webp'), landscape].map(src => new Promise<void>((resolve, reject) => { const image = new Image(); image.src = src; image.decode().then(resolve).catch(reject); }))).then(() => {});
      prepared.current.set(value, task);
      void task.catch(() => prepared.current.delete(value));
    }
    return task;
  }, (value: VisualStyle) => {
    cancelReadingRestore.current?.();
    cancelReadingRestore.current = preserveReadingPosition(() => { active.current = value; document.documentElement.dataset.style = value; flushSync(() => setEdition(value)); });
    saveStyle(value); setSelection(value); setAnnouncement(value); document.documentElement.dataset.styleReady = 'true';
  }, () => { setSelection(active.current); document.documentElement.dataset.styleReady = 'true'; setAnnouncement('La schermata non è stata caricata. Puoi riprovare.'); }, 'liquid_glass'), []);
  const requestEdition = useCallback((value: VisualStyle) => {
    setSelection(value);
    document.documentElement.dataset.styleReady = 'false';
    void choose(value);
  }, [choose]);
  useLayoutEffect(() => {
    const saved = readSavedStyle() as VisualStyle;
    active.current = saved; setEdition(saved); setSelection(saved); document.documentElement.dataset.style = saved; document.documentElement.dataset.styleReady = 'true';
    try { const storedTheme = localStorage.getItem('songlass-site-theme'); if (['light', 'dark', 'system'].includes(storedTheme ?? '')) setTheme(storedTheme!); setCalm(localStorage.getItem('songlass-site-calm') === 'true'); setSolid(localStorage.getItem('songlass-site-solid') === 'true'); } catch { /* Preferences are optional. */ }
  }, []);
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.motion = reduced ? 'reduced' : 'full';
    document.documentElement.dataset.transparency = reducedTransparency ? 'reduced' : 'full';
    try { localStorage.setItem('songlass-site-theme', theme); localStorage.setItem('songlass-site-calm', String(calm)); localStorage.setItem('songlass-site-solid', String(solid)); } catch { /* Keep the current session usable without storage. */ }
  }, [theme, calm, reduced, solid, reducedTransparency]);
  useEffect(() => {
    const desktop = matchMedia('(min-width: 1000px)');
    const closeOnDesktop = () => {
      if (!desktop.matches || !menu) return;
      const focused = document.activeElement;
      if (document.getElementById('mobile-navigation')?.contains(focused) || focused?.matches('.menu-toggle')) document.querySelector<HTMLAnchorElement>('.desktop-nav a')?.focus({ preventScroll: true });
      setMenu(false);
    };
    desktop.addEventListener('change', closeOnDesktop);
    return () => desktop.removeEventListener('change', closeOnDesktop);
  }, [menu]);
  const openPreview = useCallback<OpenPreview>((type, element) => { origin.current = element; setPreviewType(type); dialog.current?.showModal(); }, []);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape' && menu) { setMenu(false); document.querySelector<HTMLButtonElement>('.menu-toggle')?.focus(); } };
    const onAnchor = (event: MouseEvent) => {
      const link = (event.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]');
      if (!link || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
      const target = document.getElementById(link.hash.slice(1)); if (!target) return;
      event.preventDefault(); cancelReadingRestore.current?.();
      if (target instanceof HTMLDetailsElement) target.open = true;
      const focus = target.matches('fieldset') ? target.querySelector<HTMLElement>('input:checked') : target.matches('details') ? target.querySelector<HTMLElement>('summary') : target;
      if (!focus?.matches('input,button,a,summary')) focus?.setAttribute('tabindex', '-1');
      history.pushState(null, '', link.hash);
      const top = Math.max(0, target.getBoundingClientRect().top + scrollY - 96);
      focus?.focus({ preventScroll: true });
      scrollToPosition(top, reduced);
    };
    document.addEventListener('keydown', onKey); document.addEventListener('click', onAnchor);
    const initial = document.getElementById(location.hash.slice(1)); if (initial instanceof HTMLDetailsElement) initial.open = true;
    return () => { document.removeEventListener('keydown', onKey); document.removeEventListener('click', onAnchor); };
  }, [menu, reduced]);
  useEffect(() => {
    let snapshot; try { snapshot = JSON.parse(sessionStorage.getItem('songlass-react-reading') ?? 'null'); } catch { /* Use browser restoration. */ }
    const cleanup = installReloadReadingRestore(snapshot, active.current);
    const save = () => { const id = sectionIds.find(id => { const element = document.getElementById(id); return element && element.getBoundingClientRect().bottom > 140; }) ?? sectionIds[0]; try { sessionStorage.setItem('songlass-react-reading', JSON.stringify({ url: location.pathname + location.search, width: innerWidth, style: active.current, scrollY, anchor: { id, top: document.getElementById(id)?.getBoundingClientRect().top ?? 0 } })); } catch { /* Optional persistence. */ } };
    addEventListener('pagehide', save); return () => { cleanup(); removeEventListener('pagehide', save); };
  }, []);
  return <div className="songlass-app" data-edition={edition} ref={root}><a className="skip-link" href="#main-content">{t('Vai al contenuto')}</a><Header menu={menu} setMenu={setMenu} theme={theme} setTheme={setTheme} /><main id="main-content">
    <section id="songlass_intro" className="hero" data-scroll-scene="hero" aria-labelledby="hero-heading"><HeroArtwork /><div className="hero-copy"><h1 id="hero-heading"><span>{t('Musica.')}</span><span>{t('A modo tuo.')}</span></h1><p className="hero-description">{t('I tuoi file. Il tuo suono. Il tuo spazio.')}<br />{t('Un lettore musicale locale per Android, con tre modi di essere te.')}</p><noscript><p className="no-script-note">{t('Le anteprime interattive richiedono JavaScript. Puoi comunque esplorare l’app e scaricare Songlass.')}</p></noscript><div className="hero-actions"><DownloadLink /><a className="text-link" href="#style_showcase">{t('Entra in ascolto')}<ArrowUpRight size={19} aria-hidden="true" /></a></div><p className="release-note">{t('Android 13+ · In sviluppo')}</p></div>
      <div className="hero-selector"><p className="hero-selector-note">{t('Stessa musica. Altro carattere.')}</p><StylePicker edition={selection} onChange={requestEdition} id="intro" /><a className="scroll-cue" href="#style_showcase" aria-label={t('Scopri Songlass')}><ArrowDown size={24} /><span>{t('Scopri Songlass')}</span></a></div>
      <div className="hero-visual" onPointerMove={event => { if (reduced || !matchMedia('(hover:hover) and (pointer:fine)').matches) return; const bounds = event.currentTarget.getBoundingClientRect(); sceneState.setPointer((event.clientX - bounds.left) / bounds.width - .5, (event.clientY - bounds.top) / bounds.height - .5); }} onPointerLeave={() => sceneState.setPointer(0, 0)}><div className="hero-stage"><HeroGallery edition={edition} reducedMotion={reduced} /></div><a className="hero-caption" href={screenshot(edition, 'player')} onClick={event => { if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return; event.preventDefault(); openPreview('player', event.currentTarget); }}><span>{styles[edition].label}</span><span>{t('Guarda il player')}<ArrowUpRight size={17} aria-hidden="true" /></span></a></div>
    </section>
    <section id="style_showcase" className="showcase section-shell" data-scroll-scene="showcase" aria-labelledby="showcase-heading">
      <div className="section-heading"><h2 id="showcase-heading" data-reveal="rise">{t('Un brano.')}<br />{t('Tre sensazioni.')}</h2><p data-reveal="rise">{t('Cambia interfaccia mentre ascolti. Il brano continua, ma ogni gesto ha un altro carattere.')}</p></div>
      <div className="showcase-layout">
        <div className="showcase-picker"><StylePicker edition={selection} onChange={requestEdition} id="showcase" descriptive /><div className="edition-statement"><h3>{t(editionCopy[edition].title)}</h3><p>{t(editionCopy[edition].body)}</p></div></div>
        <div className="showcase-sticky"><fieldset className="preview-theme-picker" data-ui><legend>{t('Aspetto dell’anteprima')}</legend><div>{(['light', 'dark'] as const).map(mode => <label key={mode}><input type="radio" name="player-appearance" value={mode} checked={playerAppearance === mode} onChange={() => setPlayerAppearance(mode)} />{mode === 'light' ? <Sun size={17} aria-hidden="true" /> : <Moon size={17} aria-hidden="true" />}<span>{t(mode === 'light' ? 'Chiaro' : 'Scuro')}</span></label>)}</div></fieldset><div className="showcase-player-stage"><EditionLandscape edition={edition} className="showcase-landscape" /><EditionPlayer edition={edition} reducedMotion={reduced} appearance={playerAppearance} /></div><p className="demo-caption">{t('Anteprima web interattiva con uno studio sonoro originale. Le schermate dell’app sono qui sotto.')}</p></div>
        <div className="edition-notes">{editionCopy[edition].traits.map(([title, copy], index) => <article className="edition-trait" data-reveal="rise" key={index}><h3>{t(title)}</h3><p>{t(copy)}</p></article>)}<div className="screenshot-references"><ScreenshotReference edition={edition} type="player" onOpen={openPreview} /><ScreenshotReference edition={edition} type="appearance" onOpen={openPreview} /></div><p className="media-note">{t('Schermate originali dell’app. Audio dimostrativo e copertine del progetto.')}</p></div>
      </div>
    </section>
    <section id="appearance_options" className="appearance-section section-shell" aria-labelledby="appearance-heading"><div className="appearance-gallery"><Screen edition={edition} type="appearance" onOpen={openPreview} /><div className="appearance-detail" data-ui><Sun size={32} aria-hidden="true" /><span>{t('Chiaro o scuro.')}</span><strong>Aa</strong><span>{t('Grande quanto serve.')}</span></div></div><div className="appearance-copy"><h2 id="appearance-heading" data-reveal="rise">{t('Ti somiglia.')}</h2><p className="section-lead" data-reveal="rise">{t('Scegli il tema, regola la dimensione del testo e ritrova un’interfaccia comoda per te.')}</p><AppearancePreferences theme={theme} setTheme={setTheme} calm={calm} setCalm={setCalm} solid={solid} setSolid={setSolid} /><div className="appearance-notes"><article><h3>{t('Meno movimento.')}</h3><p>{t('Riduci le animazioni mantenendo tutti i controlli disponibili.')}</p></article><article><h3>{t('Meno trasparenza.')}</h3><p>{t('Preferisci superfici più solide quando vuoi più chiarezza.')}</p></article></div></div></section>
    <section id="music_library" className="library-section section-shell" aria-labelledby="library-heading"><div className="section-heading"><h2 id="library-heading" data-reveal="rise">{t('La tua collezione.')}<br />{t('Ritrovata.')}</h2><p data-reveal="rise">{t('Dai file ai tuoi preferiti.')}<br />{t('Cinque modi per ritrovare un ascolto.')}</p></div><Library reducedMotion={reduced} /><div className="library-notes"><article id="playlist_details" data-reveal="rise"><h3>{t('Le tue playlist.')}</h3><p>{t('Crea raccolte intelligenti, ritrova preferiti e cronologia. Organizza gli ascolti anche con regole basate sulla valutazione dei brani.')}</p></article><article data-reveal="rise"><h3>{t('Pronte a partire.')}</h3><p>{t('Importa ed esporta le tue playlist in formato M3U.')}</p></article></div></section>
    <section id="playback_control" className="playback-section section-shell" aria-labelledby="playback-heading"><div className="section-heading"><h2 id="playback-heading" data-reveal="rise">{t('Resta nel ritmo.')}</h2><p data-reveal="rise">{t('Da una traccia alla prossima.')}<br />{t('Senza perdere il momento.')}</p></div><PlaybackArt /><div className="playback-groups"><article data-reveal="rise"><h3>{t('Gapless.')}</h3><p>{t('Un brano segue l’altro, senza pause aggiunte tra le tracce.')}</p></article><article data-reveal="rise"><h3>{t('Crossfade.')}</h3><p>{t('Scegli la dissolvenza tra i brani e configura le transizioni dei comandi.')}</p></article><article id="queue_details" data-reveal="rise"><h3>{t('La tua coda.')}</h3><p>{t('Riordina i prossimi brani, aggiungi tracce temporanee e salva la sequenza come playlist.')}</p></article></div></section>
    <section id="sound_control" className="sound-section section-shell" aria-labelledby="sound-heading"><div className="section-heading"><h2 id="sound-heading" data-reveal="rise">{t('Il tuo suono.')}<br />{t('Davvero tuo.')}</h2><p data-reveal="rise">{t('Le tue cuffie. Il tuo equilibrio.')}<br />{t('Ascolta lo studio sonoro e prova a cambiare il carattere del suono.')}</p></div><ListeningLab reducedMotion={reduced} /><div className="sound-tools"><article id="sound_equalizer" data-reveal="rise"><h3>{t('Equalizzatore')}</h3><p>{t('EQ grafico e profili parametrici con frequenza, guadagno e Q.')}</p></article><article data-reveal="rise"><h3>AutoEQ</h3><p>{t('Scegli il profilo per le tue cuffie.')}</p></article><article data-reveal="rise"><h3>ReplayGain</h3><p>{t('Gestisci i livelli di riproduzione dei brani.')}</p></article><article data-reveal="rise"><h3>{t('Uscite audio')}</h3><p>{t('Configura le impostazioni per le diverse uscite.')}</p></article></div></section>
    <section id="lyrics_artwork" className="lyrics-section section-shell" data-scroll-scene="lyrics" aria-labelledby="lyrics_details"><div className="lyrics-intro"><h2 id="lyrics_details" data-reveal="rise">{t('Ogni parola.')}<br />{t('Ogni copertina.')}</h2><div className="lyrics-copy"><article data-reveal="rise"><h3>{t('Segui il testo.')}</h3><p>{t('Importa i testi, correggi la sincronizzazione e segui le parole evidenziate quando sono disponibili i tempi di ogni parola.')}</p></article><article id="artwork_details" data-reveal="rise"><h3>{t('Riconosci i tuoi album.')}</h3><p>{t('Usa le copertine incorporate nei file o nelle cartelle. Cerca online quelle mancanti e scegli o sostituisci le immagini scaricate.')}</p></article></div></div><div className="lyrics-art" data-ui data-reveal="media"><img src={sitePath('assets/footer/liquid-glass-landscape.webp')} alt={t('Paesaggio immaginario di Songlass')} width="1672" height="941" loading="lazy" /><div className="lyric-lines"><p>{t('Abbassa il rumore.')}</p><p><strong>{t('Alza quello che senti.')}</strong></p><p>{t('Resta ancora un po’.')}</p></div><div className="lyric-credit"><span>{t('Versi originali per questa anteprima')}</span><Heart size={22} aria-hidden="true" /></div></div></section>
    <section id="platform_details" className="faq-section section-shell" aria-labelledby="faq-heading"><div className="faq-intro"><h2 id="faq-heading" data-reveal="rise">{t('Da sapere.')}</h2><p>{t('La musica resta tua.')}<br />{t('Qui trovi le risposte utili.')}</p><a className="text-link" href={repository}>{t('Esplora il progetto')}<ArrowUpRight size={20} aria-hidden="true" /></a></div><div className="faq-list">{faqs.map(([id, question, answer]) => <details key={id} id={id} open={id === 'platform_answer' ? true : undefined}><summary><span>{t(question)}</span><Plus size={24} className="disclosure-sign" aria-hidden="true" /></summary><div className="faq-answer"><p>{t(answer)}</p></div></details>)}</div></section>
  </main><Footer edition={edition} selection={selection} choose={requestEdition} /><dialog id="screen-preview" ref={dialog} aria-labelledby="preview-heading" onClose={() => { origin.current?.focus({ preventScroll: true }); }} onClick={event => { if (event.target === dialog.current || (event.target instanceof HTMLElement && event.target.classList.contains('preview-scroll'))) dialog.current?.close(); }}><div className="preview-toolbar"><h2 id="preview-heading">{t(previewType === 'player' ? 'Player' : 'Aspetto')} · {styles[edition].label}</h2><form method="dialog"><button aria-label={t('Chiudi la schermata')}><X size={24} /></button></form></div><div className="preview-scroll"><img src={screenshot(edition, previewType)} width="1080" height="2400" alt={`${t(previewType === 'player' ? 'Player di Songlass' : 'Impostazioni Aspetto')} ${styles[edition].label}`} /></div></dialog><p id="style-announcement" className="visually-hidden" aria-live="polite" aria-atomic="true">{announcement in styles ? `${t('Interfaccia selezionata:')} ${styles[announcement as VisualStyle].label}.` : t(announcement)}</p></div>;
}
