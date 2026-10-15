import { useEffect, useRef, useState } from 'react';
import { request, sessionPath, socketUrl } from './api.js';
import { applyEvent, snapshot } from './state.js';
const storageKey='say2play.session';
const stored = () => { try { return sessionStorage.getItem(storageKey); } catch { return null; } };
const remember = id => { try { if(id) sessionStorage.setItem(storageKey,id); else sessionStorage.removeItem(storageKey); } catch { /* Storage may be disabled. */ } };
export function useGame() {
  const [game,setGame]=useState(null), [status,setStatus]=useState('connecting');
  const [error,setError]=useState(''), [feedback,setFeedback]=useState({}), [pending,setPending]=useState({}), [busy,setBusy]=useState(false);
  const [attempt,setAttempt]=useState(0);
  const runtime=useRef(null);
  useEffect(()=>{
    const r={alive:true,socket:null,timer:null,ended:false,finished:new Set(),pending:new Set(),generation:0,busy:false};
    runtime.current=r;
    setStatus('connecting');setError('');setPending({});setBusy(false);setFeedback({});
    let retries=0;
    const end=message=>{r.ended=true;remember(null);setStatus('ended');setError(message);};
    r.end=end;
    const handle=event=>{
      if(event.type==='ACTION_FINISHED') r.finished.add(event.data.actionId);
      if(event.type==='SESSION_RESET'){r.generation++;setFeedback({});r.finished.clear();}
      setGame(current=>current?applyEvent(current,event):current);
      if(event.type==='SESSION_ENDED') end(event.data.reason==='EXPIRED'?'세션이 만료되었습니다. 새 게임을 시작해 주세요.':'게임이 종료되었습니다.');
    };
    async function connect(id) {
      if(!r.alive||r.ended)return;
      setStatus('connecting');
      const socket=new WebSocket(socketUrl(id));r.socket=socket;
      let ready=false,buffer=[];
      socket.onmessage=e=>{
        try {const event=JSON.parse(e.data);if(ready)handle(event);else buffer.push(event);}
        catch {setError('게임 이벤트를 읽을 수 없습니다. 다시 연결합니다.');socket.close();}
      };
      socket.onopen=async()=>{
        try {
          const data=await request(sessionPath(id));
          if(!r.alive||socket!==r.socket||socket.readyState!==WebSocket.OPEN)return;
          setGame(snapshot(data));buffer.forEach(handle);buffer=[];ready=true;retries=0;
          if(data.session.status==='ENDED')end('게임이 종료되었습니다.');
          if(!r.ended){setStatus('connected');setError('');}
        } catch(e) {
          if(!r.alive)return;
          if(e.code==='SESSION_NOT_FOUND')end('세션이 만료되었습니다. 새 게임을 시작해 주세요.');
          else setError(e.message);
          socket.close();
        }
      };
      socket.onerror=()=>socket.close();
      socket.onclose=()=>{
        if(!r.alive||r.ended)return;
        setStatus('reconnecting');
        r.timer=setTimeout(async()=>{
          try {await request(sessionPath(id));if(r.alive)connect(id);}
          catch(e){if(!r.alive)return;if(e.code==='SESSION_NOT_FOUND')end('세션이 만료되었습니다. 새 게임을 시작해 주세요.');else {setError(e.message);connect(id);}}
        },Math.min(1000*2**retries++,10000));
      };
    }
    (async()=>{
      try {
        let id=stored(), data;
        if(id)try {data=await request(sessionPath(id));}catch(e){if(e.code!=='SESSION_NOT_FOUND')throw e;remember(null);}
        if(!r.alive)return;
        if(!data||data.session.status==='ENDED')data=await request('/sessions',{method:'POST'});
        // Preserve a newly created session even if the component was unmounted during POST.
        remember(data.session.id);
        if(!r.alive)return;
        r.id=data.session.id;setGame(snapshot(data));connect(r.id);
      }catch(e){if(r.alive){setStatus('error');setError(e.message);}}
    })();
    return ()=>{r.alive=false;clearTimeout(r.timer);r.socket?.close();};
  },[attempt]);
  async function command(characterId,text) {
    const r=runtime.current;
    if(status!=='connected'||r.busy||r.pending.has(characterId))return false;
    if(!text.trim()||text.trim().length>500){setFeedback(f=>({...f,[characterId]:{error:true,message:'1~500자의 명령을 입력해 주세요.'}}));return false;}
    const generation=r.generation;r.pending.add(characterId);setPending(Object.fromEntries([...r.pending].map(id=>[id,true])));
    try {
      const data=await request(`${sessionPath(r.id)}/characters/${encodeURIComponent(characterId)}/commands`,{method:'POST',body:{text}});
      if(!r.alive||r.ended||generation!==r.generation)return false;
      setFeedback(f=>({...f,[characterId]:{message:`“${text}” → ${data.action.actionType}`,actionId:data.action.id}}));
      setGame(g=>({...g,characters:g.characters.map(c=>c.id!==characterId||r.finished.has(data.action.id)||c.queue.some(a=>a.id===data.action.id)?c:{...c,queue:[...c.queue,data.action].sort((a,b)=>a.seq-b.seq)})}));
      return true;
    }catch(e){if(r.alive){setFeedback(f=>({...f,[characterId]:{error:true,message:e.message}}));if(e.code==='SESSION_NOT_FOUND'||e.code==='SESSION_ENDED')r.end(e.message);}return false;}
    finally{r.pending.delete(characterId);if(r.alive)setPending(Object.fromEntries([...r.pending].map(id=>[id,true])));}
  }
  async function control(method,suffix) {
    const r=runtime.current;
    if(!r.id||r.busy||r.pending.size)return;
    r.busy=true;setBusy(true);setError('');
    try {
      const data=await request(`${sessionPath(r.id)}${suffix}`,{method});
      if(!r.alive)return;
      if(method==='DELETE'){r.end('게임을 종료했습니다.');r.socket?.close();setGame(null);}
      else {
        // Fetch after reset instead of overwriting newer WebSocket state with a delayed reset response.
        if(data && !r.ended){r.generation++;setFeedback({});r.socket?.close();}
      }
    }catch(e){if(r.alive){setError(e.message);if(e.code==='SESSION_NOT_FOUND'||e.code==='SESSION_ENDED')r.end(e.message);}}
    finally{r.busy=false;if(r.alive)setBusy(false);}
  }
  return {game,status,error,feedback,pending,busy,command,reset:()=>control('POST','/reset'),leave:()=>control('DELETE',''),retry:()=>setAttempt(n=>n+1)};
}
