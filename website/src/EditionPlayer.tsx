import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { AudioLines, Heart, ListMusic, LoaderCircle, Pause, Play, RotateCcw, RotateCw, SlidersHorizontal } from 'lucide-react';
import { SoundStudy, audioContextConstructor } from './audio-engine';
import type { AudioPlayback } from './audio-engine';
import type { VisualStyle } from './scene-state';
import { useI18n } from './i18n';
import { sitePath } from './site-path.js';
import './edition-player.css';

type EditionPlayerProps = { edition: VisualStyle; reducedMotion: boolean; appearance?: 'light' | 'dark' };
const STUDY_DURATION = 12;
const wavePath = Array.from({ length: 31 }, (_, x) => `${x ? 'L' : 'M'}${x} ${(7 + Math.sin(x / 30 * Math.PI * 2) * 3.5).toFixed(3)}`).join(' ');
const timeLabel = (seconds: number) => `0:${Math.floor(Math.max(0, seconds)).toString().padStart(2, '0')}`;

/** One real sound study, one stable player, three native-inspired control systems. */
export default function EditionPlayer({ edition, reducedMotion, appearance }: EditionPlayerProps) {
  const { t } = useI18n();
  const id = `edition-player-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const [playback, setPlayback] = useState<AudioPlayback>('idle');
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(STUDY_DURATION);
  const [favorite, setFavorite] = useState(false);
  const [queueOpen, setQueueOpen] = useState(false);
  const [seeking, setSeeking] = useState(false);
  const [error, setError] = useState('');
  const root = useRef<HTMLDivElement>(null);
  const queueButton = useRef<HTMLButtonElement>(null);
  const engine = useRef<SoundStudy | null>(null);
  const unsubscribe = useRef<(() => void) | undefined>(undefined);
  const visible = useRef(true);
  const editing = useRef(false);
  const operation = useRef(0);
  const artwork = sitePath('assets/footer/liquid-glass-landscape.webp');
  const playing = playback === 'playing';

  const pause = useCallback(() => {
    ++operation.current;
    engine.current?.pause();
  }, []);

  const finishSeek = useCallback(() => {
    editing.current = false;
    setSeeking(false);
    if (engine.current) setPosition(engine.current.currentTime);
  }, []);

  useEffect(() => {
    const cancelPending = () => { ++operation.current; };
    const onHidden = () => { if (document.hidden) pause(); };
    const observer = new IntersectionObserver(entries => {
      visible.current = entries[0].isIntersecting;
      if (!visible.current) pause();
    }, { threshold: 0 });
    if (root.current) observer.observe(root.current);
    document.addEventListener('visibilitychange', onHidden);
    window.addEventListener('pagehide', pause);
    window.addEventListener('pointerup', finishSeek);
    window.addEventListener('pointercancel', finishSeek);
    return () => {
      cancelPending();
      observer.disconnect();
      document.removeEventListener('visibilitychange', onHidden);
      window.removeEventListener('pagehide', pause);
      window.removeEventListener('pointerup', finishSeek);
      window.removeEventListener('pointercancel', finishSeek);
      unsubscribe.current?.();
      engine.current?.close();
      engine.current = null;
    };
  }, [pause, finishSeek]);

  useEffect(() => {
    if (playback !== 'playing') return;
    // Eight clock reads per second while audible; no UI frame loop at rest.
    const interval = window.setInterval(() => {
      if (!editing.current && visible.current && !document.hidden && engine.current) setPosition(engine.current.currentTime);
    }, 125);
    return () => window.clearInterval(interval);
  }, [playback]);

  const togglePlayback = async () => {
    if (playback === 'playing' || playback === 'loading') { pause(); return; }
    const Context = audioContextConstructor();
    if (!Context) { setError('Il tuo browser non supporta questa anteprima audio.'); return; }
    const currentOperation = ++operation.current;
    setError('');
    setPlayback('loading');
    try {
      if (!engine.current) {
        const study = new SoundStudy(Context);
        study.seek(position);
        engine.current = study;
        setDuration(study.duration);
        unsubscribe.current = study.subscribe(state => {
          setPlayback(state);
          if (!editing.current) setPosition(study.currentTime);
        });
      }
      const started = await engine.current.play();
      if (currentOperation !== operation.current) return;
      if (!started || !visible.current || document.hidden) pause();
    } catch {
      unsubscribe.current?.();
      unsubscribe.current = undefined;
      engine.current?.close();
      engine.current = null;
      if (currentOperation === operation.current) {
        setPlayback('paused');
        setError('Non è stato possibile avviare l’audio. Riprova con il pulsante Ascolta.');
      }
    }
  };

  const seek = (seconds: number) => {
    const next = Math.max(0, Math.min(duration, seconds));
    setPosition(engine.current ? engine.current.seek(next) : next);
  };
  const beginSeek = () => { editing.current = true; setSeeking(true); };
  const progress = `${Math.max(0, Math.min(100, position / duration * 100))}%`;
  const playLabel = playback === 'loading' ? t('Preparazione...') : playing ? t('Pausa') : t('Ascolta');

  return <div className="edition-player" data-ui data-preview-theme={appearance} data-edition={edition} data-playing={playing} data-seeking={seeking} data-reduced-motion={reducedMotion} ref={root} role="group" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`} onKeyDown={event => {
    if (event.key === 'Escape' && queueOpen) {
      event.preventDefault(); event.stopPropagation(); setQueueOpen(false); queueButton.current?.focus({ preventScroll: true });
    }
  }}>
    <div className="edition-player__eyebrow"><AudioLines size={17} aria-hidden="true" /><span>{t('Studio sonoro')}</span><span className="edition-player__duration" aria-label={`${t('Durata')}: ${timeLabel(duration)}`}>{timeLabel(duration)}</span></div>
    <div className="edition-player__artwork"><img src={artwork} alt={t('Paesaggio immaginario di Songlass')} width="1672" height="941" loading="lazy" decoding="async" /></div>
    <div className="edition-player__metadata">
      <div><h3 id={`${id}-title`}>{t('Orizzonti di vetro')}</h3><p>Songlass <span aria-hidden="true">·</span> {t('Composizione originale')}</p></div>
      <button type="button" className="edition-player__favorite edition-player__interactive" aria-label={t('Preferito')} aria-pressed={favorite} onClick={() => setFavorite(current => !current)}><Heart size={25} fill={favorite ? 'currentColor' : 'none'} aria-hidden="true" /></button>
    </div>
    <div className="edition-player__seek edition-player__interactive" style={{ '--player-progress': progress } as CSSProperties}>
      <label className="visually-hidden" htmlFor={`${id}-seek`}>{t('Posizione del brano')}</label>
      <div className="edition-player__seek-control">
        <div className="edition-player__rail" aria-hidden="true"><span className="edition-player__remaining" /><span className="edition-player__played"><span className="edition-player__flat" /><svg className="edition-player__wave" width="100%" height="14" aria-hidden="true"><defs><pattern id={`${id}-wave`} x="0" y="0" width="30" height="14" patternUnits="userSpaceOnUse"><path d={wavePath} fill="none" stroke="currentColor" strokeWidth="5" strokeLinecap="round" /></pattern></defs><rect width="100%" height="14" fill={`url(#${id}-wave)`} /></svg></span></div>
        <input id={`${id}-seek`} type="range" min="0" max={duration} step="0.05" value={position} aria-valuetext={`${Math.round(position * 10) / 10} ${t('secondi su')} ${duration}`} onChange={event => seek(Number(event.target.value))} onPointerDown={beginSeek} onBlur={finishSeek} onKeyDown={event => { if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) beginSeek(); }} onKeyUp={finishSeek} />
      </div>
      <div className="edition-player__times" aria-hidden="true"><span>{timeLabel(position)}</span><span>{t('Studio in loop')}</span><span>−{timeLabel(Math.ceil(duration - position))}</span></div>
    </div>
    <div className="edition-player__transport edition-player__interactive" role="group" aria-label={t('Controlli di riproduzione')}>
      <button type="button" className="edition-player__skip" aria-label={t('Indietro di 3 secondi')} onClick={() => seek((engine.current?.currentTime ?? position) - 3)}><RotateCcw size={30} aria-hidden="true" /><span aria-hidden="true">3</span></button>
      <button type="button" className="edition-player__play" onClick={() => void togglePlayback()} aria-label={playLabel} aria-busy={playback === 'loading'} disabled={playback === 'loading'}>{playback === 'loading' ? <LoaderCircle className="edition-player__loading" aria-hidden="true" /> : playing ? <Pause fill="currentColor" aria-hidden="true" /> : <Play fill="currentColor" aria-hidden="true" />}</button>
      <button type="button" className="edition-player__skip" aria-label={t('Avanti di 3 secondi')} onClick={() => seek((engine.current?.currentTime ?? position) + 3)}><RotateCw size={30} aria-hidden="true" /><span aria-hidden="true">3</span></button>
    </div>
    <div className="edition-player__utilities edition-player__interactive">
      <button type="button" ref={queueButton} aria-expanded={queueOpen} aria-controls={`${id}-queue`} onClick={() => setQueueOpen(current => !current)}><ListMusic size={20} aria-hidden="true" /><span>{t('Coda')}</span></button>
      <a href="#sound_control"><SlidersHorizontal size={20} aria-hidden="true" /><span>{t('Equalizzatore')}</span></a>
    </div>
    <div id={`${id}-queue`} className="edition-player__queue edition-player__interactive" hidden={!queueOpen} role="region" aria-labelledby={`${id}-queue-title`}>
      <h4 id={`${id}-queue-title`}>{t('La tua coda')} <span>1</span></h4>
      <ol><li><img src={artwork} alt="" width="44" height="44" loading="lazy" /><div><strong>{t('Orizzonti di vetro')}</strong><span>{t('Un unico studio originale, in loop.')}</span></div><span>{timeLabel(duration)}</span></li></ol>
    </div>
    <noscript><style>{'.edition-player .edition-player__interactive{display:none!important}'}</style><p className="edition-player__noscript">{t('Attiva JavaScript per ascoltare lo studio sonoro.')}</p></noscript>
    <p className="visually-hidden" id={`${id}-description`}>{t('Audio originale. Prova il player, cambia interfaccia.')}</p>
    {error && <p className="edition-player__error" role="status">{t(error)}</p>}
    <span className="visually-hidden" role="status" aria-atomic="true">{playback === 'loading' ? t('Preparazione...') : playing ? t('Anteprima in riproduzione.') : playback === 'paused' ? t('Anteprima in pausa.') : ''}</span>
  </div>;
}
