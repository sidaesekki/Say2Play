import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { useGame } from './useGame.js';
import { createRenderer } from './renderer.js';
import { facings, labels } from './state.js';
import '../style.css';
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
function CommandPanel({character,type,index,client}) {
  const [text,setText]=useState('');
  const enemy=type==='ENEMY', name=enemy?'적':'플레이어';
  const disabled=client.status!=='connected'||client.busy||!character||client.pending[character?.id];
  const feedback=client.feedback[character?.id];
  async function submit(e){e.preventDefault();if(!disabled&&await client.command(character.id,text))setText('');}
  return <section className={`command-panel ${enemy?'enemy-panel':''}`} aria-label={`${name} 명령`}>
    <div className="section-title"><span className="number">0{index+1}</span><h2>{name} 명령</h2><span className="keyboard">↵</span></div>
    <p className="description">입력 한 건을 AI가 동작 하나로 해석합니다.</p>
    <form onSubmit={submit}><label className="sr-only" htmlFor={`${type}-command`}>{name} 동작</label>
      <textarea id={`${type}-command`} rows="2" maxLength={500} value={text} disabled={disabled} onChange={e=>setText(e.target.value)} placeholder={enemy?'예: 아래쪽으로 한 발짝 가볼까':'예: 오른쪽으로 돌아봐'} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();e.currentTarget.form.requestSubmit();}}}/>
      <div className="input-hint">Enter 실행 <span>Shift + Enter 줄바꿈</span></div>
      <button className="run" disabled={disabled} type="submit"><span>{client.pending[character?.id]?'명령 해석 중…':`${name} 명령 실행`}</span><span>↗</span></button>
    </form>
    <p className={`feedback ${feedback?.error?'error':''}`} role="status">{feedback?.message||`${name} 명령을 입력해 주세요.`}</p>
    <p className="queue-count">실행·대기 동작 {character?.queue.length||0} / 100</p>
  </section>;
}
function App(){
 const client=useGame();const {game,status,error,busy,pending}=client;
 const connected=status==='connected';
 return <><header><a className="brand" href="./"><span className="brand-icon">S<span>↗</span></span>Say2Play<span className="tag">PLAYGROUND</span></a><span className="connection"><i/>{({connected:'서버 연결됨',connecting:'서버 연결 중',reconnecting:'다시 연결 중',ended:'게임 종료',error:'연결 실패'})[status]}</span></header>
 <main><section className="intro"><div><p className="eyebrow">YOUR WORDS. YOUR MOVES.</p><h1>한 줄의 명령, 하나의 모험.</h1><p className="subtitle">두 캐릭터의 움직임을 각각 입력해 보세요. 당신의 말이 플레이가 됩니다.</p></div><span className="version">01 / TRAINING GROUND</span></section>
 {error&&<p className="connection-error" role="alert">{error}</p>}
 {(status==='error'||status==='ended')&&<button className="session-button" onClick={client.retry}>{status==='ended'?'새 게임 시작':'다시 연결'}</button>}
 <div className="workspace"><section className="arena-panel" aria-label="게임 화면"><div className="panel-heading"><span><i className="live-dot"/> 자유 훈련장</span><span className="quiet">TOP-DOWN SANDBOX</span></div>
 <div className="canvas-wrap"><Arena game={game}/><div className="map-label"><span>SECTOR 01</span><small>THE QUIET GARDEN</small></div><div className="map-help">{game?'플레이어와 적에게 각각 명령을 내려 보세요':'서버에 연결하면 훈련장이 표시됩니다.'}</div><button className="reset" disabled={!connected||busy||Object.keys(pending).length>0} onClick={client.reset}>↺ 초기화</button></div>
 <div className="statusbar">{game?.characters.map(c=><div key={c.id} className={`actor-status ${c.type==='ENEMY'?'enemy-status':''}`}><span className="actor-name"><span className="avatar-dot"/>{c.type} 01 <b className="state">{labels[c.state]}</b></span><span className="coordinates">X <b>{c.x}</b><em>/</em> Y <b>{c.y}</b><em>/</em><b>{facings[c.facing]}</b></span></div>)}</div></section>
 <aside>{['PLAYER','ENEMY'].map((type,index)=><CommandPanel key={`${game?.session.id}-${type}`} type={type} index={index} character={game?.characters.find(c=>c.type===type)} client={client}/>)}</aside></div>
 <footer><span><i/> TWO CHARACTERS. YOUR COMMANDS.</span><button className="session-button" disabled={!connected||busy||Object.keys(pending).length>0} onClick={client.leave}>게임 나가기</button></footer></main></>;
}
createRoot(document.getElementById('root')).render(<App/>);
