import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile, stat} from 'node:fs/promises';
import {extname, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {initializeServerAdmin} from './firebase-server-admin.mjs';

const functionsRequire = createRequire(new URL('../functions/package.json', import.meta.url));
const {getAuth} = functionsRequire('firebase-admin/auth');
const {getDatabase} = functionsRequire('firebase-admin/database');
const {action, deviceAction, key, publicDevice, publicFarm, publicFormerWorker, publicMember, publicPond, publicUser, sameOperation} = functionsRequire('./lib/core.js');
const {execute} = functionsRequire('./lib/workflow.js');
const {readDeviceInfo, saveDeviceInfo} = functionsRequire('./lib/deviceInfo.js');
const {listAssignmentPonds, updateDeviceAssignment} = functionsRequire('./lib/deviceAssignment.js');
const {countOverviewInventory} = functionsRequire('./lib/overviewInventory.js');
const {filterHumanAccounts, readDeviceRegistryPage} = functionsRequire('./lib/deviceRegistry.js');
const {createDevice} = functionsRequire('./lib/deviceProvisioning.js');
const {changeDeviceRecord, deleteRegisteredDevice, resumeMatchingDeviceOperation} = functionsRequire('./lib/deviceLifecycle.js');
const {archivedResignations, matchesDirectoryFilters, summarizeOverviewAccounts} = functionsRequire('./lib/inactiveAccounts.js');
const deploymentMode = process.env.CRAYFARM_DEPLOYMENT_MODE?.trim().toLowerCase();
const hostedMode = deploymentMode === 'hostinger' || deploymentMode === 'render';
const adminEmail = (process.env.CRAYFARM_ADMIN_EMAIL ?? process.env.CRAYFARM_LOCAL_ADMIN_EMAIL)?.trim().toLowerCase();
if (!adminEmail) throw new Error('Set CRAYFARM_ADMIN_EMAIL to the exact authorized Firebase Auth email.');

const publicOriginText = process.env.CRAYFARM_PUBLIC_ORIGIN?.trim().replace(/\/$/, '');
if (hostedMode && !publicOriginText) throw new Error('Set CRAYFARM_PUBLIC_ORIGIN to the exact public HTTPS origin.');
const publicOrigin = publicOriginText ? new URL(publicOriginText) : null;
if (hostedMode && (publicOrigin.protocol !== 'https:' || publicOrigin.pathname !== '/')) {
  throw new Error('CRAYFARM_PUBLIC_ORIGIN must be an HTTPS origin without a path.');
}

const app = await initializeServerAdmin(hostedMode ? `${deploymentMode}-production-server` : 'local-production-server');
const auth = getAuth(app);
const db = getDatabase(app);
const control = db.ref('_crayfarmAdmin');
const dist = resolve(fileURLToPath(new URL('../dist', import.meta.url)));
const port = Number(process.env.PORT ?? 5173);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be a valid TCP port.');
const allowedHosts = new Set(['127.0.0.1:5173', 'localhost:5173']);
const allowedOrigins = new Set(['http://127.0.0.1:5173', 'http://localhost:5173']);
if (publicOrigin) {allowedHosts.add(publicOrigin.host);allowedOrigins.add(publicOrigin.origin);}

async function profile(user) {
  const fields = ['fullName','role','active','approved','farmId','previousFarmId','employmentStatus'];
  const values = await Promise.all(fields.map(field => db.ref(`users/${key(user.uid)}/${field}`).get()));
  const value = Object.fromEntries(fields.map((field,index) => [field,values[index].val()]));
  return publicUser(user, values.some(snapshot => snapshot.exists()) ? value : null);
}


async function farmScalars(farmId) {
  const fields=['farmName','description','ownerUid','createdAt'];
  const values=await Promise.all(fields.map(field=>db.ref(`farms/${key(farmId)}/${field}`).get()));
  return Object.fromEntries(fields.map((field,index)=>[field,values[index].val()]));
}

async function readFarmDetails(data) {
  const farmId=key(data.farmId),farm=await farmScalars(farmId);
  if(!Object.values(farm).some(value=>value!==null))throw Object.assign(new Error('Farm not found.'),{status:404});
  let owner=null;
  if(typeof farm.ownerUid==='string'&&farm.ownerUid){try{owner=await profile(await auth.getUser(key(farm.ownerUid)));}catch(error){if(error.code!=='auth/user-not-found')throw error;}}
  const [memberSnapshot,formerSnapshot]=await Promise.all([db.ref(`farms/${farmId}/members`).orderByKey().limitToFirst(101).get(),db.ref('users').orderByChild('previousFarmId').equalTo(farmId).limitToFirst(101).get()]),members=[],memberByUid=new Map();
  memberSnapshot.forEach(child=>{if(members.length<100){const member=publicMember(child.key,child.val());members.push(member);memberByUid.set(member.uid,member);}});
  formerSnapshot.forEach(child=>{const value=child.val();if(value?.role!=='worker'||!['removed','resigned'].includes(value?.employmentStatus))return;const historical=publicMember(child.key,{fullName:value.fullName,email:value.email,role:'worker',status:value.employmentStatus,active:false,joinedAt:value.joinedAt,leftAt:value.removedAt??value.resignedAt}),existing=memberByUid.get(child.key);if(existing)Object.assign(existing,{fullName:existing.fullName??historical.fullName,email:existing.email??historical.email,status:existing.status??historical.status,active:existing.active??historical.active,leftAt:existing.leftAt??historical.leftAt});else if(members.length<100){members.push(historical);memberByUid.set(historical.uid,historical);}});
  // Production rules intentionally have no farmId index yet. Keep this read bounded and filter trusted data server-side.
  const deviceSnapshot=await db.ref('devices').orderByKey().limitToFirst(1001).get(),devices=[];let matchingDeviceCount=0;
  deviceSnapshot.forEach(child=>{if(child.child('farmId').val()===farmId){matchingDeviceCount++;if(devices.length<100)devices.push(publicDevice(child.key,child.val()));}});
  const pondIds=[...new Set(devices.map(device=>device.pondId).filter(Boolean))];
  const ponds=await Promise.all(pondIds.map(async pondId=>{const fields=['pondName','deviceId','active','createdAt'];const values=await Promise.all(fields.map(field=>db.ref(`farms/${farmId}/ponds/${key(pondId)}/${field}`).get()));return publicPond(pondId,Object.fromEntries(fields.map((field,index)=>[field,values[index].val()])));}));
  return {farm:publicFarm(farmId,farm,owner),owner,members,ponds,devices,truncated:{members:memberSnapshot.numChildren()>100||formerSnapshot.numChildren()>100||memberSnapshot.numChildren()+formerSnapshot.numChildren()>100,devices:matchingDeviceCount>100||deviceSnapshot.numChildren()>1000}};
}

async function authenticate(request) {
  const header = request.headers.authorization;
  if (!header?.startsWith('Bearer ')) throw Object.assign(new Error('Sign in required.'), {status:401});
  const token = await auth.verifyIdToken(header.slice(7), true);
  if (token.email?.toLowerCase() !== adminEmail || token.crayfarmAdmin !== true) {
    throw Object.assign(new Error('This account is not authorized for the local admin website.'), {status:403});
  }
  if ((await control.child(`blocks/${key(token.uid)}`).get()).exists()) {
    throw Object.assign(new Error('Administrator access has been revoked.'), {status:403});
  }
  return token.uid;
}

async function readBody(request) {
  let body = '';
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 65536) throw Object.assign(new Error('Request is too large.'), {status:413});
  }
  if (!body) return {};
  try { return JSON.parse(body); } catch { throw Object.assign(new Error('Invalid JSON.'), {status:400}); }
}

