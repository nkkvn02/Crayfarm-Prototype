import assert from 'node:assert/strict';
import {localAdmin} from './admin.mjs';
const {auth,db}=localAdmin();
const suffix=Date.now(), password='Emulator-test-only-927!';
const ids={admin:`test-admin-${suffix}`,owner:`test-owner-${suffix}`,worker:`test-worker-${suffix}`,target:`test-target-${suffix}`,second:`test-second-${suffix}`};
let passed=0;
async function check(name,fn){await fn();passed++;console.log(`PASS ${name}`);}
async function login(uid){const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`${uid}@example.test`,password,returnSecureToken:true})});const x=await r.json();assert.ok(x.idToken,JSON.stringify(x));return x.idToken;}
async function call(name,token,data={}){const r=await fetch(`http://127.0.0.1:5001/demo-crayfarm/asia-southeast1/${name}`,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify({data})});return await r.json();}
try{
 for(const uid of Object.values(ids))await auth.createUser({uid,email:`${uid}@example.test`,password});
 await auth.setCustomUserClaims(ids.admin,{crayfarmAdmin:true});await auth.setCustomUserClaims(ids.second,{crayfarmAdmin:true,unrelated:'preserved'});
 await db.ref(`users/${ids.owner}`).set({role:'admin',active:true,approved:true,farmId:'test-farm'});
 await db.ref(`users/${ids.worker}`).set({role:'worker',active:true,approved:false,farmId:''});
 const admin=await login(ids.admin),owner=await login(ids.owner),worker=await login(ids.worker),second=await login(ids.second);
 await check('unauthenticated denied',async()=>assert.ok((await call('directory',null)).error));
 await check('owner role admin denied',async()=>assert.ok((await call('directory',owner)).error));
 await check('unapproved worker denied, forged flag ignored',async()=>assert.ok((await call('directory',worker,{crayfarmAdmin:true})).error));
 await check('admin allowed and missing profile retained',async()=>{const r=await call('directory',admin,{search:ids.target,searchBy:'uid'});assert.equal(r.result.users[0].profileExists,false);});
 await check('browser null cursor is accepted for directory and counts',async()=>{const r=await call('directory',admin,{cursor:null,search:'',role:'',status:''});assert.ok(Array.isArray(r.result.users));assert.ok((await call('overview',admin,{cursor:null})).result.total>=5);});
 await check('invalid path and unsupported role operation rejected',async()=>{assert.ok((await call('mutateAccount',admin,{targetUid:'../users',action:'disable',operationId:'invalid'})).error);assert.ok((await call('mutateAccount',admin,{targetUid:ids.target,action:'promote',operationId:'invalid'})).error);});
 const op={operationId:`${suffix}_disable`,targetUid:ids.target,action:'disable'};
 await check('disable and duplicate request produce one audit event',async()=>{assert.equal((await call('mutateAccount',admin,op)).result.status,'succeeded');assert.equal((await call('mutateAccount',admin,op)).result.status,'succeeded');assert.equal((await auth.getUser(ids.target)).disabled,true);assert.ok((await db.ref(`_crayfarmAdmin/audit/${op.operationId}`).get()).exists());});
 await check('operation ID cannot be rebound',async()=>assert.ok((await call('mutateAccount',admin,{...op,action:'enable'})).error));
 await check('enable preserves unapproved profile and missing farm',async()=>{await db.ref(`users/${ids.target}`).set({active:false,approved:false,farmId:''});assert.equal((await call('mutateAccount',admin,{...op,operationId:`${suffix}_enable`,action:'enable'})).result.status,'succeeded');assert.deepEqual((await db.ref(`users/${ids.target}`).get()).val(),{active:false,approved:false,farmId:''});});
 await check('interrupted operation blocks newer action and another admin reconciles',async()=>{
  const operationId=`${suffix}_interrupted`;
  await db.ref(`_crayfarmAdmin/operations/${operationId}`).set({operationId,actorUid:ids.second,targetUid:ids.target,action:'disable',status:'in_progress',createdAt:Date.now()});
  await db.ref(`_crayfarmAdmin/locks/${ids.target}`).set(operationId);
  await auth.updateUser(ids.target,{disabled:true});
  assert.ok((await call('mutateAccount',admin,{operationId:`${suffix}_competing`,targetUid:ids.target,action:'enable'})).error);
  assert.equal((await call('reconcileOperation',admin,{operationId})).result.status,'succeeded');
  assert.equal((await db.ref(`_crayfarmAdmin/audit/${operationId}/reconciledBy`).get()).val(),ids.admin);
 });
 await check('revocation blocks previously issued token and preserves unrelated claim',async()=>{assert.equal((await call('mutateAccount',admin,{operationId:`${suffix}_revoke`,targetUid:ids.second,action:'revokeAdmin'})).result.status,'succeeded');assert.ok((await call('directory',second)).error);assert.deepEqual((await auth.getUser(ids.second)).customClaims,{unrelated:'preserved'});assert.ok((await call('directory',await login(ids.second))).error);});
 await check('browser cannot write audit records',async()=>{const r=await fetch(`http://127.0.0.1:9000/_crayfarmAdmin/audit/forged.json?ns=demo-crayfarm-default-rtdb&auth=${admin}`,{method:'PUT',body:'{"forged":true}'});assert.equal(r.status,401);});
 console.log(`${passed} integration checks passed. Emulator behavior does not prove production token revocation.`);
}finally{
 for(const uid of Object.values(ids)){await auth.deleteUser(uid).catch(()=>{});await db.ref(`users/${uid}`).remove();}
 await Promise.all(['disable','enable','revoke','interrupted','competing'].flatMap(a=>['operations','audit','leases'].map(p=>db.ref(`_crayfarmAdmin/${p}/${suffix}_${a}`).remove())));
 for(const uid of Object.values(ids))await Promise.all(['blocks','locks'].map(p=>db.ref(`_crayfarmAdmin/${p}/${uid}`).remove()));
}
process.exit(0);
