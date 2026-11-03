import { useCallback, useEffect, useRef, useState } from 'react';
import { Pause, Play, RotateCcw } from 'lucide-react';
import { useI18n } from './i18n';
import { SoundStudy, audioContextConstructor } from './audio-engine';
import './listening-lab.css';

type Bands = [number, number, number];
type Playback = 'idle' | 'loading' | 'playing' | 'paused';
const BAND_LABELS = ['Bassi', 'Medi', 'Alti'];
const FREQUENCIES = ['180 Hz', '1.2 kHz', '4.5 kHz'];
const PRESETS: { name: string; values: Bands }[] = [
  { name: 'Neutro', values: [0, 0, 0] },
  { name: 'Caldo', values: [5, -2, -3] },
  { name: 'Brillante', values: [-2, 1, 5] },
];
const BAR_COUNT = 48;
const restingScale = (index: number) =>
  0.12 + Math.sin(index * 0.67) ** 2 * 0.22 + Math.sin(index * 0.23) ** 2 * 0.36;

export default function ListeningLab({ reducedMotion = false }: { reducedMotion?: boolean }) {
  const { t } = useI18n();
  const [bands, setBands] = useState<Bands>([0, 0, 0]);
  const [playback, setPlayback] = useState<Playback>('idle');
  const [error, setError] = useState('');
  const root = useRef<HTMLDivElement>(null);
  const meter = useRef<HTMLDivElement>(null);
  const engine = useRef<SoundStudy | null>(null);
  const unsubscribe = useRef<(() => void) | undefined>(undefined);
  const operation = useRef(0);
  const visible = useRef(true);

  const pause = useCallback(() => {
    ++operation.current;
    engine.current?.pause();
    setPlayback(current => current === 'playing' || current === 'loading' ? 'paused' : current);
  }, []);

  useEffect(() => {
    const cancelPending = () => { ++operation.current; };
    const onVisibility = () => { if (document.hidden) pause(); };
    const observer = new IntersectionObserver(entries => {
      visible.current = entries[0].isIntersecting;
      if (!visible.current) pause();
    }, { threshold: 0 });
    if (root.current) observer.observe(root.current);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pagehide', pause);
    return () => {
      cancelPending();
      observer.disconnect();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('pagehide', pause);
      unsubscribe.current?.();
      unsubscribe.current = undefined;
      engine.current?.close();
      engine.current = null;
    };
  }, [pause]);

  useEffect(() => { engine.current?.setBands(bands); }, [bands]);

  useEffect(() => {
    const bars = [...(meter.current?.children ?? [])] as HTMLElement[];
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0;
    let lastFrame = 0;
    const levels = new Uint8Array(128);
    const rest = () => bars.forEach((bar, index) => bar.style.setProperty('--lab-level', String(restingScale(index))));
    const draw = (time: number) => {
      if (reducedMotion || preference.matches || !visible.current || document.hidden || !engine.current) return;
      if (time - lastFrame >= 40) {
        lastFrame = time;
        engine.current.analyser.getByteFrequencyData(levels);
        bars.forEach((bar, index) => {
          const bin = Math.min(127, Math.round(1 + (index / (BAR_COUNT - 1)) ** 1.55 * 100));
          bar.style.setProperty('--lab-level', String(0.06 + levels[bin] / 255 * 0.94));
        });
      }
      frame = requestAnimationFrame(draw);
    };
    const configure = () => {
      window.cancelAnimationFrame(frame);
      rest();
      if (playback === 'playing' && !reducedMotion && !preference.matches) frame = requestAnimationFrame(draw);
    };
    configure();
    preference.addEventListener('change', configure);
    return () => { window.cancelAnimationFrame(frame); preference.removeEventListener('change', configure); rest(); };
  }, [playback, reducedMotion]);

  const play = async () => {
    if (playback === 'playing') { pause(); return; }
    const Context = audioContextConstructor();
    if (!Context) { setError('Il tuo browser non supporta questa anteprima audio.'); return; }
    const currentOperation = ++operation.current;
    setError('');
    setPlayback('loading');
    try {
      if (!engine.current) {
        const study = new SoundStudy(Context);
        engine.current = study;
        unsubscribe.current = study.subscribe(state => setPlayback(state === 'closed' ? 'paused' : state));
      }
      engine.current.setBands(bands);
      const started = await engine.current.play();
      if (currentOperation !== operation.current) return;
      if (started && visible.current && !document.hidden) setPlayback('playing');
      else pause();
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

  const changeBand = (index: number, value: number) => setBands(current => current.map((gain, band) => band === index ? value : gain) as Bands);
  const buttonLabel = playback === 'loading' ? t('Preparazione...') : playback === 'playing' ? t('Pausa') : t('Ascolta');

  return <div className="listening-lab" data-ui data-playing={playback === 'playing'} data-reduced-motion={reducedMotion} ref={root}>
    <div className="listening-lab__heading">
      <div>
        <h3>{t('Studio sonoro')}</h3>
        <p>{t('Una composizione originale, nel tuo browser.')}</p>
      </div>
      <button type="button" className="listening-lab__play" onClick={() => void play()} disabled={playback === 'loading'} aria-busy={playback === 'loading'} aria-describedby="listening-lab-description">
        {playback === 'playing' ? <Pause size={21} fill="currentColor" aria-hidden="true" /> : <Play size={21} fill="currentColor" aria-hidden="true" />}
        <span>{buttonLabel}</span>
      </button>
    </div>
    <div className="listening-lab__spectrum" ref={meter} aria-hidden="true">
      {Array.from({ length: BAR_COUNT }, (_, index) => <span key={index} style={{ transform: `scaleY(var(--lab-level, ${restingScale(index)}))` }} />)}
    </div>
    <fieldset className="listening-lab__presets">
      <legend>{t('Scegli il carattere')}</legend>
      <div>
        {PRESETS.map(preset => <button type="button" key={preset.name} aria-pressed={bands.every((gain, index) => gain === preset.values[index])} onClick={() => setBands([...preset.values])}>{t(preset.name)}</button>)}
      </div>
    </fieldset>
    <div className="listening-lab__bands">
      {bands.map((value, index) => <div className="listening-lab__band" key={index}>
        <div className="listening-lab__band-label"><label htmlFor={`listening-lab-band-${index}`}>{t(BAND_LABELS[index])}</label><output htmlFor={`listening-lab-band-${index}`}>{value > 0 ? '+' : ''}{value} dB</output></div>
        <input id={`listening-lab-band-${index}`} type="range" min="-9" max="9" step="1" value={value} onChange={event => changeBand(index, Number(event.target.value))} aria-valuetext={`${value > 0 ? '+' : ''}${value} ${t('decibel')}`} />
        <span className="listening-lab__frequency">{FREQUENCIES[index]}</span>
      </div>)}
    </div>
    <div className="listening-lab__foot">
      <p id="listening-lab-description">{t('Premi Ascolta e regola le tre bande. È una demo web: le impostazioni dell’app non cambiano.')}</p>
      <button type="button" className="listening-lab__reset" onClick={() => setBands([0, 0, 0])} disabled={bands.every(gain => gain === 0)}><RotateCcw size={17} aria-hidden="true" />{t('Ripristina')}</button>
    </div>
    {error && <p className="listening-lab__error" role="status">{t(error)}</p>}
    <span className="visually-hidden" role="status" aria-atomic="true">{playback === 'loading' ? t('Preparazione...') : playback === 'playing' ? t('Anteprima in riproduzione.') : playback === 'paused' ? t('Anteprima in pausa.') : ''}</span>
  </div>;
}