async function runMutation(data, actor, reconciler) {
  const targetUid=key(data.targetUid),operationId=key(data.operationId),kind=action(data.action);
  if(targetUid===(reconciler??actor))throw Object.assign(new Error('Self-administration is disabled. Use another provisioned administrator.'),{status:412});
  await auth.getUser(targetUid);
  const ref=control.child(`operations/${operationId}`);
  const proposed={operationId,actorUid:actor,targetUid,action:kind,createdAt:Date.now(),updatedAt:Date.now(),status:'pending',completedSteps:{}};
  const result=await ref.transaction(old=>old??proposed);let operation=result.snapshot.val();
  if(!sameOperation(operation,proposed))throw Object.assign(new Error('Operation ID belongs to a different request.'),{status:409});
  if(operation.status==='succeeded')return operation;
  const lock=control.child(`locks/${targetUid}`);
  const acquired=await lock.transaction(old=>old===null||old===operationId?operationId:undefined);
  if(!acquired.committed)throw Object.assign(new Error('Reconcile the existing operation for this account first.'),{status:412});
  const lease=control.child(`leases/${operationId}`),now=Date.now();
  const leased=await lease.transaction(old=>!old||old<now?now+120000:undefined);
  if(!leased.committed)return {...operation,status:'in_progress'};
  try{
    operation=(await ref.get()).val();
    if(operation.status==='succeeded'){await lock.transaction(old=>old===operationId?null:undefined);return operation;}
    const status=await execute(kind,{
      markRunning:async()=>{await ref.update({status:'in_progress',updatedAt:Date.now()});},
      block:async()=>{await control.child(`blocks/${targetUid}`).set({operationId,createdAt:Date.now()});},
      mutate:async value=>{
        if(value==='revokeAdmin'){const target=await auth.getUser(targetUid);const claims={...target.customClaims};delete claims.crayfarmAdmin;await auth.setCustomUserClaims(targetUid,claims);}
        else await auth.updateUser(targetUid,{disabled:value==='disable'});
      },
      checkpoint:async step=>{await ref.child(`completedSteps/${step}`).set(true);},
      revoke:async()=>{await auth.revokeRefreshTokens(targetUid);},
      verify:async value=>{const target=await auth.getUser(targetUid);return value==='revokeAdmin'?target.customClaims?.crayfarmAdmin!==true:target.disabled===(value==='disable');},
      finish:async()=>{const update={};update[`audit/${operationId}`]={operationId,actorUid:actor,targetUid,action:kind,outcome:'succeeded',timestamp:Date.now(),...(reconciler?{reconciledBy:reconciler}:{})};update[`operations/${operationId}/status`]='succeeded';update[`operations/${operationId}/updatedAt`]=Date.now();update[`locks/${targetUid}`]=null;await control.update(update);},
      pending:async()=>{await ref.update({status:'needs_reconciliation',updatedAt:Date.now(),errorCategory:'incomplete-or-uncertain'});}
    });
    return {operationId,status};
  }finally{await lease.remove().catch(()=>{});}
}

