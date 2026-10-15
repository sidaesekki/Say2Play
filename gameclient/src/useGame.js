import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { brokerUrl, request, sessionPath, topic } from './api.js';
import { applyEvent, snapshot } from './state.js';
const storageKey='say2play.session';
const load = () => { try { return JSON.parse(sessionStorage.getItem(storageKey)); } catch { return null; } };
const save = creds => { try { if(creds) sessionStorage.setItem(storageKey,JSON.stringify(creds)); else sessionStorage.removeItem(storageKey); } catch { /* Storage may be disabled. */ } };
const invitedSession = () => new URLSearchParams(window.location.search).get('session');
// Keep the invite link in the address bar while a multiplayer room is open.
const showInvite = id => { const url=new URL(window.location.href); if(id) url.searchParams.set('session',id); else url.searchParams.delete('session'); window.history.replaceState(null,'',url); };
const fatal = { SESSION_NOT_FOUND:'세션이 만료되었습니다. 새 게임을 시작해 주세요.', INVALID_PLAYER_TOKEN:'세션에서 나가게 되었습니다. 연결이 오래 끊겼거나 다른 곳에서 나갔어요.', SESSION_ENDED:'게임이 종료되었습니다.' };
export function useGame() {
  const [game,setGame]=useState(null), [status,setStatus]=useState('loading');
  const [error,setError]=useState(''), [feedback,setFeedback]=useState({}), [pending,setPending]=useState({}), [busy,setBusy]=useState(false);
  const [creds,setCreds]=useState(null);
  const runtime=useRef({});
  async function enter(open) {
    setBusy(true);setError('');
    try {
      const data=await open();
      const next={sessionId:data.session.id,token:data.me.playerToken};
      save(next);showInvite(data.session.mode==='MULTI'?next.sessionId:null);
      setGame(snapshot(data));setCreds(next);
    } finally { setBusy(false); }
  }
  // First visit: restore this tab's seat, otherwise accept an invite link, otherwise show the lobby.
  useEffect(()=>{
    const saved=load(), invited=invitedSession();
    if(saved&&(!invited||invited===saved.sessionId)){setCreds(saved);return;}
    if(!invited){setStatus('lobby');return;}
    enter(()=>request(`${sessionPath(invited)}/join`,{method:'POST'})).catch(e=>{
      showInvite(null);setStatus('lobby');
      setError(e.code==='SESSION_FULL'?'이미 두 명이 참가한 방입니다.':e.code==='SESSION_NOT_FOUND'?'초대받은 방이 없거나 종료되었습니다.':e.message);
    });
  },[]);
  useEffect(()=>{
    if(!creds)return;
    const r={alive:true,ended:false,finished:new Set(),pending:new Set(),generation:0,busy:false,creds};
    runtime.current=r;
    setStatus('connecting');setError('');setPending({});setFeedback({});
    let ready=false, buffer=[];
    const client=new Client({brokerURL:brokerUrl(),connectHeaders:{'X-Session-Id':creds.sessionId,'X-Player-Token':creds.token},reconnectDelay:1000,heartbeatIncoming:10000,heartbeatOutgoing:10000});
    const end=message=>{if(r.ended)return;r.ended=true;save(null);showInvite(null);setStatus('ended');setError(message);client.deactivate();};
    r.end=end;
    const handle=event=>{
      if(event.type==='ACTION_FINISHED') r.finished.add(event.data.actionId);
      if(['SESSION_RESET','ROUND_OVER','PLAYER_JOINED','PLAYER_LEFT'].includes(event.type)){r.generation++;r.finished.clear();setFeedback({});}
      setGame(current=>current?applyEvent(current,event):current);
      if(event.type==='SESSION_ENDED') end(event.data.reason==='EXPIRED'?fatal.SESSION_NOT_FOUND:fatal.SESSION_ENDED);
    };
    client.onConnect=async()=>{
      ready=false;buffer=[];
      client.subscribe(topic(creds.sessionId),message=>{
        let event;try{event=JSON.parse(message.body);}catch{return;}
        if(ready)handle(event);else buffer.push(event);
      });
      try {
        const data=await request(sessionPath(creds.sessionId),{token:creds.token});
        if(!r.alive||r.ended)return;
        setGame(snapshot(data));buffer.forEach(handle);buffer=[];ready=true;
        setStatus('connected');setError('');
      } catch(e) { if(!r.alive)return; if(fatal[e.code])end(fatal[e.code]);else setError(e.message); }
    };
    client.onStompError=frame=>{const code=frame.headers.code;if(fatal[code])end(fatal[code]);else setError(frame.headers.message||'서버 연결에 문제가 생겼습니다.');};
    client.onWebSocketClose=()=>{if(r.alive&&!r.ended)setStatus('reconnecting');};
    client.activate();
    return ()=>{r.alive=false;client.deactivate();};
  },[creds]);
  async function command(characterId,text) {
    const r=runtime.current;
    if(status!=='connected'||r.busy||r.pending.has(characterId))return false;
    if(!text.trim()||text.trim().length>500){setFeedback(f=>({...f,[characterId]:{error:true,message:'1~500자의 명령을 입력해 주세요.'}}));return false;}
    const generation=r.generation;r.pending.add(characterId);setPending(Object.fromEntries([...r.pending].map(id=>[id,true])));
    try {
      const data=await request(`${sessionPath(r.creds.sessionId)}/characters/${encodeURIComponent(characterId)}/commands`,{method:'POST',body:{text},token:r.creds.token});
      if(!r.alive||r.ended||generation!==r.generation)return false;
      setFeedback(f=>({...f,[characterId]:{message:`“${text}” → ${data.action.actionType}`,actionId:data.action.id}}));
      setGame(g=>({...g,characters:g.characters.map(c=>c.id!==characterId||r.finished.has(data.action.id)||c.queue.some(a=>a.id===data.action.id)?c:{...c,queue:[...c.queue,data.action].sort((a,b)=>a.seq-b.seq)})}));
      return true;
    }catch(e){if(r.alive){setFeedback(f=>({...f,[characterId]:{error:true,message:e.message}}));if(fatal[e.code])r.end(fatal[e.code]);}return false;}
    finally{r.pending.delete(characterId);if(r.alive)setPending(Object.fromEntries([...r.pending].map(id=>[id,true])));}
  }
  async function reset() {
    const r=runtime.current;
    if(r.busy||r.pending.size)return;
    r.busy=true;setBusy(true);setError('');
    // The SESSION_RESET event carries the new state; the response is only an acknowledgement.
    try{await request(`${sessionPath(r.creds.sessionId)}/reset`,{method:'POST',token:r.creds.token});}
    catch(e){if(r.alive){setError(e.message);if(fatal[e.code])r.end(fatal[e.code]);}}
    finally{r.busy=false;if(r.alive)setBusy(false);}
  }
  async function leave() {
    const r=runtime.current;
    if(r.busy)return;
    r.busy=true;setBusy(true);
    try{await request(`${sessionPath(r.creds.sessionId)}/leave`,{method:'POST',token:r.creds.token});}
    catch(e){if(!fatal[e.code]){setError(e.message);r.busy=false;setBusy(false);return;}}
    r.alive=false;save(null);showInvite(null);
    setCreds(null);setGame(null);setStatus('lobby');setError('');setBusy(false);
  }
  function toLobby(){save(null);showInvite(null);setCreds(null);setGame(null);setStatus('lobby');setError('');}
  const notify=(characterId,message,error=false)=>setFeedback(f=>({...f,[characterId]:{error,message}}));
  return {game,status,error,feedback,pending,busy,command,reset,leave,toLobby,notify,
    start:mode=>enter(()=>request('/sessions',{method:'POST',body:{mode}})).catch(e=>setError(e.message))};
}
