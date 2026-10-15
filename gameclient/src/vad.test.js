import test from 'node:test';
import assert from 'node:assert/strict';
import { createSegmenter, downsample, encodeWav, cleanTranscript, FRAME_SAMPLES } from './vad.js';
const frame = level => Float32Array.from({length:FRAME_SAMPLES}, (_, i) => (i % 2 ? level : -level));
const feed = (segmenter, level, ms) => { let out = null; for (let t = 0; t < ms; t += 20) out = segmenter.push(frame(level)) || out; return out; };
test('silence never produces a segment',()=>{
 const s=createSegmenter();
 assert.equal(feed(s,0.002,3000),null);assert.equal(s.speaking,false);
});
test('speech followed by silence is cut into one segment with pre-roll',()=>{
 const s=createSegmenter();
 feed(s,0.002,500);
 assert.equal(feed(s,0.2,600),null);assert.equal(s.speaking,true);
 const segment=feed(s,0.002,700);
 assert.ok(segment);assert.equal(s.speaking,false);
 // 300ms pre-roll (its last 60ms already loud) + remaining 540ms speech + 700ms trailing silence
 assert.equal(segment.length/16000*1000,1540);
});
test('a short click is ignored',()=>{
 const s=createSegmenter();
 feed(s,0.002,300);feed(s,0.3,80);
 assert.equal(feed(s,0.002,800),null);
});
test('long speech is cut at the maximum length',()=>{
 const s=createSegmenter({maxSpeechMs:1000});
 assert.ok(feed(s,0.2,1200));
});
test('downsample and WAV header match voice_server requirements',()=>{
 assert.equal(downsample(new Float32Array(48000),48000).length,16000);
 const view=new DataView(encodeWav(new Float32Array(160)));
 assert.equal(String.fromCharCode(...new Uint8Array(view.buffer,0,4)),'RIFF');
 assert.equal(view.getUint16(22,true),1);assert.equal(view.getUint32(24,true),16000);assert.equal(view.getUint16(34,true),16);
 assert.equal(view.byteLength,44+320);
});
test('empty and typical Whisper hallucinations are dropped',()=>{
 assert.equal(cleanTranscript('  앞으로 가 '),'앞으로 가');
 assert.equal(cleanTranscript('감사합니다.'),'');assert.equal(cleanTranscript('시청해주셔서 감사합니다!'),'');assert.equal(cleanTranscript(''),'');
});