function deviceFailure(error) {
  const statuses={'invalid-argument':400,'already-exists':409,'failed-precondition':412,'not-found':404};
  if(statuses[error?.message])return Object.assign(new Error(error.message),{status:statuses[error.message]});
  return error;
}

async function deviceView(deviceId,value) {
  const device=publicDevice(deviceId,value);let farmName=null,pondName=null;
  if(device.farmId)farmName=(await db.ref(`farms/${key(device.farmId)}/farmName`).get()).val();
  if(device.farmId&&device.pondId)pondName=(await db.ref(`farms/${key(device.farmId)}/ponds/${key(device.pondId)}/pondName`).get()).val();
  return {...device,farmName:typeof farmName==='string'?farmName:null,pondName:typeof pondName==='string'?pondName:null};
}

async function runDeviceMutation(data,actor,reconciler,resumeBlocked = true) {
  if (data.action === 'delete') {
    try { return await deleteRegisteredDevice(db, data, actor); }
    catch (error) { throw deviceFailure(error); }
  }
  let deviceId,operationId,kind,authUid;
  try{deviceId=key(String(data.deviceId??'').toUpperCase());operationId=key(data.operationId);kind=deviceAction(data.action);authUid=kind==='register'?key(data.authUid):undefined;}catch(error){throw deviceFailure(error);}
  if(authUid){let identity;try{identity=await auth.getUser(authUid);}catch(error){if(error.code==='auth/user-not-found')throw Object.assign(new Error('The Firebase Authentication UID does not exist.'),{status:412});throw error;}if(identity.disabled)throw Object.assign(new Error('The Firebase Authentication account is disabled.'),{status:412});const registry=await db.ref('devices').orderByKey().limitToFirst(1001).get();if(registry.numChildren()>1000)throw Object.assign(new Error('Device identity uniqueness could not be verified within the safety limit.'),{status:429});let duplicate=null;registry.forEach(child=>{if(child.key!==deviceId&&child.child('authUid').val()===authUid)duplicate=child.key;});if(duplicate)throw Object.assign(new Error('This Firebase Authentication UID is already registered to another device.'),{status:409});}
  const operationRef=control.child(`deviceOperations/${operationId}`),proposed={operationId,actorUid:actor,deviceId,action:kind,...(authUid?{authUid}:{}),createdAt:Date.now(),updatedAt:Date.now(),status:'pending'};
  const created=await operationRef.transaction(old=>old??proposed);let operation=created.snapshot.val();
  if(operation.actorUid!==actor||operation.deviceId!==deviceId||operation.action!==kind||(kind==='register'&&operation.authUid!==authUid))throw Object.assign(new Error('Operation ID belongs to a different request.'),{status:409});
  const result=()=>({operationId:operation.operationId,deviceId:operation.deviceId,action:operation.action,status:operation.resolvedStatus??operation.status,kind:'device', ...(operation.message && {message:operation.message})});
  if(['succeeded','failed','superseded'].includes(operation.status))return result();
  const lock=control.child(`deviceLocks/${deviceId}`),acquired=await lock.transaction(old=>old===null||old===operationId?operationId:undefined);
  if(!acquired.committed) {
    const resumed = resumeBlocked ? await resumeMatchingDeviceOperation(control, acquired.snapshot.val(), operation,
      previous => runDeviceMutation(previous, actor, reconciler, false)) : undefined;
    if (resumed) return resumed;
    throw Object.assign(new Error('Reconcile the existing operation for this device first.'),{status:412});
  }
  const lease=control.child(`deviceLeases/${operationId}`),now=Date.now(),leased=await lease.transaction(old=>!old||old<now?now+120000:undefined);
  if(!leased.committed)return {...result(),status:'in_progress'};
  try{
    operation=(await operationRef.get()).val();if(['succeeded','failed','superseded'].includes(operation.status)){await lock.transaction(old=>old===operationId?null:old);return result();}
    await operationRef.update({status:'in_progress',updatedAt:Date.now()});
    const message = await changeDeviceRecord(db, kind, {deviceId,authUid,actor,operationId,now:Date.now()});
    if (message) {
      await control.update({
        [`deviceOperations/${operationId}/status`]:'failed',
        [`deviceOperations/${operationId}/updatedAt`]:Date.now(),
        [`deviceOperations/${operationId}/message`]:message,
        [`deviceLocks/${deviceId}`]:null,
      });
      operation={...operation,status:'failed',message};
      return result();
    }
    const finishedAt=Date.now(),update={};update[`deviceAudit/${operationId}`]={operationId,actorUid:actor,deviceId,action:kind,outcome:'succeeded',timestamp:finishedAt,...(reconciler?{reconciledBy:reconciler}:{})};update[`deviceOperations/${operationId}/status`]='succeeded';update[`deviceOperations/${operationId}/updatedAt`]=finishedAt;update[`deviceLocks/${deviceId}`]=null;await control.update(update);operation={...operation,status:'succeeded'};return result();
  }catch(error){await operationRef.update({status:'needs_reconciliation',updatedAt:Date.now(),errorCategory:'incomplete-or-uncertain'}).catch(()=>{});throw error;}finally{await lease.remove().catch(()=>{});}
}


