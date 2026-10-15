import { visualActor, MAX_HP } from './state.js';
export function createRenderer(canvas) {
const ctx = canvas.getContext('2d');
const W=960, H=700, STEP=58;
let GRID={x:103,y:89,cols:13,rows:9}, elapsed=0, clock=0;
const HIT_MS=600;
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
function drawCharacter(actor){
 const {x,y,angle,active,id,hp,dead,hit}=actor;
 const enemy=id==='enemy';
 const palette=enemy?
  {shadow:'#c9baa9',body:'#9c665e',coat:'#bd8174',boots:'#68494a',hat:'#754c4d',trim:'#e5b79e',trail:'157,91,84',label:'#9c665e',shield:'#d69e90'}:
  {shadow:'#b5c5a4',body:'#577665',coat:'#77917b',boots:'#405849',hat:'#405b4d',trim:'#b7ce8d',trail:'103,134,85',label:'#66815a',shield:'#7db4a0'};
 const t=active?Math.min(active.t/active.duration,1):0;
 const rolling=active?.type==='roll';
 if(rolling){for(let i=3;i>0;i--)ellipse(x+(active.sx-x)*i*.19,y+(active.sy-y)*i*.19,16-i*2,16-i*2,`rgba(${palette.trail},${.13-i*.025})`);}
 ellipse(x,y+16,24,10,palette.shadow);
 ctx.globalAlpha=dead?.45:1;
 ctx.save();ctx.translate(x,y);ctx.rotate(angle);if(dead)ctx.rotate(Math.PI/2);
 if(active?.type==='attack'){
  ctx.save();ctx.rotate(-1.2+t*2.4);ctx.strokeStyle=`rgba(246,255,222,${Math.sin(t*Math.PI)})`;ctx.lineWidth=16;ctx.beginPath();ctx.arc(0,0,48,-2.4,-.7);ctx.stroke();ctx.strokeStyle=enemy?'#d5a397':'#aecb76';ctx.lineWidth=3;ctx.stroke();ctx.restore();
 }
 if(active?.type==='defend'){
  ctx.shadowColor=palette.shield;ctx.shadowBlur=18;ctx.fillStyle=enemy?'#eed2c57a':'#c9eee77a';ctx.strokeStyle=palette.shield;ctx.lineWidth=2;ctx.beginPath();ctx.arc(0,0,35,Math.PI*1.05,Math.PI*1.95);ctx.quadraticCurveTo(0,-15,-34.6,-5.5);ctx.fill();ctx.stroke();ctx.shadowBlur=0;
 }
 if(rolling){ctx.rotate(t*Math.PI*2);ctx.scale(1,.76);}
 const bob=active?.type==='move'?Math.sin(t*Math.PI*4)*3:Math.sin(elapsed*2+(enemy?1:0))*1;
 ctx.translate(0,bob);
 ellipse(-9,14,6,7,palette.boots);ellipse(9,14,6,7,palette.boots);
 roundRect(-18,-6,36,28,12,palette.body);roundRect(-13,-8,26,25,10,palette.coat);
 ellipse(-20,4,6,8,'#e1c69e');ellipse(20,4,6,8,'#e1c69e');
 ctx.save();ctx.translate(23,0);if(active?.type==='attack')ctx.rotate(-1.7+t*3.4);
 roundRect(-2,-31,5,34,2,'#eff3db');roundRect(-6,-3,13,4,1,'#a6ac83');roundRect(-1,1,4,9,1,'#6a6347');ctx.restore();
 ellipse(0,-8,16,17,'#ead6ae');ellipse(0,-14,17,12,palette.hat);roundRect(-17,-16,34,9,5,palette.hat);
 ctx.fillStyle=palette.trim;ctx.beginPath();ctx.moveTo(-5,-21);ctx.lineTo(0,-31);ctx.lineTo(5,-21);ctx.fill();
 ctx.restore();
 ctx.globalAlpha=1;
 ctx.textAlign='center';ctx.font='600 9px sans-serif';ctx.fillStyle=palette.label;ctx.fillText((enemy?'ENEMY':'YOU')+(dead?' · KO':''),x,y-48);
 roundRect(x-17,y+34,34,4,2,'#dde3d6');
 if(hp>0)roundRect(x-17,y+34,34*hp/MAX_HP,4,2,hp<=30?'#c0604a':enemy?'#d4b1a9':'#88a66d');
 const age=hit?clock-hit.at:Infinity;
 if(age<HIT_MS){
  const k=age/HIT_MS;
  ctx.globalAlpha=1-k;
  if(hit.defended){ctx.strokeStyle=palette.shield;ctx.lineWidth=3;ctx.beginPath();ctx.arc(x,y,30+k*10,0,Math.PI*2);ctx.stroke();}
  else ellipse(x,y,26,26,'rgba(214,92,70,.35)');
  ctx.font='700 13px sans-serif';ctx.fillStyle=hit.defended?'#5f8f80':'#c0563f';ctx.fillText(hit.defended?'BLOCK':`-${hit.damage}`,x+22,y-30-k*18);
  ctx.globalAlpha=1;
 }
}

return (game, now) => {
 elapsed=now/1000;clock=now;
 GRID={x:(W-game.session.mapCols*STEP)/2,y:(H-game.session.mapRows*STEP)/2,cols:game.session.mapCols,rows:game.session.mapRows};
 drawMap();
 game.characters.map(c=>visualActor(c,now,GRID,STEP)).sort((a,b)=>a.y-b.y).forEach(drawCharacter);
};
}
