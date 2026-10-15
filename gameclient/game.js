/* Canvas world coordinates stay fixed, independently of screen size. */
(() => {
'use strict';
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
const input = document.getElementById('command');
const feedback = document.getElementById('feedback');
const history = document.getElementById('history');
const W = 960, H = 700, STEP = 58;
const GRID = {x:103,y:89,cols:13,rows:9};
const CELL = {minX:GRID.x+STEP/2,maxX:GRID.x+(GRID.cols-.5)*STEP,minY:GRID.y+STEP/2,maxY:GRID.y+(GRID.rows-.5)*STEP};
const directions = [[0,-1],[1,0],[0,1],[-1,0]];
const names = ['북쪽 ↑','동쪽 →','남쪽 ↓','서쪽 ←'];
const player = {x:480,y:350,dir:0,angle:0};
let queue = [], active = null, count = 0, lastTime = 0, elapsed = 0;
const clamp = (v,min,max) => Math.max(min,Math.min(max,v));
const ease = t => t*t*(3-2*t);
function parseCommands(text) {
  const pieces = text.trim().split(/[,，;；\n]+/).map(s=>s.trim()).filter(Boolean);
  if (!pieces.length) throw new Error('실행할 명령을 입력해 주세요.');
  const actions = [];
  for (const piece of pieces) {
    const match = piece.match(/^(.*?)(?:\s*(\d+)\s*(?:칸|번|회)?)?$/);
    const word = match[1].replace(/\s+/g,'').toLowerCase();
    const amount = match[2] === undefined ? 1 : Number(match[2]);
    if (amount < 1 || amount > 20) throw new Error('숫자는 1부터 20까지 입력해 주세요.');
    let type, dir;
    if (/^(위|위로|위로이동|위쪽|위쪽으로이동|상|up|북쪽|북쪽으로이동)$/.test(word)) {type='move';dir=0;}
    else if (/^(오른쪽|오른쪽으로|오른쪽이동|오른쪽으로이동|우|right|동쪽)$/.test(word)) {type='move';dir=1;}
    else if (/^(아래|아래로|아래로이동|아래쪽|아래쪽으로이동|하|down|남쪽)$/.test(word)) {type='move';dir=2;}
    else if (/^(왼쪽|왼쪽으로|왼쪽이동|왼쪽으로이동|좌|left|서쪽)$/.test(word)) {type='move';dir=3;}
    else if (/^(오른쪽으로돌기|오른쪽돌기|오른쪽회전|우회전|turnright)$/.test(word)) {type='turn';dir=1;}
    else if (/^(왼쪽으로돌기|왼쪽돌기|왼쪽회전|좌회전|turnleft)$/.test(word)) {type='turn';dir=-1;}
    else if (/^(공격|공격하기|attack)$/.test(word)) type='attack';
    else if (/^(방어|방어하기|막기|defend|block)$/.test(word)) type='defend';
    else if (/^(구르기|구르기하기|굴러|회피|roll|dodge)$/.test(word)) type='roll';
    else throw new Error(`“${piece}” 명령을 이해하지 못했어요. 아래 예시를 확인해 주세요.`);
    for(let i=0;i<amount;i++) actions.push({type,dir,label:piece});
  }
  if (actions.length > 60) throw new Error('한 번에 최대 60개의 동작을 실행할 수 있어요.');
  return actions;
}
function submit(text) {
  try {
    const actions = parseCommands(text);
    if(queue.length + actions.length + (active?1:0)>100) throw new Error('대기 중인 동작이 많아요. 잠시 뒤 다시 입력해 주세요.');
    queue.push(...actions);
    feedback.className='';feedback.textContent=`${actions.length}개 동작을 추가했어요. 순서대로 실행합니다.`;
    input.value='';
  } catch(error) {feedback.className='error';feedback.textContent=error.message;}
}
document.getElementById('command-form').addEventListener('submit',e=>{e.preventDefault();submit(input.value);});
input.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();submit(input.value);}});
document.querySelectorAll('[data-command]').forEach(button=>button.addEventListener('click',()=>submit(button.dataset.command)));
document.getElementById('reset').addEventListener('click',()=>{
  queue=[];active=null;Object.assign(player,{x:480,y:350,dir:0,angle:0});count=0;
  history.innerHTML='<li class="empty">당신의 첫 움직임을 기다리고 있어요.</li>';
  document.getElementById('count').textContent='0 ACTIONS';feedback.className='';feedback.textContent='훈련장을 초기화했어요. 새로운 명령을 입력해 주세요.';input.value='';updateStatus();
});
const actionNames={move:'이동 중',turn:'회전 중',attack:'공격 중',defend:'방어 중',roll:'구르는 중'};
function updateStatus(){
  document.getElementById('state').textContent=active?actionNames[active.type]:'대기 중';
  document.getElementById('pos-x').textContent=((player.x-480)/STEP).toFixed(1);
  document.getElementById('pos-y').textContent=((350-player.y)/STEP).toFixed(1);
  document.getElementById('facing').textContent=names[player.dir];
}
function start(action){
  active={...action,t:0,duration:{move:.32,turn:.3,attack:.48,defend:1.1,roll:.5}[action.type],sx:player.x,sy:player.y,sa:player.angle};
  if(action.type==='move'||action.type==='roll'){
    const d=directions[action.type==='move'?action.dir:player.dir];
    const length=action.type==='roll'?STEP*2:STEP;
    active.tx=clamp(player.x+d[0]*length,CELL.minX,CELL.maxX);active.ty=clamp(player.y+d[1]*length,CELL.minY,CELL.maxY);
  }
  if(action.type==='turn'){player.dir=(player.dir+action.dir+4)%4;active.ta=player.angle+action.dir*Math.PI/2;}
  updateStatus();
}
function finish(){
  count++;
  if(count===1) history.innerHTML='';
  const li=document.createElement('li');const label=document.createElement('span');const time=document.createElement('time');
  label.textContent=`✓ ${active.label}`;time.textContent=new Date().toLocaleTimeString('ko-KR',{hour12:false,hour:'2-digit',minute:'2-digit',second:'2-digit'});
  li.append(label,time);history.prepend(li);while(history.children.length>20)history.lastChild.remove();
  document.getElementById('count').textContent=`${count} ACTIONS`;active=null;
  if(!queue.length){feedback.className='';feedback.textContent='모든 동작을 마쳤어요. 다음 명령을 입력해 주세요.';}
  updateStatus();
}
function ellipse(x,y,rx,ry,color){ctx.fillStyle=color;ctx.beginPath();ctx.ellipse(x,y,rx,ry,0,0,Math.PI*2);ctx.fill();}
function roundRect(x,y,w,h,r,fill){ctx.fillStyle=fill;ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill();}
function drawMap(){
 ctx.fillStyle='#e2e9d8';ctx.fillRect(0,0,W,H);
 ctx.strokeStyle='#d6dfcb';ctx.lineWidth=1;
 for(let x=16;x<W;x+=58){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,H);ctx.stroke();}
 for(let y=2;y<H;y+=58){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(W,y);ctx.stroke();}
 roundRect(89,75,782,550,25,'#d2ddc7');roundRect(95,81,770,538,22,'#e8eddf');
 ctx.save();ctx.beginPath();ctx.roundRect(95,81,770,538,22);ctx.clip();
 for(let row=0;row<GRID.rows;row++)for(let col=0;col<GRID.cols;col++){
  const x=GRID.x+col*STEP,y=GRID.y+row*STEP;
  roundRect(x,y,56,56,5,(row+col)%2===0?'#e2e8d8':'#e5ebdc');
  ctx.fillStyle='#d3dec7';ctx.fillRect(x+27,y+27,2,2);
 }
 ctx.restore();
 ctx.setLineDash([5,9]);ctx.strokeStyle='#cbd8bf';ctx.beginPath();ctx.arc(480,350,112,0,Math.PI*2);ctx.stroke();ctx.setLineDash([]);
 ctx.strokeStyle='#c9d7bd';ctx.beginPath();ctx.moveTo(464,350);ctx.lineTo(496,350);ctx.moveTo(480,334);ctx.lineTo(480,366);ctx.stroke();
 // Low garden borders and stones live outside the playable area.
 const bushes=[[38,145],[36,183],[29,482],[40,520],[922,205],[924,240],[925,555],[153,658],[189,659],[765,36],[799,39]];
 bushes.forEach(([x,y],i)=>{ellipse(x+2,y+9,23,13,'#bacbad');ellipse(x,y,22,20,'#b6cba0');ellipse(x-6,y-6,13,12,'#c5d6b2');ellipse(x+9,y+1,13,12,'#adc397');});
 [[143,44],[340,660],[853,660],[920,390],[40,324]].forEach(([x,y])=>{ellipse(x,y+4,15,8,'#c4cfb9');ellipse(x,y,14,8,'#d5ddcc');});
 for(let i=0;i<36;i++){const x=(i*139+54)%940,y=i%2?24+(i*7)%30:654+(i*5)%30;ctx.strokeStyle='#b2c59e';ctx.beginPath();ctx.moveTo(x-3,y-3);ctx.lineTo(x,y+4);ctx.lineTo(x+4,y-5);ctx.stroke();}
 ctx.fillStyle='#8ba078';ctx.font='9px monospace';ctx.textAlign='center';ctx.fillText('N',480,72);ctx.fillText('S',480,640);ctx.fillText('W',79,354);ctx.fillText('E',881,354);
}
function drawPlayer(){
 const t=active?Math.min(active.t/active.duration,1):0;
 const rolling=active?.type==='roll';
 if(rolling){for(let i=3;i>0;i--)ellipse(player.x+(active.sx-player.x)*i*.19,player.y+(active.sy-player.y)*i*.19,16-i*2,16-i*2,`rgba(103,134,85,${.13-i*.025})`);}
 ellipse(player.x,player.y+16,24,10,'#b5c5a4');
 ctx.save();ctx.translate(player.x,player.y);ctx.rotate(player.angle);
 if(active?.type==='attack'){
  ctx.save();ctx.rotate(-1.2+t*2.4);ctx.strokeStyle=`rgba(246,255,222,${Math.sin(t*Math.PI)})`;ctx.lineWidth=16;ctx.beginPath();ctx.arc(0,0,48,-2.4,-.7);ctx.stroke();ctx.strokeStyle='#aecb76';ctx.lineWidth=3;ctx.stroke();ctx.restore();
 }
 if(active?.type==='defend'){
  ctx.shadowColor='#a1cebd';ctx.shadowBlur=18;ctx.fillStyle='#c9eee77a';ctx.strokeStyle='#7db4a0';ctx.lineWidth=2;ctx.beginPath();ctx.arc(0,0,35,Math.PI*1.05,Math.PI*1.95);ctx.quadraticCurveTo(0,-15,-34.6,-5.5);ctx.fill();ctx.stroke();ctx.shadowBlur=0;
 }
 if(rolling){ctx.rotate(t*Math.PI*2);ctx.scale(1,.76);}
 const bob=active?.type==='move'?Math.sin(t*Math.PI*4)*3:Math.sin(elapsed*2)*1;
 ctx.translate(0,bob);
 ellipse(-9,14,6,7,'#405849');ellipse(9,14,6,7,'#405849');
 roundRect(-18,-6,36,28,12,'#577665');roundRect(-13,-8,26,25,10,'#77917b');
 ellipse(-20,4,6,8,'#e1c69e');ellipse(20,4,6,8,'#e1c69e');
 ctx.save();ctx.translate(23,0);if(active?.type==='attack')ctx.rotate(-1.7+t*3.4);
 roundRect(-2,-31,5,34,2,'#eff3db');roundRect(-6,-3,13,4,1,'#a6ac83');roundRect(-1,1,4,9,1,'#6a6347');ctx.restore();
 ellipse(0,-8,16,17,'#ead6ae');ellipse(0,-14,17,12,'#405b4d');roundRect(-17,-16,34,9,5,'#405b4d');
 ctx.fillStyle='#b7ce8d';ctx.beginPath();ctx.moveTo(-5,-21);ctx.lineTo(0,-31);ctx.lineTo(5,-21);ctx.fill();
 ctx.restore();
 ctx.textAlign='center';ctx.font='600 9px sans-serif';ctx.fillStyle='#66815a';ctx.fillText('YOU',player.x,player.y-48);
 roundRect(player.x-17,player.y+34,34,4,2,'#c4d1b5');roundRect(player.x-17,player.y+34,34,4,2,'#88a66d');
}
function frame(timestamp){
 const dt=Math.min((timestamp-lastTime)/1000||0,.05);lastTime=timestamp;elapsed+=dt;
 if(!active&&queue.length)start(queue.shift());
 if(active){active.t+=dt;const p=clamp(active.t/active.duration,0,1),v=ease(p);
  if(active.type==='move'||active.type==='roll'){player.x=active.sx+(active.tx-active.sx)*v;player.y=active.sy+(active.ty-active.sy)*v;}
  if(active.type==='turn')player.angle=active.sa+(active.ta-active.sa)*v;
  updateStatus();if(p>=1)finish();
 }
 drawMap();drawPlayer();requestAnimationFrame(frame);
}
updateStatus();requestAnimationFrame(frame);
})();
