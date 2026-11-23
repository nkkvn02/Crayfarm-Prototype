import {test} from 'node:test';
import assert from 'node:assert/strict';
import {requireAdmin, key, action, deviceAction, nextDeviceRecord, publicDevice, publicFarm, publicFormerWorker, publicMember, publicPond, publicUser, sameOperation} from './core';
import {execute, Effects} from './workflow';
test('only the boolean website claim authorizes; owner role and flags do not',()=>{
 for(const token of [{uid:'owner',role:'admin'},{uid:'worker'},{uid:'x',crayfarmAdmin:'true'},{uid:'x',crayfarmAdmin:false}]) assert.throws(()=>requireAdmin(token,false));
 assert.equal(requireAdmin({uid:'dev',crayfarmAdmin:true},false),'dev');
 assert.throws(()=>requireAdmin({uid:'dev',crayfarmAdmin:true},true));
});
test('path injection and unsupported operations are rejected',()=>{
 for(const v of ['', '../users', 'a/b', 'a.b', 2, null,'x'.repeat(129)])assert.throws(()=>key(v));
 assert.throws(()=>action('delete'));assert.throws(()=>action('promote'));
 assert.throws(()=>deviceAction('delete'));assert.equal(deviceAction('register'),'register');
});
test('device registration is allowlisted and idempotent',()=>{
 const input={deviceId:'CRAY-1',authUid:'auth-user',actor:'admin',operationId:'op-1',now:10};
 const record=nextDeviceRecord(null,'register',input);
 assert.equal(record.status,'available');assert.equal(record.active,true);assert.equal(nextDeviceRecord(record,'register',input),record);
 assert.throws(()=>nextDeviceRecord(record,'register',{...input,operationId:'op-2'}));
});
test('assigned devices cannot be deactivated and available devices can be toggled',()=>{
 const base={deviceId:'CRAY-1',authUid:'auth-user',active:true,status:'available',farmId:'',pondId:''};
 const off=nextDeviceRecord(base,'deactivate',{deviceId:'CRAY-1',actor:'admin',operationId:'op-2',now:20});
 assert.equal(off.active,false);assert.equal(off.status,'inactive');
 const on=nextDeviceRecord(off,'activate',{deviceId:'CRAY-1',actor:'admin',operationId:'op-3',now:30});
 assert.equal(on.active,true);assert.equal(on.status,'available');
 assert.throws(()=>nextDeviceRecord({...base,status:'assigned',farmId:'farm-1'},'deactivate',{deviceId:'CRAY-1',actor:'admin',operationId:'op-4',now:40}));
});
test('directory projection excludes secrets and preserves unknown fields',()=>{
 const result=publicUser({uid:'x',passwordHash:'secret',customClaims:{secret:'hidden'},metadata:{creationTime:'today'},disabled:false},null);
 assert.equal(result.profileExists,false);assert.equal(result.active,null);assert.equal(result.role,null);
 assert.ok(!JSON.stringify(result).includes('secret'));
});
test('former worker projection preserves history without secrets',()=>{
 const result=publicFormerWorker('worker',{fullName:'Former Worker',email:'former@example.com',role:'worker',employmentStatus:'removed',previousFarmId:'farm-1',removedAt:5,password:'secret'});
 assert.equal(result.historical,true);assert.equal(result.authExists,false);assert.equal(result.employmentStatus,'removed');assert.ok(!JSON.stringify(result).includes('secret'));
});
test('farm projections expose only allowlisted scalar fields',()=>{
 const secret={password:'hidden',authUid:'device-secret',photoBase64:'large'};
 const farm=publicFarm('farm-1',{farmName:'North Farm',description:'Demo',createdAt:5,...secret},{uid:'owner',name:'Owner',email:'owner@example.com'});
 const member=publicMember('worker',{fullName:'Worker',email:'worker@example.com',role:'worker',active:true,...secret});
 const pond=publicPond('pond-1',{pondName:'Pond 1',deviceId:'DEVICE-1',active:true,latest:{secret:'reading'},...secret});
 const device=publicDevice('DEVICE-1',{active:true,status:'assigned',farmId:'farm-1',pondId:'pond-1',authUid:'hidden'});
 const output=JSON.stringify({farm,member,pond,device});
 assert.ok(!output.includes('hidden'));assert.ok(!output.includes('photoBase64'));assert.ok(!output.includes('latest'));
 assert.equal(farm.ownerUid,'owner');assert.equal(pond.deviceId,'DEVICE-1');assert.equal(device.status,'assigned');
});
test('idempotency binds actor, target and action',()=>{
 const a={actorUid:'a',targetUid:'t',action:'disable'};
 assert.ok(sameOperation(a,{...a}));for(const field of Object.keys(a))assert.ok(!sameOperation(a,{...a,[field]:'different'}));
});
function effects(fail?:string){const steps:string[]=[];let mutated=false;const step=async(s:string)=>{steps.push(s);if(s===fail)throw new Error('injected');};const e:Effects={markRunning:()=>step('running'),block:()=>step('block'),mutate:async()=>{await step('mutate');mutated=true;},checkpoint:step,revoke:()=>step('revoke'),verify:async()=>{await step('verify');return true;},finish:()=>step('finish'),pending:()=>step('pending')};return {e,steps,mutated:()=>mutated};}
test('revocation barrier precedes claims and token changes',async()=>{const x=effects();assert.equal(await execute('revokeAdmin',x.e),'succeeded');assert.deepEqual(x.steps,['running','block','mutate','authMutation','revoke','sessionPolicy','verify','finish']);});
for(const failure of ['block','mutate','authMutation','revoke','sessionPolicy','verify','finish'])test(`failure at ${failure} remains recoverable`,async()=>{const x=effects(failure);assert.equal(await execute('revokeAdmin',x.e),'needs_reconciliation');assert.equal(x.steps.at(-1),'pending');if(failure==='block')assert.equal(x.mutated(),false);});
test('audit/result failure after Auth success can be retried',async()=>{const x=effects('finish');assert.equal(await execute('disable',x.e),'needs_reconciliation');assert.ok(x.mutated());const retry=effects();assert.equal(await execute('disable',retry.e),'succeeded');});
test('enable preserves profile state and does not run revocation',async()=>{const x=effects();await execute('enable',x.e);assert.ok(!x.steps.includes('revoke'));assert.ok(!x.steps.includes('block'));});
test('failure to persist reconciliation does not report success',async()=>{const x=effects('finish');x.e.pending=async()=>{throw Error('database down');};assert.equal(await execute('disable',x.e),'needs_reconciliation');});
