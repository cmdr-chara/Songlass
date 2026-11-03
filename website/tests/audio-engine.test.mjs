import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SoundStudy, audioContextConstructor } from '../src/audio-engine.ts';
import { createSoundStudy } from '../src/sound-study.ts';

// These allocation/clock/node ports test scheduling and ownership contracts only.
// They do not render Web Audio, model a filter response, or prove audible quality.
class ParameterPort {
  value = 0;
  setValueAtTime(value) { this.value = value; }
  linearRampToValueAtTime(value) { this.value = value; }
  setTargetAtTime(value) { this.value = value; }
  cancelScheduledValues() {}
  cancelAndHoldAtTime() {}
}
class NodePort {
  disconnected = false;
  connect() {}
  disconnect() { this.disconnected = true; }
}
class SourcePort extends NodePort {
  starts = [];
  stops = [];
  onended = null;
  start(when, offset) { this.starts.push({ when, offset }); }
  stop(when) { this.stops.push(when); }
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
class ContextPort {
  sampleRate = 1000; // Fast orchestration fixture; signal tests use 44.1/48 kHz.
  currentTime = 0;
  state = 'suspended';
  destination = new NodePort();
  nodes = [];
  sources = [];
  filters = [];
  resumes = [];
  listeners = new Set();
  resumeCalls = 0;
  closeCalls = 0;
  bufferAllocations = 0;
  createBuffer(numberOfChannels, length, sampleRate) {
    this.bufferAllocations++;
    const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
    return { duration: length / sampleRate, getChannelData: channel => channels[channel] };
  }
  createBiquadFilter() {
    const node = new NodePort();
    Object.assign(node, { gain: new ParameterPort(), frequency: new ParameterPort(), Q: new ParameterPort() });
    this.filters.push(node); this.nodes.push(node); return node;
  }
  createDynamicsCompressor() {
    const node = new NodePort();
    for (const key of ['threshold', 'knee', 'ratio', 'attack', 'release']) node[key] = new ParameterPort();
    this.nodes.push(node); return node;
  }
  createGain() {
    const node = new NodePort(); node.gain = new ParameterPort(); this.nodes.push(node); return node;
  }
  createAnalyser() { const node = new NodePort(); this.nodes.push(node); return node; }
  createBufferSource() { const node = new SourcePort(); this.sources.push(node); this.nodes.push(node); return node; }
  addEventListener(_, listener) { this.listeners.add(listener); }
  removeEventListener(_, listener) { this.listeners.delete(listener); }
  signalState() { this.listeners.forEach(listener => listener()); }
  holdResume() { const next = deferred(); this.resumes.push(next); return next; }
  async resume() {
    this.resumeCalls++;
    const pending = this.resumes.shift();
    if (pending) await pending.promise;
    if (this.state === 'closed') throw new Error('Closed context');
    this.state = 'running'; this.signalState();
  }
  async suspend() { this.state = 'suspended'; this.signalState(); }
  async close() { this.closeCalls++; this.state = 'closed'; this.signalState(); }
  advance(seconds) { if (this.state === 'running') this.currentTime += seconds; }
}
function studyFor(t, builder = async context => createSoundStudy(context)) {
  const study = new SoundStudy(ContextPort, builder);
  t.after(() => study.close());
  return study;
}

test('import, construction, EQ and paused seeking never start audio', t => {
  assert.equal(audioContextConstructor(), undefined);
  const study = studyFor(t);
  study.setBands([5, -2, -3]);
  study.seek(7);
  assert.equal(study.currentTime, 7);
  assert.equal(study.state, 'idle');
  assert.equal(study.context.resumeCalls, 0);
  assert.equal(study.context.bufferAllocations, 0, 'construction and idle controls do not synthesize samples');
  assert.equal(study.context.sources.length, 0);
});

test('pause freezes the real context position and play resumes the same loop offset', async t => {
  const study = studyFor(t);
  assert.equal(await study.play(), true);
  study.context.advance(3.25);
  assert.equal(study.currentTime, 3.25);
  study.pause();
  study.context.advance(0.03);
  assert.equal(study.currentTime, 3.25);
  assert.equal(await study.play(), true);
  assert.equal(study.context.sources.at(-1).starts[0].offset, 3.25);
  study.context.advance(10);
  assert.equal(study.currentTime, 1.25);
});

test('seeking moves the audible source and clamps paused offsets without autoplay', async t => {
  const study = studyFor(t);
  await study.play();
  study.context.advance(2);
  const oldSource = study.context.sources.at(-1);
  study.seek(8.5);
  assert.equal(study.context.sources.at(-1).starts[0].offset, 8.5);
  assert.equal(oldSource.stops.length, 1);
  study.context.advance(0.5);
  assert.equal(study.currentTime, 9);
  study.pause();
  const sourceCount = study.context.sources.length;
  study.seek(-4); assert.equal(study.currentTime, 0);
  study.seek(90); assert.equal(study.currentTime, study.duration);
  study.seek(NaN); assert.equal(study.currentTime, study.duration);
  assert.equal(study.context.sources.length, sourceCount);
  assert.equal(study.state, 'paused');
  await study.play();
  assert.equal(study.context.sources.at(-1).starts[0].offset, 0, 'playing from the end starts the loop again');
});

test('the EQ configuration survives seeking and pause/resume', async t => {
  const study = studyFor(t);
  study.setBands([5, -2, -3]);
  await study.play(); study.seek(4); study.pause(); await study.play();
  assert.deepEqual(study.context.filters.map(filter => filter.gain.value), [5, -2, -3]);
});

test('pausing a pending resume prevents a source and re-suspends a late context', async t => {
  const study = studyFor(t);
  const pending = study.context.holdResume();
  const playing = study.play();
  assert.equal(study.state, 'loading');
  study.pause(true);
  pending.resolve();
  assert.equal(await playing, false);
  assert.equal(study.state, 'paused');
  assert.equal(study.context.sources.length, 0);
  assert.equal(study.context.state, 'suspended');
});

test('the latest player intent supersedes another player with a pending resume', async t => {
  const first = studyFor(t), second = studyFor(t);
  const pending = first.context.holdResume();
  const oldPlay = first.play();
  await second.play();
  pending.resolve();
  assert.equal(await oldPlay, false);
  assert.equal(first.state, 'paused');
  assert.equal(first.context.state, 'suspended');
  assert.equal(first.context.sources.length, 0);
  assert.equal(second.state, 'playing');
});

test('handoff immediately silences the previous source, preserves its position, and notifies its UI', async t => {
  const first = studyFor(t), second = studyFor(t);
  const states = [];
  first.subscribe(state => states.push(state));
  await first.play(); first.context.advance(4);
  const original = first.context.sources.at(-1);
  await second.play();
  assert.equal(first.state, 'paused');
  assert.equal(first.currentTime, 4);
  assert.equal(original.stops.at(-1), first.context.currentTime);
  assert.equal(original.disconnected, true);
  assert.equal(first.context.state, 'suspended');
  assert.equal(states.at(-1), 'paused');
  await first.play();
  assert.equal(first.context.sources.at(-1).starts[0].offset, 4);
  assert.equal(second.state, 'paused');
});

test('handoff also silences the release tail of an already-paused study', async t => {
  const first = studyFor(t), second = studyFor(t);
  await first.play();
  first.pause();
  const previousSource = first.context.sources.at(-1);
  assert(previousSource.stops.at(-1) > first.context.currentTime);
  await second.play();
  assert.equal(previousSource.stops.at(-1), first.context.currentTime);
  assert.equal(previousSource.disconnected, true);
  assert.equal(first.context.state, 'suspended');
  assert.equal(second.state, 'playing');
});

test('a stale rejected resume cannot stop a newer successful play on the same engine', async t => {
  const study = studyFor(t);
  const previous = study.context.holdResume(), latest = study.context.holdResume();
  const oldPlay = study.play(), newPlay = study.play();
  latest.resolve();
  assert.equal(await newPlay, true);
  previous.reject(new Error('Old resume failed'));
  assert.equal(await oldPlay, false);
  assert.equal(study.state, 'playing');
  assert.equal(study.context.sources.length, 1);
  assert.equal(await study.play(), true);
  assert.equal(study.context.sources.length, 1, 'repeated play while playing cannot duplicate a source');
});

test('a current resume failure relinquishes ownership and allows a real retry', async t => {
  const study = studyFor(t);
  const pending = study.context.holdResume();
  const playing = study.play();
  pending.reject(new Error('Audio unavailable'));
  await assert.rejects(playing, /Audio unavailable/);
  assert.equal(study.state, 'paused');
  assert.equal(await study.play(), true);
  assert.equal(study.context.sources.length, 1);
});

test('pause suspends its idle context after the short release tail', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const study = studyFor(t);
  await study.play(); study.pause();
  assert.equal(study.context.state, 'running');
  t.mock.timers.tick(100);
  assert.equal(study.context.state, 'suspended');
});

