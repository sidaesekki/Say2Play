import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { useGame } from './useGame.js';
import { createRenderer } from './renderer.js';
import { inviteUrl } from './api.js';
import { facings, labels, MAX_HP } from './state.js';
import '../style.css';
const names={PLAYER:'플레이어',ENEMY:'적'};
function useNow(active){
  const [now,setNow]=useState(Date.now());
  useEffect(()=>{if(!active)return;const id=setInterval(()=>setNow(Date.now()),200);return()=>clearInterval(id);},[active]);
  return now;
}
function Arena({game}) {
  const canvas=useRef(null), latest=useRef(game);
  useEffect(()=>{latest.current=game;},[game]);
  useEffect(()=>{
    const draw=createRenderer(canvas.current);let frame;
    const render=()=>{if(latest.current)draw(latest.current,Date.now());frame=requestAnimationFrame(render);};
    render();return()=>cancelAnimationFrame(frame);
  },[]);
  return <canvas ref={canvas} width="960" height="700" aria-label="플레이어와 적 캐릭터를 텍스트 명령으로 조종하는 탑뷰 훈련장"/>;
}
function CommandPanel({character,type,index,client,blocked}) {
  const [text,setText]=useState('');
  const enemy=type==='ENEMY', name=names[type];
  const dead=character?.state==='DEAD';
  const disabled=client.status!=='connected'||client.busy||!character||dead||!!blocked||client.pending[character?.id];
  const feedback=blocked?{message:blocked}:dead?{error:true,message:`${enemy?'적이':'플레이어가'} 쓰러졌어요.`}:client.feedback[character?.id];
  async function submit(e){e.preventDefault();if(!disabled&&await client.command(character.id,text))setText('');}
  return <section className={`command-panel ${enemy?'enemy-panel':''}`} aria-label={`${name} 명령`}>
    <div className="section-title"><span className="number">0{index+1}</span><h2>{name} 명령</h2><span className="keyboard">↵</span></div>
    <p className="description">입력 한 건을 AI가 동작 하나로 해석합니다.</p>
    <form onSubmit={submit}><label className="sr-only" htmlFor={`${type}-command`}>{name} 동작</label>
      <textarea id={`${type}-command`} rows="2" maxLength={500} value={text} readOnly={disabled} aria-disabled={disabled} onChange={e=>setText(e.target.value)} placeholder={enemy?'예: 앞으로 가':'예: 오른쪽으로 돌아봐'} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();e.currentTarget.form.requestSubmit();}}}/>
      <div className="input-hint">Enter 실행 <span>Shift + Enter 줄바꿈</span></div>
      <button className="run" disabled={disabled} type="submit"><span>{client.pending[character?.id]?'명령 해석 중…':`${name} 명령 실행`}</span><span>↗</span></button>
    </form>
    <p className={`feedback ${feedback?.error?'error':''}`} role="status">{feedback?.message||`${name} 명령을 입력해 주세요.`}</p>
    <p className="queue-count">실행·대기 동작 {character?.queue.length||0} / 100</p>
  </section>;
}
function Invite({id}) {
  const [copied,setCopied]=useState(false);
  const link=inviteUrl(id);
  async function copy(){try{await navigator.clipboard.writeText(link);setCopied(true);setTimeout(()=>setCopied(false),1500);}catch{setCopied(false);}}
  return <div className="invite"><input readOnly value={link} onFocus={e=>e.target.select()} aria-label="초대 링크"/><button className="session-button" onClick={copy}>{copied?'복사됨':'링크 복사'}</button></div>;
}
function Lobby({client}) {
  return <section className="lobby" aria-label="게임 시작">
    <button className="lobby-card" disabled={client.busy} onClick={()=>client.start('SOLO')}><span className="number">01</span><b>혼자 하기</b><span>플레이어와 적을 모두 직접 조종합니다.</span></button>
    <button className="lobby-card enemy-panel" disabled={client.busy} onClick={()=>client.start('MULTI')}><span className="number">02</span><b>친구와 하기</b><span>방을 만들고 초대 링크를 보내 1:1로 겨룹니다.</span></button>
  </section>;
}
function Overlay({game,me}) {
  const round=game.round, now=useNow(!!round);
  if(game.session.status==='WAITING') return <div className="result-banner" role="status"><b>상대를 기다리는 중</b><span>아래 링크를 친구에게 보내 주세요.</span><Invite id={game.session.id}/></div>;
  if(game.session.status!=='ROUND_OVER') return null;
  const winner=game.characters.find(c=>c.id===round?.winnerId)||game.characters.find(c=>c.state!=='DEAD');
  const title=game.session.mode==='MULTI'?(me.controls.includes(winner?.type)?'승리!':'패배'):`${names[winner?.type]||''} 승리!`;
  const seconds=round?Math.max(0,Math.ceil((round.resetAt-now)/1000)):null;
  return <div className="result-banner" role="status"><b>{title}</b><span>{seconds!==null?`${seconds}초 뒤 새 판이 시작됩니다`:'곧 새 판이 시작됩니다'}</span></div>;
}
function App(){
 const client=useGame();const {game,status,error,busy,pending}=client;
 const connected=status==='connected', me=game?.me||{controls:[]}, multi=game?.session.mode==='MULTI';
 const blocked=game?.session.status==='WAITING'?'상대가 들어오면 시작합니다.':game?.session.status==='ROUND_OVER'?'잠시 뒤 새 판이 시작됩니다.':null;
 const seat=c=>multi?(me.controls.includes(c.type)?'YOU':(()=>{const p=game.players?.find(p=>p.controls.includes(c.type));return !p?'빈 자리':p.connected?'상대':'상대 · 연결 끊김';})()):null;
 return <><header><a className="brand" href="./"><span className="brand-icon">S<span>↗</span></span>Say2Play<span className="tag">{multi?'1:1 MATCH':'PLAYGROUND'}</span></a><span className="connection"><i/>{({loading:'불러오는 중',lobby:'대기실',connected:'서버 연결됨',connecting:'서버 연결 중',reconnecting:'다시 연결 중',ended:'게임 종료',error:'연결 실패'})[status]}</span></header>
 <main><section className="intro"><div><p className="eyebrow">YOUR WORDS. YOUR MOVES.</p><h1>한 줄의 명령, 하나의 모험.</h1><p className="subtitle">{multi?'내 캐릭터에게 명령해 상대를 쓰러뜨리세요.':'두 캐릭터의 움직임을 각각 입력해 보세요. 당신의 말이 플레이가 됩니다.'}</p></div><span className="version">01 / TRAINING GROUND</span></section>
 {error&&<p className="connection-error" role="alert">{error}</p>}
 {status==='ended'&&<button className="session-button" onClick={client.toLobby}>처음으로</button>}
 {status==='lobby'&&<Lobby client={client}/>}
 {game&&status!=='ended'&&<><div className="workspace"><section className="arena-panel" aria-label="게임 화면"><div className="panel-heading"><span><i className="live-dot"/> {multi?'1:1 대전':'자유 훈련장'}</span><span className="quiet">TOP-DOWN SANDBOX</span></div>
 <div className="canvas-wrap"><Arena game={game}/><div className="map-label"><span>SECTOR 01</span><small>THE QUIET GARDEN</small></div><div className="map-help">{multi?'내 캐릭터에게 명령을 내려 보세요':'플레이어와 적에게 각각 명령을 내려 보세요'}</div><Overlay game={game} me={me}/>{!multi&&<button className="reset" disabled={!connected||busy||Object.keys(pending).length>0} onClick={client.reset}>↺ 초기화</button>}</div>
 <div className="statusbar">{game.characters.map(c=><div key={c.id} className={`actor-status ${c.type==='ENEMY'?'enemy-status':''}`}><span className="actor-name"><span className="avatar-dot"/>{c.type} 01 {seat(c)&&<em className="seat">{seat(c)}</em>}<b className="state">{labels[c.state]}</b></span><span className="hp" aria-label={`HP ${c.hp??MAX_HP} / ${MAX_HP}`}>HP <i className="hp-bar"><i style={{width:`${(c.hp??MAX_HP)/MAX_HP*100}%`}}/></i><b>{c.hp??MAX_HP}</b></span><span className="coordinates">X <b>{c.x}</b><em>/</em> Y <b>{c.y}</b><em>/</em><b>{facings[c.facing]}</b></span></div>)}</div></section>
 <aside>{['PLAYER','ENEMY'].filter(type=>me.controls.includes(type)).map((type,index)=><CommandPanel key={`${game.session.id}-${type}`} type={type} index={index} blocked={blocked} character={game.characters.find(c=>c.type===type)} client={client}/>)}
 {multi&&game.session.status!=='WAITING'&&<section className="command-panel invite-panel"><div className="section-title"><h2>초대 링크</h2></div><p className="description">상대가 나가면 같은 링크로 새 상대를 받을 수 있어요.</p><Invite id={game.session.id}/></section>}</aside></div>
 <footer><span><i/> {multi?'ONE CHARACTER EACH. YOUR COMMANDS.':'TWO CHARACTERS. YOUR COMMANDS.'}</span><button className="session-button" disabled={busy} onClick={client.leave}>게임 나가기</button></footer></>}
 </main></>;
}
createRoot(document.getElementById('root')).render(<App/>);