const handlers = {
  session: async (_data, actor) => ({uid:actor, mode:'local-production'}),
  directory: async data => {
    const cursor = data.cursor ?? undefined;
    if (cursor !== undefined && (typeof cursor !== 'string' || cursor.length > 2048)) throw Object.assign(new Error('Invalid cursor.'), {status:400});
    const inactive = data.status === 'inactive';
    const includeArchived = inactive && (!data.role || data.role === 'worker');
    if (data.search) {
      if (typeof data.search !== 'string' || data.search.length > 254 || !['uid','email'].includes(data.searchBy)) throw Object.assign(new Error('Invalid search.'), {status:400});
      const users = [];
      try {
        const user = data.searchBy === 'email' ? await auth.getUserByEmail(data.search) : await auth.getUser(key(data.search));
        const projected = await profile(user);
        if (!inactive || matchesDirectoryFilters(projected, data.role, data.status)) {
          users.push(...await filterHumanAccounts(db, [projected]));
        }
      } catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
      if (includeArchived) users.push(...await archivedResignations(db, auth, data.search, data.searchBy));
      return {users, cursor:null};
    }
    if (data.role && !['admin','worker','unknown'].includes(data.role)) throw Object.assign(new Error('Invalid role.'), {status:400});
    if (data.status && !['enabled','disabled','inactive'].includes(data.status)) throw Object.assign(new Error('Invalid status.'), {status:400});
    const page = await auth.listUsers(25,cursor);
    const users = await filterHumanAccounts(db, await Promise.all(page.users.map(profile)));
    const matching = users.filter(user => matchesDirectoryFilters(user, data.role, data.status));
    if (includeArchived && !cursor) matching.push(...await archivedResignations(db, auth));
    return {users:matching, cursor:page.pageToken ?? null};
  },
  formerWorkers: async data => {
    const search=typeof data.search==='string'?data.search.trim().toLowerCase():'';if(search.length>254||!['uid','email'].includes(data.searchBy??'email'))throw Object.assign(new Error('Invalid search.'),{status:400});
    const snapshot=await db.ref('users').orderByKey().limitToFirst(201).get(),entries=[],workers=[];snapshot.forEach(child=>{if(entries.length<200)entries.push({uid:child.key,profile:child.val()});});
    for(const entry of entries){if(entry.profile?.role!=='worker'||!['removed','resigned'].includes(entry.profile?.employmentStatus))continue;if(search&&!(data.searchBy==='uid'?entry.uid.toLowerCase()===search:String(entry.profile?.email??'').toLowerCase()===search))continue;let user;try{user=await auth.getUser(entry.uid);}catch(error){if(error.code!=='auth/user-not-found')throw error;}workers.push(publicFormerWorker(entry.uid,entry.profile,user));}
    return {users:workers,cursor:null,truncated:snapshot.numChildren()>200};
  },
  overview: async data => {
    const cursor = data.cursor ?? undefined;
    if (cursor !== undefined && (typeof cursor !== 'string' || cursor.length > 2048)) throw Object.assign(new Error('Invalid cursor.'), {status:400});
    const page = await auth.listUsers(100,cursor);
    const users = await filterHumanAccounts(db, await Promise.all(page.users.map(profile)));
    const archived = cursor ? [] : await archivedResignations(db, auth);
    return {...summarizeOverviewAccounts(users, archived.length), cursor:page.pageToken ?? null};
  },
  overviewInventory: () => countOverviewInventory(app),
  farmDirectory: async data => {
    let cursor=data.cursor??undefined;if(cursor!==undefined&&(typeof cursor!=='string'||cursor.length>2048))throw Object.assign(new Error('Invalid cursor.'),{status:400});
    const farms=[];let pages=0;
    do{const page=await auth.listUsers(25,cursor);for(const user of page.users){const owner=await profile(user);if(owner.role!=='admin'||!owner.farmId)continue;farms.push(publicFarm(owner.farmId,await farmScalars(owner.farmId),owner));}cursor=page.pageToken;pages++;}while(cursor&&farms.length<25&&pages<5);
    return {farms:farms.slice(0,25),cursor:cursor??null};
  },
  farmDetails: readFarmDetails,
  deviceDirectory: async data => {
    if(data.status&&!['available','assigned','inactive'].includes(data.status))throw Object.assign(new Error('Invalid status.'),{status:400});
    if(data.search){if(typeof data.search!=='string')throw Object.assign(new Error('Invalid search.'),{status:400});const deviceId=key(data.search.toUpperCase());const snapshot=await db.ref(`devices/${deviceId}`).get();const device=snapshot.exists()?await deviceView(deviceId,snapshot.val()):null;return {devices:device&&(!data.status||device.status===data.status)?[device]:[],cursor:null};}
    const {entries, cursor} = await readDeviceRegistryPage(db, 25, data.cursor);
    const devices = await Promise.all(entries.map(entry => deviceView(entry.id, entry.value)));
    return {devices:devices.filter(device=>!data.status||device.status===data.status),cursor};
  },
  provisionDevice: (data, actor) => createDevice(db, auth, data, actor),
  deviceInfo: async data => {
    try { return await readDeviceInfo(db, data.deviceId); }
    catch (error) { throw deviceFailure(error); }
  },
  assignmentPonds: async data => {
    try { return await listAssignmentPonds(db, data.farmId, data.cursor); }
    catch (error) { throw deviceFailure(error); }
  },
  changeDeviceAssignment: async (data, actor) => {
    try { return await updateDeviceAssignment(db, data, actor); }
    catch (error) {
      if (error.message === 'conflict') throw Object.assign(new Error('This request was already used.'), {status:409});
      if (error.message === 'failed-precondition') {
        throw Object.assign(new Error('The device or pond changed. Refresh the device and check that the destination is active and available.'), {status:412});
      }
      if (error.message === 'assignment-database-too-large') {
        throw Object.assign(new Error('The database exceeds the size limit for safe assignment.'), {status:412});
      }
      throw deviceFailure(error);
    }
  },
  updateDeviceInfo: async (data, actor) => {
    try { return await saveDeviceInfo(db, data.deviceId, data.displayName, data.maintenanceNotes, actor); }
    catch (error) { throw deviceFailure(error); }
  },
  recentOperations: async () => {
    // Operation IDs are timestamp-prefixed, so this remains bounded without requiring a production rules index.
    const snapshot = await control.child('operations').orderByKey().limitToLast(30).get();
    const records=[];snapshot.forEach(child=>records.push({...child.val(),kind:'account'}));const deviceSnapshot=await control.child('deviceOperations').orderByKey().limitToLast(30).get();deviceSnapshot.forEach(child=>{const value=child.val();records.push({operationId:value.operationId,actorUid:value.actorUid,deviceId:value.deviceId,action:value.action,createdAt:value.createdAt,updatedAt:value.updatedAt,status:value.status,kind:'device'});});return records.sort((a,b)=>(a.createdAt??0)-(b.createdAt??0)).slice(-30);
  },
  operationPage: async data => {
    let query=control.child('operations').orderByKey();
    if(data.cursor)query=query.startAfter(key(data.cursor));
    const snapshot=await query.limitToFirst(25).get();const operations=[];snapshot.forEach(child=>operations.push(child.val()));
    return {operations,cursor:operations.length===25?operations.at(-1).operationId:null};
  },
  orphanProfiles: async data => {
    let query=db.ref('users').orderByKey();if(data.cursor)query=query.startAfter(key(data.cursor));
    const snapshot=await query.limitToFirst(25).get();const ids=[];snapshot.forEach(child=>ids.push(child.key));
    const orphaned=[];for(const uid of ids){try{await auth.getUser(uid);}catch(error){if(error.code==='auth/user-not-found')orphaned.push(uid);else throw error;}}
    return {orphaned,scanned:ids.length,cursor:ids.length===25?ids.at(-1):null};
  },
  mutateAccount: (data,actor)=>runMutation(data,actor),
  reconcileOperation: async (data,actor)=>{const operation=(await control.child(`operations/${key(data.operationId)}`).get()).val();if(!operation)throw Object.assign(new Error('Operation not found.'),{status:404});return runMutation(operation,operation.actorUid,actor);},
  mutateDevice: (data,actor)=>runDeviceMutation(data,actor),
  reconcileDeviceOperation: async (data,actor)=>{const operation=(await control.child(`deviceOperations/${key(data.operationId)}`).get()).val();if(!operation)throw Object.assign(new Error('Operation not found.'),{status:404});return runDeviceMutation(operation,operation.actorUid,actor);}
};

