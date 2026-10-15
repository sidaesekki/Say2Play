import test from 'node:test';
import assert from 'node:assert/strict';
import { snapshot, applyEvent, visualActor } from './state.js';
const character={id:'player',type:'PLAYER',x:6,y:7,facing:'N',state:'IDLE'};
const initial=()=>snapshot({session:{id:'session',status:'PLAYING',mapCols:13,mapRows:9},characters:[character]});
const action={id:'a',seq:1,actionType:'move_forward',status:'RUNNING',startedAt:new Date(1000).toISOString()};
const start={type:'ACTION_STARTED',data:{characterId:'player',action,from:{x:6,y:7,facing:'N'},to:{x:6,y:6,facing:'N'},durationMs:320}};
test('server movement interpolates without completing or advancing the local queue',()=>{
 const game=applyEvent(initial(),start);
 const actor=visualActor(game.characters[0],1160,{x:0,y:0},10);
 assert.equal(actor.y,70);
 assert.equal(game.characters[0].y,7);
 assert.equal(game.characters[0].state,'MOVING');
 assert.equal(visualActor(game.characters[0],2000,{x:0,y:0},10).y,65);
 assert.equal(game.characters[0].queue.length,1);
 const finished=applyEvent(game,{type:'ACTION_FINISHED',data:{actionId:'a',character:{...character,y:6},queueLength:0}});
 assert.equal(finished.characters[0].y,6);assert.equal(finished.characters[0].animation,null);assert.deepEqual(finished.characters[0].queue,[]);
});
test('blocked movement stays at server coordinates',()=>{
 const game=applyEvent(initial(),{...start,data:{...start.data,to:start.data.from,action:{...action,blocked:true}}});
 assert.equal(visualActor(game.characters[0],1160,{x:0,y:0},10).y,75);
});
test('restored running action uses startedAt and target from snapshot',()=>{
 const game=snapshot({characters:[{...character,state:'MOVING',queue:[{...action,targetX:6,targetY:6}]}]});
 assert.equal(visualActor(game.characters[0],1160,{x:0,y:0},10).y,70);
});
test('left rotation from north takes counterclockwise quarter turn',()=>{
 const game=applyEvent(initial(),{...start,data:{...start.data,action:{...action,actionType:'turn_left'},to:{x:6,y:7,facing:'W'},durationMs:300}});
 assert.equal(visualActor(game.characters[0],1300,{x:0,y:0},10).angle,-Math.PI/2);
});
test('reset and ended events clear active animations and queues',()=>{
 const game=applyEvent(initial(),start);
 const reset=applyEvent(game,{type:'SESSION_RESET',data:{characters:[character]}});
 assert.equal(reset.characters[0].animation,null);assert.deepEqual(reset.characters[0].queue,[]);
 const ended=applyEvent(game,{type:'SESSION_ENDED',data:{reason:'EXPIRED'}});
 assert.equal(ended.session.status,'ENDED');assert.equal(ended.characters[0].animation,null);
});