test('a system interruption pauses the UI and requires another explicit play', async t => {
  const study = studyFor(t);
  await study.play(); study.context.advance(2.5);
  await study.context.suspend();
  assert.equal(study.state, 'paused');
  assert.equal(study.currentTime, 2.5);
  assert.equal(study.context.sources.at(-1).disconnected, true);
  assert.equal(await study.play(), true);
  assert.equal(study.context.sources.at(-1).starts[0].offset, 2.5);
});

test('closing cancels pending resumes, releases nodes/listeners, and is idempotent', async t => {
  const study = studyFor(t);
  const pending = study.context.holdResume();
  const playing = study.play();
  study.close(); study.close();
  pending.resolve();
  assert.equal(await playing, false);
  assert.equal(study.state, 'closed');
  assert.equal(study.context.closeCalls, 1);
  assert.equal(study.context.listeners.size, 0);
  assert(study.context.nodes.every(node => node.disconnected));
  assert.equal(study.context.sources.length, 0);
  assert.equal(await study.play(), false);
});

test('resume begins in the play call stack, while PCM waits and uses the latest seek', async t => {
  const pending = deferred();
  let builds = 0;
  const study = studyFor(t, () => { builds++; return pending.promise; });
  assert.equal(builds, 0);
  const playing = study.play();
  assert.equal(study.context.resumeCalls, 1, 'resume must precede any asynchronous preparation wait');
  assert.equal(builds, 1);
  assert.equal(study.state, 'loading');
  assert.equal(study.context.sources.length, 0);
  study.seek(7.5);
  pending.resolve(createSoundStudy(study.context));
  assert.equal(await playing, true);
  assert.equal(study.context.sources[0].starts[0].offset, 7.5);
});

