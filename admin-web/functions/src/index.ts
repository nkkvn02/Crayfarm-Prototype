import {initializeApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getDatabase} from 'firebase-admin/database';
import {onCall, HttpsError} from 'firebase-functions/v2/https';
import {action, deviceAction, key, nextDeviceRecord, publicDevice, publicFarm, publicFormerWorker, publicMember, publicPond, publicUser, requireAdmin, sameOperation} from './core';
import {execute} from './workflow';
import {readDeviceInfo, saveDeviceInfo} from './deviceInfo';
import {listAssignmentPonds, updateDeviceAssignment} from './deviceAssignment';
import {countOverviewInventory} from './overviewInventory';
import {archivedResignations, matchesDirectoryFilters, summarizeOverviewAccounts} from './inactiveAccounts';
import {filterHumanAccounts, readDeviceRegistryPage} from './deviceRegistry';
import {createDevice, DeviceProvisioningError} from './deviceProvisioning';
const usingEmulators=process.env.FUNCTIONS_EMULATOR==='true';
const app = initializeApp({databaseURL:usingEmulators?'https://demo-crayfarm-default-rtdb.firebaseio.com':'https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app'});
const auth = getAuth(), db = getDatabase();
const control = db.ref('_crayfarmAdmin');
function endpoint(fn: (data: any, actor: string) => Promise<any>) {
  return onCall({region:'asia-southeast1', memory:'256MiB', cpu:'gcf_gen1', minInstances:0, maxInstances:3, timeoutSeconds:60}, async request => {
    try {
      const header = request.rawRequest.headers.authorization;
      if (!header?.startsWith('Bearer ')) throw new HttpsError('unauthenticated','Sign in required.');
      const token = await auth.verifyIdToken(header.slice(7), true);
      const block = await control.child(`blocks/${key(token.uid)}`).get();
      const actor = requireAdmin(token, block.exists());
      return await fn(request.data ?? {}, actor);
    } catch (error: any) {
      if (error instanceof HttpsError) throw error;
      if (['permission-denied','invalid-argument','already-exists','failed-precondition','not-found'].includes(error.message)) throw new HttpsError(error.message, error.message);
      if (error.code?.startsWith('auth/')) throw new HttpsError('unauthenticated','Authentication or target account could not be verified.');
      throw new HttpsError('unavailable','Request could not be completed. Retry with the same operation ID.');
    }
  });
}
export const session = endpoint(async (_, actor) => ({uid:actor}));
async function profile(user: any) {
  // Read only the allowed scalar fields, avoiding large profile payloads.
  const fields = ['fullName','role','active','approved','farmId','previousFarmId','employmentStatus'];
  const values = await Promise.all(fields.map(f => db.ref(`users/${key(user.uid)}/${f}`).get()));
  const p = Object.fromEntries(fields.map((f,i) => [f,values[i].val()]));
  return publicUser(user, values.some(v => v.exists()) ? p : null);
}
export const directory = endpoint(async data => {
  const cursor = data.cursor ?? undefined;
  if (cursor !== undefined && (typeof cursor !== 'string' || cursor.length > 2048)) throw new Error('invalid-argument');
  const search = data.search;
  const inactive = data.status === 'inactive';
  const includeArchived = inactive && (!data.role || data.role === 'worker');
  if (search) {
    if (typeof search !== 'string' || search.length > 254 || !['uid','email'].includes(data.searchBy)) throw new Error('invalid-argument');
    const users: Array<ReturnType<typeof publicUser> | ReturnType<typeof publicFormerWorker>> = [];
    try {
      const user = data.searchBy === 'email' ? await auth.getUserByEmail(search) : await auth.getUser(key(search));
      const projected = await profile(user);
      if (!inactive || matchesDirectoryFilters(projected, data.role, data.status)) {
        users.push(...await filterHumanAccounts(db, [projected]));
      }
    } catch (error: any) { if (error.code !== 'auth/user-not-found') throw error; }
    if (includeArchived) users.push(...await archivedResignations(db, auth, search, data.searchBy));
    return {users, cursor:null};
  }
  if (data.role && !['admin','worker','unknown'].includes(data.role)) throw new Error('invalid-argument');
  if (data.status && !['enabled','disabled','inactive'].includes(data.status)) throw new Error('invalid-argument');
  const page = await auth.listUsers(25, cursor);
  const projected = await Promise.all(page.users.map(profile));
  const users = await filterHumanAccounts(db, projected);
  const matching = users.filter(user => matchesDirectoryFilters(user, data.role, data.status));
  const archived = includeArchived && !cursor ? await archivedResignations(db, auth) : [];
  return {users:[...matching, ...archived], cursor:page.pageToken ?? null};
});
export const formerWorkers = endpoint(async data => {
  const search=typeof data.search==='string'?data.search.trim().toLowerCase():'';
  if(search.length>254||!['uid','email'].includes(data.searchBy??'email'))throw new Error('invalid-argument');
  const snapshot=await db.ref('users').orderByKey().limitToFirst(201).get(),workers:any[]=[];
  const entries:Array<{uid:string;profile:any}>=[];snapshot.forEach(child=>{if(entries.length<200)entries.push({uid:child.key!,profile:child.val()});});
  for(const entry of entries){
    if(entry.profile?.role!=='worker'||!['removed','resigned'].includes(entry.profile?.employmentStatus))continue;
    if(search&&!(data.searchBy==='uid'?entry.uid.toLowerCase()===search:String(entry.profile?.email??'').toLowerCase()===search))continue;
    let user:any;try{user=await auth.getUser(entry.uid);}catch(error:any){if(error.code!=='auth/user-not-found')throw error;}
    workers.push(publicFormerWorker(entry.uid,entry.profile,user));
  }
  return {users:workers,cursor:null,truncated:snapshot.numChildren()>200};
});
export const overview = endpoint(async data => {
  const cursor=data.cursor ?? undefined;
  if(cursor !== undefined && (typeof cursor !== 'string' || cursor.length > 2048)) throw new Error('invalid-argument');
  const page = await auth.listUsers(100, cursor);
  const projected=await Promise.all(page.users.map(profile));
  const users = await filterHumanAccounts(db, projected);
  const archived = cursor ? [] : await archivedResignations(db, auth);
  return {...summarizeOverviewAccounts(users, archived.length), cursor:page.pageToken ?? null};
});
export const overviewInventory = endpoint(() => countOverviewInventory(
  app, usingEmulators ? process.env.FIREBASE_DATABASE_EMULATOR_HOST ?? '127.0.0.1:9000' : undefined,
));

