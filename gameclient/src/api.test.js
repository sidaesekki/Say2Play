import test from 'node:test';
import assert from 'node:assert/strict';
import {request, socketUrl} from './api.js';
test('REST sends original text and unwraps the API envelope',async t=>{
 t.mock.method(globalThis,'fetch',async(url,options)=>{
 assert.equal(url,'/api/v1/sessions/s/characters/p/commands');assert.equal(options.method,'POST');
 assert.deepEqual(JSON.parse(options.body),{text:'앞으로 가서 공격'});
 return new Response(JSON.stringify({success:true,data:{action:{id:'a'}}}),{status:201});
 });
 assert.deepEqual(await request('/sessions/s/characters/p/commands',{method:'POST',body:{text:'앞으로 가서 공격'}}),{action:{id:'a'}});
});
test('204 session deletion does not attempt JSON parsing',async t=>{
 t.mock.method(globalThis,'fetch',async()=>new Response(null,{status:204}));
 assert.equal(await request('/sessions/s',{method:'DELETE'}),null);
});
test('API error code and message survive, with no automatic command retry',async t=>{
 let calls=0;
 t.mock.method(globalThis,'fetch',async()=>{calls++;return new Response(JSON.stringify({success:false,error:{code:'AI_TIMEOUT',message:'AI 응답 시간 초과'}}),{status:504});});
 await assert.rejects(request('/sessions/s/characters/p/commands',{method:'POST',body:{text:'공격'}}),e=>e.code==='AI_TIMEOUT'&&e.message==='AI 응답 시간 초과');assert.equal(calls,1);
});
test('WebSocket uses wss on HTTPS and the documented path',()=>{
 globalThis.window={location:{origin:'https://game.example'}};
 assert.equal(socketUrl('session'),'wss://game.example/ws/sessions/session');
 delete globalThis.window;
});
