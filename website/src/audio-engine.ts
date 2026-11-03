import { SOUND_STUDY_DURATION } from './sound-study.ts';
import { loadSoundStudyBuffer } from './sound-study-loader.ts';
import type { SoundStudyBufferBuilder } from './sound-study-loader.ts';

export type AudioPlayback = 'idle' | 'loading' | 'playing' | 'paused' | 'closed';
export type EqualizerBands = readonly [number, number, number];
type Voice = { source: AudioBufferSourceNode; gain: GainNode; stopping: boolean };

// A new play intent immediately relinquishes both audible and pending ownership.
// Keeping this coordinator outside React also covers two different demo components.
const playbackOwnership: { owner: SoundStudy | null } = { owner: null };
const playbackEngines = new Set<SoundStudy>();

export function audioContextConstructor(): typeof AudioContext | undefined {
  if (typeof window === 'undefined') return undefined;
  return window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
}

/** A seekable, opt-in original loop. Its clock is the actual audio context clock. */
export class SoundStudy {
  readonly context: AudioContext;
  readonly analyser: AnalyserNode;
  readonly duration = SOUND_STUDY_DURATION;
  private buffer: AudioBuffer | null = null;
  private bufferPending: Promise<AudioBuffer> | undefined;
  private readonly buildBuffer: SoundStudyBufferBuilder;
  private readonly filters: BiquadFilterNode[];
  private readonly limiter: DynamicsCompressorNode;
  private readonly volume: GainNode;
  private readonly voices = new Set<Voice>();
  private readonly listeners = new Set<(state: AudioPlayback) => void>();
  private stateValue: AudioPlayback = 'idle';
  private offset = 0;
  private startedAt = 0;
  private revision = 0;
  private suspendTimer: ReturnType<typeof setTimeout> | undefined;

  constructor(Context: typeof AudioContext, buildBuffer: SoundStudyBufferBuilder = loadSoundStudyBuffer) {
    this.buildBuffer = buildBuffer;
    this.context = new Context();
    try {
      this.filters = [180, 1200, 4500].map((frequency, index) => {
        const filter = this.context.createBiquadFilter();
        filter.type = index === 0 ? 'lowshelf' : index === 1 ? 'peaking' : 'highshelf';
        filter.frequency.value = frequency;
        filter.Q.value = 0.75;
        return filter;
      });
      this.limiter = this.context.createDynamicsCompressor();
      this.limiter.threshold.value = -16;
      this.limiter.knee.value = 18;
      this.limiter.ratio.value = 5;
      this.limiter.attack.value = 0.008;
      this.limiter.release.value = 0.18;
      this.volume = this.context.createGain();
      this.volume.gain.value = 0.27;
      this.analyser = this.context.createAnalyser();
      this.analyser.fftSize = 256;
      this.analyser.smoothingTimeConstant = 0.75;
      this.filters[0].connect(this.filters[1]);
      this.filters[1].connect(this.filters[2]);
      this.filters[2].connect(this.limiter);
      this.limiter.connect(this.analyser);
      this.analyser.connect(this.volume);
      this.volume.connect(this.context.destination);
      this.context.addEventListener('statechange', this.onContextState);
      playbackEngines.add(this);
    } catch (error) {
      void this.context.close().catch(() => {});
      throw error;
    }
  }

  get state(): AudioPlayback { return this.stateValue; }

  get currentTime(): number {
    if (this.stateValue !== 'playing') return this.offset;
    const elapsed = Math.max(0, this.context.currentTime - this.startedAt);
    return (this.offset + elapsed) % this.duration;
  }

  subscribe(listener: (state: AudioPlayback) => void) {
    this.listeners.add(listener);
    listener(this.stateValue);
    return () => { this.listeners.delete(listener); };
  }

  private notify() { this.listeners.forEach(listener => listener(this.stateValue)); }

  private readonly onContextState = () => {
    if (this.stateValue === 'playing' && this.context.state !== 'running') this.pause(true);
  };

  setBands(bands: EqualizerBands) {
    if (this.stateValue === 'closed') return;
    this.filters.forEach((filter, index) => {
      const gain = Number.isFinite(bands[index]) ? Math.max(-9, Math.min(9, bands[index])) : 0;
      filter.gain.setTargetAtTime(gain, this.context.currentTime, 0.025);
    });
  }

  private disposeVoice(voice: Voice) {
    voice.source.onended = null;
    voice.source.disconnect();
    voice.gain.disconnect();
    this.voices.delete(voice);
  }

