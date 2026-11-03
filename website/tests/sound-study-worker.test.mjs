import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { Worker as ThreadWorker } from 'node:worker_threads';
import { createSoundStudyPcm } from '../src/sound-study.ts';
import { createSoundStudyBufferLoader, generateSoundStudyOffThread } from '../src/sound-study-loader.ts';

const fingerprint = samples => createHash('sha256').update(new Uint8Array(samples.buffer, samples.byteOffset, samples.byteLength)).digest('hex');
function contextFor(sampleRate = 1000) {
  return {
    sampleRate, state: 'suspended', allocations: 0,
    createBuffer(numberOfChannels, length, rate) {
      this.allocations++;
      const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
      return { duration: length / rate, getChannelData: index => channels[index] };
    },
  };
}
function pcmFor(sampleRate) {
  return { sampleRate, left: new Float32Array(sampleRate * 12).fill(.125), right: new Float32Array(sampleRate * 12).fill(-.25) };
}

// This executes the actual worker module in a Node worker thread. The small
// bridge supplies message/transfer APIs, not a browser or a Web Audio mock.
for (const sampleRate of [44100, 48000]) {
  test(`the worker transfers unchanged stereo PCM at ${sampleRate} Hz`, { timeout: 10000 }, async () => {
    const moduleUrl = new URL('../src/sound-study.worker.ts', import.meta.url).href;
    const bridge = `
      import { parentPort } from 'node:worker_threads';
      globalThis.postMessage = (message, transfer) => parentPort.postMessage(message, transfer);
      globalThis.close = () => parentPort.close();
      await import(${JSON.stringify(moduleUrl)});
      parentPort.on('message', data => globalThis.onmessage({ data }));
    `;
    const worker = new ThreadWorker(new URL(`data:text/javascript,${encodeURIComponent(bridge)}`));
    try {
      const received = new Promise((resolve, reject) => { worker.once('message', resolve); worker.once('error', reject); });
      worker.postMessage({ sampleRate });
      const actual = await received;
      const expected = createSoundStudyPcm(sampleRate);
      assert.equal(actual.sampleRate, sampleRate);
      assert.equal(actual.left.length, sampleRate * 12);
      assert.equal(actual.right.length, sampleRate * 12);
      assert.equal(fingerprint(actual.left), fingerprint(expected.left));
      assert.equal(fingerprint(actual.right), fingerprint(expected.right));
    } finally { await worker.terminate(); }
  });
}

test('PCM cache is lazy, shares concurrent work by sample rate, and isolates mutable audio buffers', async () => {
  const rates = [];
  const load = createSoundStudyBufferLoader(async rate => { rates.push(rate); return pcmFor(rate); });
  assert.deepEqual(rates, []);
  const firstContext = contextFor(), secondContext = contextFor();
  const first = load(firstContext), second = load(secondContext);
  assert.deepEqual(rates, [1000]);
  const [one, two] = await Promise.all([first, second]);
  assert.notEqual(one, two);
  one.getChannelData(0)[0] = .9;
  assert.equal(two.getChannelData(0)[0], .125);
  const third = await load(contextFor());
  assert.equal(third.getChannelData(0)[0], .125, 'a consumer cannot mutate cached PCM for the next demo');
  await load(contextFor(2000));
  assert.deepEqual(rates, [1000, 2000]);
});

test('a rejected generation is evicted so a later explicit request can retry', async () => {
  let attempts = 0;
  const load = createSoundStudyBufferLoader(async rate => {
    if (++attempts === 1) throw new Error('Preparation failed');
    return pcmFor(rate);
  });
  await assert.rejects(load(contextFor()), /Preparation failed/);
  const buffer = await load(contextFor());
  assert.equal(attempts, 2);
  assert.equal(buffer.duration, 12);
});

test('a closed consumer does not allocate a buffer, and another consumer can reuse the prepared PCM', async () => {
  let complete, generations = 0;
  const pending = new Promise(resolve => { complete = resolve; });
  const load = createSoundStudyBufferLoader(() => { generations++; return pending; });
  const closed = contextFor();
  const request = load(closed);
  closed.state = 'closed';
  complete(pcmFor(closed.sampleRate));
  await assert.rejects(request, /closed/);
  assert.equal(closed.allocations, 0);
  assert.equal((await load(contextFor())).duration, 12);
  assert.equal(generations, 1);
});

test('unsupported and constructor-blocked workers fall back to the same local composition', async () => {
  const expected = createSoundStudyPcm(1000);
  for (const factory of [() => null, () => { throw new Error('Worker blocked'); }]) {
    const actual = await generateSoundStudyOffThread(1000, factory);
    assert.equal(fingerprint(actual.left), fingerprint(expected.left));
    assert.equal(fingerprint(actual.right), fingerprint(expected.right));
  }
});

test('an asynchronously blocked worker is terminated before local fallback', async () => {
  let terminations = 0, prevented = false;
  const worker = {
    onmessage: null, onerror: null, onmessageerror: null,
    postMessage() { Promise.resolve().then(() => this.onerror?.({ preventDefault() { prevented = true; } })); },
    terminate() { terminations++; },
  };
  const pcm = await generateSoundStudyOffThread(1000, () => worker);
  assert.equal(terminations, 1);
  assert.equal(prevented, true);
  assert.equal(worker.onerror, null);
  assert.equal(pcm.left.length, 12000);
});
