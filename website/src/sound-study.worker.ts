import { createSoundStudyPcm } from './sound-study.ts';

// A one-shot module worker: no audio nodes, fetches, timers or startup synthesis.
const scope = globalThis as unknown as {
  onmessage: ((event: MessageEvent<{ sampleRate: number }>) => void) | null;
  postMessage: (message: unknown, transfer?: Transferable[]) => void;
  close: () => void;
};
scope.onmessage = event => {
  try {
    const pcm = createSoundStudyPcm(event.data.sampleRate);
    scope.postMessage(pcm, [pcm.left.buffer, pcm.right.buffer]);
  } catch {
    scope.postMessage({ error: 'Sound study generation failed' });
  } finally {
    scope.close();
  }
};