async function farmScalars(farmId: string) {
  const fields = ['farmName','description','ownerUid','createdAt'];
  const values = await Promise.all(fields.map(field => db.ref(`farms/${key(farmId)}/${field}`).get()));
  return Object.fromEntries(fields.map((field,index) => [field,values[index].val()]));
}
export const farmDirectory = endpoint(async data => {
  let cursor = data.cursor ?? undefined;
  if(cursor !== undefined && (typeof cursor !== 'string' || cursor.length > 2048)) throw new Error('invalid-argument');
  const farms: any[] = [];
  let pages = 0;
  do {
    const page = await auth.listUsers(25,cursor);
    for(const user of page.users) {
      const owner = await profile(user);
      if(owner.role !== 'admin' || !owner.farmId) continue;
      farms.push(publicFarm(owner.farmId,await farmScalars(owner.farmId),owner));
    }
    cursor = page.pageToken;
    pages++;
  } while(cursor && farms.length < 25 && pages < 5);
  return {farms:farms.slice(0,25),cursor:cursor ?? null};
});
export const farmDetails = endpoint(async data => {
  const farmId = key(data.farmId);
  const farm = await farmScalars(farmId);
  if(!Object.values(farm).some(value => value !== null)) throw new HttpsError('not-found','Farm not found.');
  let owner = null;
  if(typeof farm.ownerUid === 'string' && farm.ownerUid) {
    try { owner = await profile(await auth.getUser(key(farm.ownerUid))); }
    catch(error: any) { if(error.code !== 'auth/user-not-found') throw error; }
  }
  const [memberSnapshot,formerSnapshot] = await Promise.all([
    db.ref(`farms/${farmId}/members`).orderByKey().limitToFirst(101).get(),
    db.ref('users').orderByChild('previousFarmId').equalTo(farmId).limitToFirst(101).get()
  ]);
  const members: any[] = [],memberByUid=new Map<string,any>();
  memberSnapshot.forEach(child => {if(members.length<100){const member=publicMember(child.key!,child.val());members.push(member);memberByUid.set(member.uid,member);}});
  formerSnapshot.forEach(child=>{
    const value=child.val();if(value?.role!=='worker'||!['removed','resigned'].includes(value?.employmentStatus))return;
    const historical=publicMember(child.key!,{fullName:value.fullName,email:value.email,role:'worker',status:value.employmentStatus,active:false,joinedAt:value.joinedAt,leftAt:value.removedAt??value.resignedAt});
    const existing=memberByUid.get(child.key!);
    if(existing)Object.assign(existing,{fullName:existing.fullName??historical.fullName,email:existing.email??historical.email,status:existing.status??historical.status,active:existing.active??historical.active,leftAt:existing.leftAt??historical.leftAt});
    else if(members.length<100){members.push(historical);memberByUid.set(historical.uid,historical);}
  });
  // Production rules intentionally have no farmId index yet. Keep this read bounded and filter trusted data server-side.
  const deviceSnapshot = await db.ref('devices').orderByKey().limitToFirst(1001).get();
  const devices: any[] = [];let matchingDeviceCount=0;
  deviceSnapshot.forEach(child => {if(child.child('farmId').val()===farmId){matchingDeviceCount++;if(devices.length<100)devices.push(publicDevice(child.key!,child.val()));}});
  const pondIds = [...new Set(devices.map(device => device.pondId).filter(Boolean))] as string[];
  const ponds = await Promise.all(pondIds.map(async pondId => {
    const fields = ['pondName','deviceId','active','createdAt'];
    const values = await Promise.all(fields.map(field => db.ref(`farms/${farmId}/ponds/${key(pondId)}/${field}`).get()));
    return publicPond(pondId,Object.fromEntries(fields.map((field,index) => [field,values[index].val()])));
  }));
  return {farm:publicFarm(farmId,farm,owner),owner,members,ponds,devices,
    truncated:{members:memberSnapshot.numChildren()>100||formerSnapshot.numChildren()>100||memberSnapshot.numChildren()+formerSnapshot.numChildren()>100,devices:matchingDeviceCount>100||deviceSnapshot.numChildren()>1000}};
});
async function deviceView(deviceId: string, value: any) {
  const device = publicDevice(deviceId,value);
  let farmName = null, pondName = null;
  if(device.farmId) farmName = (await db.ref(`farms/${key(device.farmId)}/farmName`).get()).val();
  if(device.farmId && device.pondId) pondName = (await db.ref(`farms/${key(device.farmId)}/ponds/${key(device.pondId)}/pondName`).get()).val();
  return {...device,farmName:typeof farmName === 'string' ? farmName : null,pondName:typeof pondName === 'string' ? pondName : null};
}
export const deviceDirectory = endpoint(async data => {
  if(data.status && !['available','assigned','inactive'].includes(data.status)) throw new Error('invalid-argument');
  if(data.search) {
    if(typeof data.search !== 'string') throw new Error('invalid-argument');
    const deviceId=key(data.search.toUpperCase());const snap=await db.ref(`devices/${deviceId}`).get();
    const device=snap.exists()?await deviceView(deviceId,snap.val()):null;
    return {devices:device&&(!data.status||device.status===data.status)?[device]:[],cursor:null};
  }
  const {entries, cursor} = await readDeviceRegistryPage(db, 25, data.cursor);
  const devices = await Promise.all(entries.map(entry => deviceView(entry.id, entry.value)));
  return {devices:devices.filter(device=>!data.status||device.status===data.status),cursor};
});
export const deviceInfo = endpoint(data => readDeviceInfo(db, data.deviceId));
export const provisionDevice = endpoint(async (data, actor) => {
  try { return await createDevice(db, auth, data, actor); }
  catch (error) {
    if (!(error instanceof DeviceProvisioningError)) throw error;
    const code = error.status === 400 ? 'invalid-argument' : error.status === 409 ? 'failed-precondition' : 'unavailable';
    throw new HttpsError(code, error.message);
  }
});
export const assignmentPonds = endpoint(data => listAssignmentPonds(db, data.farmId, data.cursor));
export const changeDeviceAssignment = endpoint(async (data, actor) => {
  try { return await updateDeviceAssignment(db, data, actor); }
  catch (error) {
    if (error instanceof Error && error.message === 'conflict') throw new HttpsError('already-exists', 'This request was already used.');
    if (error instanceof Error && error.message === 'assignment-database-too-large') {
      throw new HttpsError('failed-precondition', 'The database exceeds the size limit for safe assignment.');
    }
    throw error;
  }
});
export const updateDeviceInfo = endpoint((data, actor) =>
  saveDeviceInfo(db, data.deviceId, data.displayName, data.maintenanceNotes, actor));

