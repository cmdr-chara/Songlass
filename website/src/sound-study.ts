/** An original, deterministic twelve-second instrumental, synthesized locally.
 * Each note wraps its release into the next loop to avoid a splice click.
 * There are no samples, network requests, microphone input, or user files.
 */
export const SOUND_STUDY_DURATION = 12;
export type SoundStudyPcm = Readonly<{
  sampleRate: number;
  left: Float32Array<ArrayBuffer>;
  right: Float32Array<ArrayBuffer>;
}>;

export function createSoundStudy(context: Pick<AudioContext, 'sampleRate' | 'createBuffer'>) {
  const buffer = context.createBuffer(2, Math.round(context.sampleRate * SOUND_STUDY_DURATION), context.sampleRate);
  fillSoundStudy(context.sampleRate, buffer.getChannelData(0), buffer.getChannelData(1));
  return buffer;
}

/** The worker only needs typed arrays; it never creates an audio context. */
export function createSoundStudyPcm(sampleRate: number): SoundStudyPcm {
  if (!Number.isFinite(sampleRate) || sampleRate <= 0) throw new RangeError('Invalid sound study sample rate');
  const length = Math.round(sampleRate * SOUND_STUDY_DURATION);
  const left = new Float32Array(length);
  const right = new Float32Array(length);
  fillSoundStudy(sampleRate, left, right);
  return { sampleRate, left, right };
}

function fillSoundStudy(rate: number, left: Float32Array, right: Float32Array) {
  const length = left.length;
  const note = (midi: number, start: number, duration: number, level: number, pan: number, bell = false) => {
    const frequency = 440 * 2 ** ((midi - 69) / 12);
    const offset = Math.round(start * rate);
    const samples = Math.round(duration * rate);
    for (let frame = 0; frame < samples; frame++) {
      const time = frame / rate;
      const attack = Math.min(1, time / (bell ? 0.012 : 0.09));
      const release = Math.min(1, (duration - time) / 0.25);
      const envelope = attack * release * Math.exp(-time * (bell ? 2.6 : 0.7));
      const phase = 2 * Math.PI * frequency * time;
      const sample = (Math.sin(phase) + Math.sin(phase * 2) * 0.23 + Math.sin(phase * (bell ? 4 : 3)) * 0.08) * envelope * level;
      const index = (offset + frame) % length;
      left[index] += sample * Math.sqrt((1 - pan) / 2);
      right[index] += sample * Math.sqrt((1 + pan) / 2);
    }
  };
  const chords = [[50, 57, 61, 64], [47, 54, 57, 62], [43, 50, 54, 57], [45, 52, 57, 61]];
  chords.forEach((chord, measure) => {
    note(chord[0] - 12, measure * 3, 3.5, 0.16, 0);
    chord.forEach((midi, voice) => note(midi + 12, measure * 3 + voice * 0.06, 3.4, 0.045, (voice - 1.5) * 0.38));
    for (let beat = 0; beat < 8; beat++) {
      note(chord[[1, 3, 2, 3, 0, 2, 1, 3][beat]] + 24, measure * 3 + beat * 0.375, 1.2, beat % 2 ? 0.03 : 0.043, Math.sin(beat * 1.6) * 0.55, true);
    }
  });
  // Quiet deterministic high-frequency texture makes treble adjustments audible.
  let seed = 815;
  for (let beat = 0; beat < 32; beat++) {
    const offset = Math.round(beat * 0.375 * rate);
    const duration = Math.round(rate * 0.065);
    let previous = 0;
    for (let frame = 0; frame < duration; frame++) {
      seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
      const noise = seed / 2147483648;
      const envelope = Math.min(1, frame / (rate * 0.002)) * Math.exp(-frame / (rate * 0.014));
      const sample = (noise - previous) * envelope * 0.009;
      left[(offset + frame) % length] += sample;
      right[(offset + frame) % length] += sample;
      previous = noise;
    }
  }
  let peak = 0;
  for (let frame = 0; frame < length; frame++) peak = Math.max(peak, Math.abs(left[frame]), Math.abs(right[frame]));
  const level = peak > 0 ? 0.7 / peak : 1;
  for (let frame = 0; frame < length; frame++) { left[frame] *= level; right[frame] *= level; }
}
