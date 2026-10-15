import { useEffect, useRef, useState } from 'react';
import { createSegmenter, downsample, encodeWav, FRAME_SAMPLES } from './vad.js';
// Copies raw microphone samples to the main thread; VAD runs there.
const worklet = `registerProcessor('capture',class extends AudioWorkletProcessor{process(inputs){const c=inputs[0][0];if(c)this.port.postMessage(c.slice());return true;}});`;
// While active, listens to the microphone and calls onSegment(wav) whenever a phrase ends in silence.
// phase: off | starting | listening | speaking | error
export function useVoice(active, onSegment) {
  const [phase,setPhase]=useState('off'), [error,setError]=useState('');
  const callback=useRef(onSegment);
  useEffect(()=>{callback.current=onSegment;});
  useEffect(()=>{
    if(!active){setPhase('off');return;}
    let closed=false, stream, context, moduleUrl;
    setPhase('starting');setError('');
    (async()=>{
      try {
        stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true,autoGainControl:true}});
        if(closed)return;
        context=new AudioContext();
        moduleUrl=URL.createObjectURL(new Blob([worklet],{type:'text/javascript'}));
        await context.audioWorklet.addModule(moduleUrl);
        if(closed)return;
        const node=new AudioWorkletNode(context,'capture'), mute=context.createGain();
        mute.gain.value=0;
        context.createMediaStreamSource(stream).connect(node).connect(mute).connect(context.destination);
        const segmenter=createSegmenter();
        let pending=new Float32Array(0), speaking=false;
        node.port.onmessage=({data})=>{
          const chunk=downsample(data,context.sampleRate), joined=new Float32Array(pending.length+chunk.length);
          joined.set(pending);joined.set(chunk,pending.length);
          let offset=0;
          for(;offset+FRAME_SAMPLES<=joined.length;offset+=FRAME_SAMPLES){
            const segment=segmenter.push(joined.subarray(offset,offset+FRAME_SAMPLES));
            if(segment)callback.current(encodeWav(segment));
          }
          pending=joined.slice(offset);
          if(segmenter.speaking!==speaking){speaking=segmenter.speaking;setPhase(speaking?'speaking':'listening');}
        };
        setPhase('listening');
      } catch(e) {
        if(closed)return;
        setPhase('error');
        setError(e?.name==='NotAllowedError'?'마이크 권한이 필요합니다. 브라우저 주소창에서 허용해 주세요.':e?.name==='NotFoundError'?'마이크를 찾을 수 없습니다.':'마이크를 시작하지 못했습니다. localhost 또는 HTTPS에서 열어 주세요.');
      }
    })();
    return ()=>{closed=true;stream?.getTracks().forEach(t=>t.stop());context?.close();if(moduleUrl)URL.revokeObjectURL(moduleUrl);};
  },[active]);
  return {phase,error};
}