export const recentOperations = endpoint(async () => {
  // Operation IDs are timestamp-prefixed, so this remains bounded without requiring a database-rules deployment.
  const snap = await control.child('operations').orderByKey().limitToLast(30).get();
  const records: any[]=[];snap.forEach(c=>{records.push({...c.val(),kind:'account'});});
  const deviceSnap=await control.child('deviceOperations').orderByKey().limitToLast(30).get();
  deviceSnap.forEach(c=>{const value=c.val();records.push({operationId:value.operationId,actorUid:value.actorUid,deviceId:value.deviceId,action:value.action,createdAt:value.createdAt,updatedAt:value.updatedAt,status:value.status,kind:'device'});});
  return records.sort((a,b)=>(a.createdAt??0)-(b.createdAt??0)).slice(-30);
});
export const operationPage = endpoint(async data => {
  let query=control.child('operations').orderByKey();
  if(data.cursor)query=query.startAfter(key(data.cursor));
  const snap=await query.limitToFirst(25).get();const records:any[]=[];snap.forEach(c=>{records.push(c.val());});
  return {operations:records,cursor:records.length===25?records.at(-1).operationId:null};
});
export const orphanProfiles = endpoint(async data => {
  let query = db.ref('users').orderByKey();
  if(data.cursor) query = query.startAfter(key(data.cursor));
  const snap = await query.limitToFirst(25).get();
  const ids: string[] = []; snap.forEach(c=>{ids.push(c.key!);});
  const orphaned: string[] = [];
  for(const uid of ids) {try {await auth.getUser(uid);} catch(e: any) {if(e.code==='auth/user-not-found') orphaned.push(uid); else throw e;}}
  return {orphaned, scanned:ids.length, cursor:ids.length === 25 ? ids.at(-1) : null};
});
async function runMutation(data:any, actor:string, reconciler?:string) {
  const targetUid = key(data.targetUid), operationId = key(data.operationId), kind = action(data.action);
  if(targetUid === (reconciler ?? actor)) throw new HttpsError('failed-precondition','Self-administration is disabled. Use another provisioned administrator.');
  await auth.getUser(targetUid);
  const ref = control.child(`operations/${operationId}`);
  const proposed = {operationId, actorUid:actor, targetUid, action:kind, createdAt:Date.now(), updatedAt:Date.now(), status:'pending', completedSteps:{}};
  const result = await ref.transaction(old => old ?? proposed);
  let op = result.snapshot.val();
  if(!sameOperation(op, proposed)) throw new HttpsError('already-exists','Operation ID belongs to a different request.');
  if(op.status === 'succeeded') return op;
  // Lock is retained across failures; a different action cannot overtake unfinished work.
  const lock = control.child(`locks/${targetUid}`);
  const acquired = await lock.transaction(old => old === null || old === operationId ? operationId : undefined);
  if(!acquired.committed) throw new HttpsError('failed-precondition','Reconcile the existing operation for this account first.');
  // A second execution of the same operation is excluded until its lease expires.
  const lease = control.child(`leases/${operationId}`);
  const now = Date.now();
  const leased = await lease.transaction(old => !old || old < now ? now + 120000 : undefined);
  if(!leased.committed) return {...op,status:'in_progress'};
  try {
    // Recheck after acquiring execution lease: another duplicate may have completed.
    op = (await ref.get()).val();
    if(op.status === 'succeeded') {
      await lock.transaction(old=>old === operationId ? null : undefined);
      return op;
    }
    const status = await execute(kind, {
      markRunning: async()=>{await ref.update({status:'in_progress',updatedAt:Date.now()});},
      block: async()=>{await control.child(`blocks/${targetUid}`).set({operationId,createdAt:Date.now()});},
      mutate: async a=>{
        if(a==='revokeAdmin') {const u=await auth.getUser(targetUid);const claims={...u.customClaims};delete claims.crayfarmAdmin;await auth.setCustomUserClaims(targetUid,claims);}
        else await auth.updateUser(targetUid,{disabled:a==='disable'});
      },
      checkpoint:async step=>{await ref.child(`completedSteps/${step}`).set(true);},
      revoke:async()=>{await auth.revokeRefreshTokens(targetUid);},
      verify:async a=>{const u=await auth.getUser(targetUid);return a==='revokeAdmin'?u.customClaims?.crayfarmAdmin!==true:u.disabled===(a==='disable');},
      finish:async()=>{
        const update: Record<string,unknown> = {};
        update[`audit/${operationId}`] = {operationId,actorUid:actor,targetUid,action:kind,outcome:'succeeded',timestamp:Date.now(),...(reconciler?{reconciledBy:reconciler}:{})};
        update[`operations/${operationId}/status`] = 'succeeded';
        update[`operations/${operationId}/updatedAt`] = Date.now();
        update[`locks/${targetUid}`] = null;
        await control.update(update);
      },
      pending:async()=>{await ref.update({status:'needs_reconciliation',updatedAt:Date.now(),errorCategory:'incomplete-or-uncertain'});}
    });
    return {operationId,status};
  } finally {await lease.remove().catch(()=>{});}
}
export const mutateAccount = endpoint((data,actor)=>runMutation(data,actor));
export const reconcileOperation = endpoint(async (data,actor)=>{
  const op=(await control.child(`operations/${key(data.operationId)}`).get()).val();
  if(!op)throw new HttpsError('not-found','Operation not found.');
  return runMutation(op,op.actorUid,actor);
});
async function runDeviceMutation(data:any, actor:string, reconciler?:string) {
  const deviceId=key(String(data.deviceId??'').toUpperCase()),operationId=key(data.operationId),kind=deviceAction(data.action);
  const authUid=kind==='register'?key(data.authUid):undefined;
  if(authUid) {
    let identity;
    try {identity=await auth.getUser(authUid);} catch(error:any) {if(error.code==='auth/user-not-found')throw new HttpsError('failed-precondition','The Firebase Authentication UID does not exist.');throw error;}
    if(identity.disabled)throw new HttpsError('failed-precondition','The Firebase Authentication account is disabled.');
    const registry=await db.ref('devices').orderByKey().limitToFirst(1001).get();
    if(registry.numChildren()>1000)throw new HttpsError('resource-exhausted','Device identity uniqueness could not be verified within the safety limit.');
    let duplicate:string|null=null;registry.forEach(child=>{if(child.key!==deviceId&&child.child('authUid').val()===authUid)duplicate=child.key;});
    if(duplicate)throw new HttpsError('already-exists','This Firebase Authentication UID is already registered to another device.');
  }
  const operationRef=control.child(`deviceOperations/${operationId}`);
  const proposed={operationId,actorUid:actor,deviceId,action:kind,...(authUid?{authUid}:{}),createdAt:Date.now(),updatedAt:Date.now(),status:'pending'};
  const created=await operationRef.transaction(old=>old??proposed);let operation=created.snapshot.val();
  if(operation.actorUid!==proposed.actorUid||operation.deviceId!==deviceId||operation.action!==kind||(kind==='register'&&operation.authUid!==authUid)) {
    throw new HttpsError('already-exists','Operation ID belongs to a different request.');
  }
  const result=()=>({operationId:operation.operationId,deviceId:operation.deviceId,action:operation.action,status:operation.status,kind:'device'});
  if(operation.status==='succeeded')return result();
  const lock=control.child(`deviceLocks/${deviceId}`),acquired=await lock.transaction(old=>old===null||old===operationId?operationId:undefined);
  if(!acquired.committed)throw new HttpsError('failed-precondition','Reconcile the existing operation for this device first.');
  const lease=control.child(`deviceLeases/${operationId}`),now=Date.now(),leased=await lease.transaction(old=>!old||old<now?now+120000:undefined);
  if(!leased.committed)return {...result(),status:'in_progress'};
  try {
    operation=(await operationRef.get()).val();if(operation.status==='succeeded'){await lock.transaction(old=>old===operationId?null:undefined);return result();}
    await operationRef.update({status:'in_progress',updatedAt:Date.now()});
    let mutationError: any;
    const deviceRef=db.ref(`devices/${deviceId}`),changed=await deviceRef.transaction(current=>{try{return nextDeviceRecord(current,kind,{deviceId,authUid,actor,operationId,now:Date.now()});}catch(error){mutationError=error;return undefined;}});
    if(!changed.committed)throw mutationError??new Error('failed-precondition');
    const finishedAt=Date.now(),update:Record<string,unknown>={};
    update[`deviceAudit/${operationId}`]={operationId,actorUid:actor,deviceId,action:kind,outcome:'succeeded',timestamp:finishedAt,...(reconciler?{reconciledBy:reconciler}:{})};
    update[`deviceOperations/${operationId}/status`]='succeeded';update[`deviceOperations/${operationId}/updatedAt`]=finishedAt;update[`deviceLocks/${deviceId}`]=null;
    await control.update(update);operation={...operation,status:'succeeded'};return result();
  } catch(error) {
    await operationRef.update({status:'needs_reconciliation',updatedAt:Date.now(),errorCategory:'incomplete-or-uncertain'}).catch(()=>{});throw error;
  } finally {await lease.remove().catch(()=>{});}
}
export const mutateDevice = endpoint((data,actor)=>runDeviceMutation(data,actor));
export const reconcileDeviceOperation = endpoint(async (data,actor)=>{
  const operation=(await control.child(`deviceOperations/${key(data.operationId)}`).get()).val();
  if(!operation)throw new HttpsError('not-found','Operation not found.');
  return runDeviceMutation(operation,operation.actorUid,actor);
});
