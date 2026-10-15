// Energy-based voice activity detection: speech starts when the level rises above the
// background noise and a segment is cut once it stays quiet for a while.
export const SAMPLE_RATE = 16000;
export const FRAME_SAMPLES = SAMPLE_RATE / 50; // 20ms
const hallucinations = /^(감사합니다|시청해\s*주셔서\s*감사합니다|구독과\s*좋아요.*|MBC 뉴스.*)[.!?。\s]*$/;
export function downsample(input, inputRate, outRate = SAMPLE_RATE) {
  if (inputRate === outRate) return Float32Array.from(input);
  const ratio = inputRate / outRate, out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const start = Math.floor(i * ratio), end = Math.min(input.length, Math.floor((i + 1) * ratio));
    let sum = 0; for (let j = start; j < end; j++) sum += input[j];
    out[i] = sum / Math.max(1, end - start);
  }
  return out;
}
export function rms(frame) { let sum = 0; for (const v of frame) sum += v * v; return Math.sqrt(sum / frame.length); }
// 16-bit mono PCM WAV, the only format voice_server accepts.
export function encodeWav(samples, rate = SAMPLE_RATE) {
  const buffer = new ArrayBuffer(44 + samples.length * 2), view = new DataView(buffer);
  const text = (offset, s) => [...s].forEach((c, i) => view.setUint8(offset + i, c.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, 36 + samples.length * 2, true); text(8, 'WAVE'); text(12, 'fmt ');
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, samples.length * 2, true);
  samples.forEach((v, i) => view.setInt16(44 + i * 2, Math.max(-1, Math.min(1, v)) * 0x7fff, true));
  return buffer;
}
export function cleanTranscript(text) {
  const trimmed = (text || '').trim();
  return hallucinations.test(trimmed) ? '' : trimmed;
}
export function createSegmenter({ startMs = 60, silenceMs = 700, preRollMs = 300, minSpeechMs = 250, maxSpeechMs = 8000, minThreshold = 0.015, noiseRatio = 3 } = {}) {
  const frameMs = FRAME_SAMPLES / SAMPLE_RATE * 1000;
  let noise = 0.005, loud = 0, quiet = 0, lead = 0, speaking = false, frames = [], preRoll = [];
  const finish = () => {
    // The pre-roll before the first loud frame and the trailing silence are not speech.
    const speechFrames = frames.length - Math.round(quiet / frameMs) - lead;
    const segment = speechFrames * frameMs >= minSpeechMs ? concat(frames) : null;
    speaking = false; frames = []; preRoll = []; loud = 0; quiet = 0; lead = 0;
    return segment;
  };
  return {
    get speaking() { return speaking; },
    // Feeds one 20ms frame at 16kHz. Returns the finished segment, or null.
    push(frame) {
      const level = rms(frame), threshold = Math.max(minThreshold, noise * noiseRatio);
      if (!speaking) {
        if (level > threshold) loud += frameMs; else { loud = 0; noise = noise * 0.95 + level * 0.05; }
        preRoll.push(frame); if (preRoll.length * frameMs > preRollMs) preRoll.shift();
        if (loud >= startMs) { speaking = true; frames = [...preRoll]; lead = preRoll.length - Math.round(loud / frameMs); quiet = 0; }
        return null;
      }
      frames.push(frame);
      quiet = level > threshold ? 0 : quiet + frameMs;
      if (quiet >= silenceMs || frames.length * frameMs >= maxSpeechMs) return finish();
      return null;
    },
    reset() { speaking = false; frames = []; preRoll = []; loud = 0; quiet = 0; },
  };
}
function concat(frames) {
  const out = new Float32Array(frames.reduce((n, f) => n + f.length, 0));
  let offset = 0; for (const f of frames) { out.set(f, offset); offset += f.length; }
  return out;
}
