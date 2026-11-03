import { before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createSoundStudy } from '../src/sound-study.ts';

// Allocation only: these tests inspect generated PCM samples. They do not mock
// audio nodes or claim to verify browser playback, equalization, or scheduling.
function allocationContext(sampleRate) {
  return {
    sampleRate,
    createBuffer(numberOfChannels, length, rate) {
      const channels = Array.from({ length: numberOfChannels }, () => new Float32Array(length));
      return {
        numberOfChannels,
        length,
        sampleRate: rate,
        duration: length / rate,
        getChannelData(channel) { return channels[channel]; },
      };
    },
  };
}

function fingerprint(samples) {
  const bytes = new Uint8Array(samples.buffer, samples.byteOffset, samples.byteLength);
  return createHash('sha256').update(bytes).digest('hex');
}

for (const sampleRate of [44100, 48000]) {
  describe(`original sound study at ${sampleRate} Hz`, () => {
    let buffer;
    before(() => { buffer = createSoundStudy(allocationContext(sampleRate)); });

    test('provides exactly twelve seconds of stereo at the requested sample rate', () => {
      assert.equal(buffer.sampleRate, sampleRate);
      assert.equal(buffer.numberOfChannels, 2);
      assert.equal(buffer.length, sampleRate * 12);
      assert.equal(buffer.duration, 12);
      for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
        assert.equal(buffer.getChannelData(channel).length, sampleRate * 12);
      }
    });

    test('contains finite, nonzero stereo with conservative signal headroom', () => {
      const left = buffer.getChannelData(0);
      const right = buffer.getChannelData(1);
      let stereoDifference = 0;
      for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
        let energy = 0;
        let peak = 0;
        const samples = buffer.getChannelData(channel);
        for (let frame = 0; frame < samples.length; frame++) {
          const sample = samples[frame];
          if (!Number.isFinite(sample)) assert.fail(`Channel ${channel}, frame ${frame} is not finite`);
          energy += sample * sample;
          peak = Math.max(peak, Math.abs(sample));
        }
        assert.ok(energy > 0, `Channel ${channel} must contain sound`);
        assert.ok(peak <= 0.700001, `Channel ${channel} peak ${peak} exceeds the headroom limit`);
      }
      for (let frame = 0; frame < left.length; frame++) {
        stereoDifference += (left[frame] - right[frame]) ** 2;
      }
      assert.ok(stereoDifference > 0, 'The two channels must contain distinct stereo information');
    });

    test('recreates identical PCM samples without depending on previous generation', () => {
      const recreated = createSoundStudy(allocationContext(sampleRate));
      for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
        assert.equal(fingerprint(recreated.getChannelData(channel)), fingerprint(buffer.getChannelData(channel)));
      }
    });

    test('has no anomalous sample step where the loop joins', () => {
      for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
        const samples = buffer.getChannelData(channel);
        const ordinarySteps = new Float64Array(samples.length - 1);
        for (let frame = 1; frame < samples.length; frame++) {
          ordinarySteps[frame - 1] = Math.abs(samples[frame] - samples[frame - 1]);
        }
        ordinarySteps.sort();
        // Compare the join against a robust distribution of the actual signal,
        // rather than its largest transient, a fixed sample value, or the score.
        const usualUpperStep = ordinarySteps[Math.floor((ordinarySteps.length - 1) * 0.95)];
        const joinStep = Math.abs(samples[0] - samples[samples.length - 1]);
        assert.ok(usualUpperStep > 0, 'The ordinary signal must have measurable variation');
        assert.ok(joinStep <= usualUpperStep,
          `Channel ${channel} loop step ${joinStep} exceeds the ordinary 95th percentile ${usualUpperStep}`);
      }
    });
  });
}