  private stopVoices(immediate: boolean) {
    const now = this.context.currentTime;
    this.voices.forEach(voice => {
      if (voice.stopping && !immediate) return;
      voice.stopping = true;
      const gain = voice.gain.gain;
      if (typeof gain.cancelAndHoldAtTime === 'function') gain.cancelAndHoldAtTime(now);
      else gain.cancelScheduledValues(now);
      if (immediate) gain.setValueAtTime(0, now);
      else gain.setTargetAtTime(0, now, 0.006);
      voice.source.stop(now + (immediate ? 0 : 0.04));
      if (immediate) this.disposeVoice(voice);
    });
  }

  private startVoice() {
    if (!this.buffer) throw new Error('Sound study is not ready');
    const source = this.context.createBufferSource();
    const gain = this.context.createGain();
    const voice: Voice = { source, gain, stopping: false };
    source.buffer = this.buffer;
    source.loop = true;
    source.connect(gain);
    gain.connect(this.filters[0]);
    const now = this.context.currentTime;
    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(1, now + 0.035);
    source.onended = () => this.disposeVoice(voice);
    try {
      source.start(now, this.offset % this.duration);
      this.voices.add(voice);
      this.startedAt = now;
    } catch (error) {
      this.disposeVoice(voice);
      throw error;
    }
  }

  private suspendWhenIdle() {
    if (this.stateValue !== 'closed' && this.stateValue !== 'playing' && this.stateValue !== 'loading' && this.context.state === 'running') {
      void this.context.suspend().catch(() => {});
    }
  }

  private async prepareBuffer(): Promise<AudioBuffer> {
    if (this.buffer) return this.buffer;
    if (!this.bufferPending) {
      const task = this.buildBuffer(this.context).then(buffer => {
        if (this.stateValue === 'closed') throw new Error('Sound study was closed');
        this.buffer = buffer;
        return buffer;
      });
      this.bufferPending = task;
      void task.catch(() => { if (this.bufferPending === task) this.bufferPending = undefined; });
    }
    return this.bufferPending;
  }

  async play(): Promise<boolean> {
    if (this.stateValue === 'closed') return false;
    if (this.stateValue === 'playing') return true;
    const revision = ++this.revision;
    clearTimeout(this.suspendTimer);
    playbackEngines.forEach(study => {
      // Include an already-paused study whose short release tail may still sound.
      if (study !== this && study.state !== 'idle' && study.state !== 'closed') study.pause(true);
    });
    playbackOwnership.owner = this;
    this.stateValue = 'loading';
    this.notify();
    try {
      // Resume is invoked in the click call stack, before waiting on PCM work.
      const resuming = this.context.resume().then(() => {
        if (revision !== this.revision || playbackOwnership.owner !== this) this.suspendWhenIdle();
      });
      await Promise.all([resuming, this.prepareBuffer()]);
      if (revision !== this.revision || playbackOwnership.owner !== this) {
        // A late resume must not leave a hidden or superseded context running.
        this.suspendWhenIdle();
        return false;
      }
      if (this.context.state !== 'running') throw new Error('Audio context did not start');
      this.offset %= this.duration;
      this.startVoice();
      this.stateValue = 'playing';
      this.notify();
      return true;
    } catch (error) {
      if (revision !== this.revision || playbackOwnership.owner !== this) {
        this.suspendWhenIdle();
        return false;
      }
      this.pause(true);
      throw error;
    }
  }

  /** Immediate pause is used only for handoffs and system interruptions. */
  pause(immediate = false) {
    if (this.stateValue === 'closed') return;
    const position = this.currentTime;
    ++this.revision;
    this.offset = position;
    this.stopVoices(immediate);
    if (playbackOwnership.owner === this) playbackOwnership.owner = null;
    if (this.stateValue !== 'idle') this.stateValue = 'paused';
    this.notify();
    clearTimeout(this.suspendTimer);
    if (immediate) this.suspendWhenIdle();
    else this.suspendTimer = setTimeout(() => { this.suspendTimer = undefined; this.suspendWhenIdle(); }, 60);
  }

  /** Seeking never starts a paused study or claims playback from another demo. */
  seek(seconds: number): number {
    if (this.stateValue === 'closed' || !Number.isFinite(seconds)) return this.currentTime;
    this.offset = Math.max(0, Math.min(this.duration, seconds));
    if (this.stateValue === 'playing') {
      this.stopVoices(false);
      this.offset %= this.duration;
      this.startVoice();
    }
    this.notify();
    return this.currentTime;
  }

  close() {
    if (this.stateValue === 'closed') return;
    this.pause(true);
    this.stateValue = 'closed';
    playbackEngines.delete(this);
    this.buffer = null;
    this.bufferPending = undefined;
    clearTimeout(this.suspendTimer);
    this.context.removeEventListener('statechange', this.onContextState);
    [...this.filters, this.limiter, this.analyser, this.volume].forEach(node => node.disconnect());
    void this.context.close().catch(() => {});
    this.notify();
    this.listeners.clear();
  }
}