function securityHeaders(response) {
  response.setHeader('X-Content-Type-Options','nosniff');
  response.setHeader('X-Frame-Options','DENY');
  response.setHeader('Referrer-Policy','no-referrer');
  response.setHeader('Cache-Control','no-store');
  response.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; connect-src 'self' https://*.googleapis.com https://*.firebaseio.com https://*.firebasedatabase.app; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
}

async function api(request,response,name) {
  if (request.method !== 'POST') throw Object.assign(new Error('Method not allowed.'), {status:405});
  const origin=request.headers.origin;if(origin && !allowedOrigins.has(origin)) throw Object.assign(new Error('Origin not allowed.'), {status:403});
  const handler=handlers[name];if(!handler)throw Object.assign(new Error('Endpoint not found.'), {status:404});
  const actor=await authenticate(request);const result=await handler(await readBody(request),actor);
  response.writeHead(200,{'Content-Type':'application/json; charset=utf-8'});response.end(JSON.stringify({result}));
}

async function staticFile(request,response,url) {
  const requested=url.pathname==='/'?'/index.html':decodeURIComponent(url.pathname);
  let path=resolve(dist,`.${requested}`);
  if(path!==dist && !path.startsWith(`${dist}${sep}`))throw Object.assign(new Error('Invalid path.'),{status:400});
  try{if(!(await stat(path)).isFile())path=resolve(dist,'index.html');}catch{path=resolve(dist,'index.html');}
  const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
  response.writeHead(200,{'Content-Type':types[extname(path)]??'application/octet-stream'});response.end(await readFile(path));
}

const server=createServer(async(request,response)=>{
  securityHeaders(response);
  try{
    if(request.url==='/healthz'){response.writeHead(200,{'Content-Type':'application/json; charset=utf-8'});response.end('{"status":"ok"}');return;}
    const forwardedHost=String(request.headers['x-forwarded-host']??'').split(',')[0].trim();
    const requestHost=forwardedHost||request.headers.host;
    if(!allowedHosts.has(requestHost))throw Object.assign(new Error('Host not allowed.'),{status:421});
    const url=new URL(request.url??'/',`http://${request.headers.host}`);
    if(url.pathname.startsWith('/api/'))await api(request,response,url.pathname.slice(5));else await staticFile(request,response,url);
  }catch(error){const status=Number.isInteger(error.status)?error.status:500;response.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});response.end(JSON.stringify({error:{message:status===500?'Request failed.':error.message}}));if(status===500)console.error(error);}
});

const listenHost = hostedMode ? '0.0.0.0' : '127.0.0.1';
server.listen(port,listenHost,()=>console.log(`CrayFarm admin listening on ${listenHost}:${port} (authorized: ${adminEmail})`));
