export const labels = { IDLE:'대기 중', MOVING:'이동 중', TURNING:'회전 중', ATTACKING:'공격 중', DEFENDING:'방어 중', ROLLING:'구르는 중' };
export const facings = { N:'북쪽 ↑', E:'동쪽 →', S:'남쪽 ↓', W:'서쪽 ←' };
const durations = {move_forward:320,move_backward:320,turn_left:300,turn_right:300,attack:480,defend:1100,roll:500};
const kind = type => type.startsWith('move_') ? 'move' : type.startsWith('turn_') ? 'turn' : type;
const states = {move:'MOVING',turn:'TURNING',attack:'ATTACKING',defend:'DEFENDING',roll:'ROLLING'};
export function snapshot(data) {
  return {...data, characters:data.characters.map(c => {
    const action = c.queue?.find(a => a.status === 'RUNNING');
    let animation = null;
    if (action) {
      let facing=c.facing;
      if (kind(action.actionType)==='turn') facing='NESW'[('NESW'.indexOf(facing)+(action.actionType==='turn_left'?3:1))%4];
      animation={action,from:{x:c.x,y:c.y,facing:c.facing},to:{x:action.targetX??c.x,y:action.targetY??c.y,facing},durationMs:durations[action.actionType],start:Date.parse(action.startedAt)};
    }
    return {...c,queue:c.queue||[],animation};
  })};
}
export function applyEvent(game, {type,data}) {
  if (type==='SESSION_RESET') return {...game,characters:snapshot({characters:data.characters}).characters};
  if (type==='SESSION_ENDED') return {...game,session:{...game.session,status:'ENDED'},characters:game.characters.map(c=>({...c,animation:null,queue:[]}))};
  return {...game,characters:game.characters.map(c=>{
    if(type==='ACTION_STARTED' && c.id===data.characterId) return {...c,...data.from,state:states[kind(data.action.actionType)],queue:[data.action,...c.queue.filter(a=>a.id!==data.action.id)],animation:{...data,start:Date.parse(data.action.startedAt)||Date.now()}};
    if(type==='ACTION_FINISHED' && c.id===data.character.id) return {...c,...data.character,queue:c.queue.filter(a=>a.id!==data.actionId),animation:null};
    return c;
  })};
}
export function visualActor(c,now,grid,step) {
  const a=c.animation;
  const p=a?Math.max(0,Math.min((now-a.start)/a.durationMs,1)):0, v=p*p*(3-2*p);
  const from=a?.from||c, to=a?.to||c;
  const angle='NESW'.indexOf(from.facing)*Math.PI/2;
  const turn=a && kind(a.action.actionType)==='turn' ? (a.action.actionType==='turn_left'?-1:1)*Math.PI/2*v:0;
  return {id:c.type.toLowerCase(),x:grid.x+(from.x+(to.x-from.x)*v+.5)*step,y:grid.y+(from.y+(to.y-from.y)*v+.5)*step,angle:angle+turn,
    active:a?{type:kind(a.action.actionType),t:p*a.durationMs/1000,duration:a.durationMs/1000,sx:grid.x+(from.x+.5)*step,sy:grid.y+(from.y+.5)*step}:null};
}