test('pause and a subsequent seek cancel a start whose PCM is still pending', async t => {
  const pending = deferred();
  const study = studyFor(t, () => pending.promise);
  const playing = study.play();
  study.pause(true);
  study.seek(9);
  pending.resolve(createSoundStudy(study.context));
  assert.equal(await playing, false);
  assert.equal(study.state, 'paused');
  assert.equal(study.currentTime, 9);
  assert.equal(study.context.sources.length, 0);
  assert.equal(study.context.state, 'suspended');
});

test('PCM finishing for a superseded demo cannot take playback from the newer demo', async t => {
  const pending = deferred();
  const first = studyFor(t, () => pending.promise), second = studyFor(t);
  const oldPlay = first.play();
  await second.play();
  pending.resolve(createSoundStudy(first.context));
  assert.equal(await oldPlay, false);
  assert.equal(first.context.sources.length, 0);
  assert.equal(first.context.state, 'suspended');
  assert.equal(second.state, 'playing');
});

test('closing during PCM preparation prevents source creation after preparation completes', async t => {
  const pending = deferred();
  const study = studyFor(t, () => pending.promise);
  const playing = study.play();
  const buffer = createSoundStudy(study.context);
  study.close();
  pending.resolve(buffer);
  assert.equal(await playing, false);
  assert.equal(study.context.sources.length, 0);
  assert.equal(study.state, 'closed');
});

test('failed PCM preparation re-suspends a later resume and permits a fresh preparation', async t => {
  const pending = deferred();
  let builds = 0;
  const study = studyFor(t, async context => ++builds === 1 ? pending.promise : createSoundStudy(context));
  const resume = study.context.holdResume();
  const playing = study.play();
  pending.reject(new Error('Preparation unavailable'));
  await assert.rejects(playing, /Preparation unavailable/);
  resume.resolve();
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(study.context.state, 'suspended');
  assert.equal(study.context.sources.length, 0);
  assert.equal(await study.play(), true);
  assert.equal(builds, 2);
});
