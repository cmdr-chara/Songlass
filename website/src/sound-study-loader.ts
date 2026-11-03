import { createSoundStudyPcm, SOUND_STUDY_DURATION } from './sound-study.ts';
import type { SoundStudyPcm } from './sound-study.ts';

type BufferContext = Pick<AudioContext, 'sampleRate' | 'createBuffer' | 'state'>;
export type SoundStudyBufferBuilder = (context: BufferContext) => Promise<AudioBuffer>;
type PcmGenerator = (sampleRate: number) => Promise<SoundStudyPcm>;
type PcmWorker = Pick<Worker, 'onmessage' | 'onerror' | 'onmessageerror' | 'postMessage' | 'terminate'>;
type WorkerFactory = () => PcmWorker | null;

function createWorker(): PcmWorker | null {
  if (typeof Worker === 'undefined') return null;
  return new Worker(new URL('./sound-study.worker.ts', import.meta.url), { type: 'module', name: 'songlass-sound-study' });
}

function receivePcm(worker: PcmWorker, sampleRate: number): Promise<SoundStudyPcm> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (pcm?: SoundStudyPcm) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      worker.onmessage = null;
      worker.onerror = null;
      worker.onmessageerror = null;
      worker.terminate();
      if (pcm) resolve(pcm);
      else reject(new Error('Sound study worker unavailable'));
    };
    worker.onmessage = event => {
      const pcm = event.data as Partial<SoundStudyPcm> | undefined;
      const length = Math.round(sampleRate * SOUND_STUDY_DURATION);
      if (pcm?.sampleRate === sampleRate && pcm.left instanceof Float32Array && pcm.right instanceof Float32Array && pcm.left.length === length && pcm.right.length === length) finish(pcm as SoundStudyPcm);
      else finish();
    };
    worker.onerror = event => { event.preventDefault(); finish(); };
    worker.onmessageerror = () => finish();
    const timer = setTimeout(() => finish(), 10000);
    try { worker.postMessage({ sampleRate }); } catch { finish(); }
  });
}

/** Work begins only on demand. Blocked/unsupported workers retain a local fallback. */
export async function generateSoundStudyOffThread(sampleRate: number, factory: WorkerFactory = createWorker): Promise<SoundStudyPcm> {
  try {
    const worker = factory();
    if (worker) return await receivePcm(worker, sampleRate);
  } catch { /* The same original synthesis remains available without a worker. */ }
  // Yield the click task before the slower fallback. This is not a frame-time guarantee.
  await new Promise<void>(resolve => setTimeout(resolve, 0));
  return createSoundStudyPcm(sampleRate);
}

/** PCM is private to this cache. Every consumer receives a separate mutable buffer. */
export function createSoundStudyBufferLoader(generate: PcmGenerator = generateSoundStudyOffThread): SoundStudyBufferBuilder {
  const cache = new Map<number, Promise<SoundStudyPcm>>();
  return async context => {
    const rate = context.sampleRate;
    let pending = cache.get(rate);
    if (!pending) {
      pending = generate(rate);
      cache.set(rate, pending);
      const task = pending;
      void task.catch(() => { if (cache.get(rate) === task) cache.delete(rate); });
    }
    const pcm = await pending;
    if (context.state === 'closed') throw new Error('Sound study context was closed');
    const buffer = context.createBuffer(2, pcm.left.length, pcm.sampleRate);
    buffer.getChannelData(0).set(pcm.left);
    buffer.getChannelData(1).set(pcm.right);
    return buffer;
  };
}

// Creating the cache performs no worker, audio, network or PCM generation work.
export const loadSoundStudyBuffer = createSoundStudyBufferLoader();
