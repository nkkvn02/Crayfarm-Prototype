import {createRequire} from 'node:module';
import {databaseURL, initializeCliAdmin, projectId} from './firebase-cli-admin.mjs';

const functionsRequire=createRequire(new URL('../functions/package.json',import.meta.url));
const {deleteApp}=functionsRequire('firebase-admin/app');
const {getAuth}=functionsRequire('firebase-admin/auth');
const {getDatabase}=functionsRequire('firebase-admin/database');
const app=initializeCliAdmin('production-data-audit');
const auth=getAuth(app),db=getDatabase(app),findings=[];
const knownRoots=new Set(['users','employeeRequests','deviceUsers','devices','farms','_crayfarmAdmin']);
const add=(severity,type,path,details={})=>findings.push({severity,type,path,...details});
const object=value=>value&&typeof value==='object'&&!Array.isArray(value)?value:{};

async function shallow(path=''){
  const token=await app.options.credential.getAccessToken();
  const url=`${databaseURL}/${path}.json?shallow=true`;
  const response=await fetch(url,{headers:{Authorization:`Bearer ${token.access_token}`}});
  if(!response.ok)throw new Error(`Shallow read failed at ${path||'/'}: ${response.status}`);
  return Object.keys(object(await response.json()));
}
async function allAuthUsers(){
  const users=[];let token;
  do{const page=await auth.listUsers(1000,token);users.push(...page.users);token=page.pageToken;}while(token);
  return users;
}
async function fields(path,names){
  const values=await Promise.all(names.map(name=>db.ref(`${path}/${name}`).get()));
  return Object.fromEntries(names.map((name,index)=>[name,values[index].val()]));
}

try{
  const [authUsers,profileSnap,requestSnap,deviceSnap,deviceUserSnap,rootKeys,farmIds]=await Promise.all([
    allAuthUsers(),db.ref('users').get(),db.ref('employeeRequests').get(),db.ref('devices').get(),db.ref('deviceUsers').get(),shallow(),shallow('farms')
  ]);
  const authByUid=new Map(authUsers.map(user=>[user.uid,user]));
  const profiles=object(profileSnap.val()),requests=object(requestSnap.val()),devices=object(deviceSnap.val()),deviceUsers=object(deviceUserSnap.val());
  for(const root of rootKeys)if(!knownRoots.has(root))add('review','unknown_root',root);
  for(const user of authUsers)if(!profiles[user.uid]){
    if(deviceUsers[user.uid])add('info','device_auth_without_profile',`deviceUsers/${user.uid}`,{uid:user.uid,deviceId:deviceUsers[user.uid]?.deviceId??null});
    else if(user.customClaims?.crayfarmAdmin===true)add('info','website_admin_without_profile',`users/${user.uid}`,{uid:user.uid,disabled:user.disabled});
    else add('high','auth_without_profile',`users/${user.uid}`,{uid:user.uid,disabled:user.disabled});
  }
  for(const [uid,profile] of Object.entries(profiles)){
    if(!authByUid.has(uid)){
      const historical=!profile?.farmId&&['removed','resigned'].includes(profile?.employmentStatus);
      add(historical?'info':'high',historical?'historical_profile_without_auth':'profile_without_auth',`users/${uid}`,{uid,role:profile?.role??null,farmId:profile?.farmId??null,previousFarmId:profile?.previousFarmId??null,employmentStatus:profile?.employmentStatus??null,active:profile?.active??null,approved:profile?.approved??null,createdAt:profile?.createdAt??null,removedAt:profile?.removedAt??null});
    }
    if(!['admin','worker'].includes(profile?.role))add('high','invalid_role',`users/${uid}/role`,{uid,value:profile?.role??null});
    if(profile?.farmId&&!farmIds.includes(profile.farmId))add('high','user_missing_farm',`users/${uid}/farmId`,{uid,farmId:profile.farmId});
  }
  const membershipByUid=new Map();
  const pondKeysByFarm=new Map();
  for(const farmId of farmIds){
    const [farm,membersSnap,pondIds]=await Promise.all([
      fields(`farms/${farmId}`,['farmName','ownerUid','createdAt']),db.ref(`farms/${farmId}/members`).get(),shallow(`farms/${farmId}/ponds`)
    ]);
    pondKeysByFarm.set(farmId,new Set(pondIds));
    if(!farm.farmName)add('medium','farm_missing_name',`farms/${farmId}/farmName`,{farmId});
    if(!farm.ownerUid)add('high','farm_missing_owner',`farms/${farmId}/ownerUid`,{farmId});
    else{
      const ownerProfile=profiles[farm.ownerUid],ownerAuth=authByUid.get(farm.ownerUid);
      if(!ownerProfile||!ownerAuth)add('high','farm_owner_missing_account',`farms/${farmId}/ownerUid`,{farmId,ownerUid:farm.ownerUid});
      else if(ownerProfile.role!=='admin'||ownerProfile.farmId!==farmId)add('high','farm_owner_mismatch',`farms/${farmId}/ownerUid`,{farmId,ownerUid:farm.ownerUid});
    }
    const members=object(membersSnap.val());
    for(const [uid,member] of Object.entries(members)){
      const profile=profiles[uid],identity=authByUid.get(uid),list=membershipByUid.get(uid)??[];list.push(farmId);membershipByUid.set(uid,list);
      if(!profile&&!identity)add('critical','membership_without_account',`farms/${farmId}/members/${uid}`,{farmId,uid,status:member?.status??null,active:member?.active??null,namePresent:!!member?.fullName,emailPresent:!!member?.email,joinedAt:member?.joinedAt??null,leftAt:member?.leftAt??null,removedAt:member?.removedAt??null});
      else if(!profile)add('high','membership_without_profile',`farms/${farmId}/members/${uid}`,{farmId,uid});
      else if(!identity){const historical=member?.active===false&&['removed','resigned'].includes(member?.status);add(historical?'info':'high',historical?'historical_membership_without_auth':'membership_without_auth',`farms/${farmId}/members/${uid}`,{farmId,uid,status:member?.status??null,active:member?.active??null,profileFarmId:profile?.farmId??null,employmentStatus:profile?.employmentStatus??null});}
      if(profile&&member?.role==='worker'){
        if(!member.fullName&&(profile.fullName||identity?.displayName))add('safe_fix','membership_missing_name',`farms/${farmId}/members/${uid}/fullName`,{farmId,uid,source:profile.fullName?'profile':'auth'});
        if(!member.email&&(profile.email||identity?.email))add('safe_fix','membership_missing_email',`farms/${farmId}/members/${uid}/email`,{farmId,uid,source:profile.email?'profile':'auth'});
        if(profile.farmId&&profile.farmId!==farmId)add('high','membership_farm_mismatch',`farms/${farmId}/members/${uid}`,{farmId,uid,profileFarmId:profile.farmId});
        if(identity?.disabled&&member.active!==false)add('info','disabled_auth_active_membership',`farms/${farmId}/members/${uid}`,{farmId,uid});
      }
    }
  }
  for(const [uid,farmList] of membershipByUid)if(farmList.length>1)add('high','multiple_farm_memberships',`farms/*/members/${uid}`,{uid,farmIds:farmList});
  for(const [code,request] of Object.entries(requests)){
    const uid=request?.workerUid;
    if(!uid||!authByUid.has(uid)){const historical=['approved','replaced','rejected'].includes(request?.status)&&['removed','resigned'].includes(profiles[uid]?.employmentStatus);add(historical?'info':'high',historical?'historical_request_without_auth':'request_without_auth',`employeeRequests/${code}`,{code,uid:uid??null,status:request?.status??null,profileExists:!!profiles[uid],createdAt:request?.createdAt??null,approvedAt:request?.approvedAt??null,replacedAt:request?.replacedAt??null});}
    else if(request.status==='pending'&&profiles[uid]?.farmId)add('medium','stale_pending_request',`employeeRequests/${code}`,{code,uid,farmId:profiles[uid].farmId});
  }
  const authUidToDevice=new Map();
  for(const [deviceId,device] of Object.entries(devices)){
    if(device?.deviceId&&device.deviceId!==deviceId)add('high','device_id_mismatch',`devices/${deviceId}/deviceId`,{deviceId,value:device.deviceId});
    if(device?.authUid){
      if(authUidToDevice.has(device.authUid))add('critical','duplicate_device_auth',`devices/${deviceId}/authUid`,{deviceId,otherDeviceId:authUidToDevice.get(device.authUid)});
      authUidToDevice.set(device.authUid,deviceId);
      if(!authByUid.has(device.authUid))add('high','device_auth_missing',`devices/${deviceId}/authUid`,{deviceId,authUid:device.authUid});
      if(deviceUsers[device.authUid]?.deviceId!==deviceId)add('high','device_user_mapping_mismatch',`deviceUsers/${device.authUid}`,{deviceId,authUid:device.authUid});
    }else add('high','device_missing_auth',`devices/${deviceId}/authUid`,{deviceId});
    if(device?.status==='assigned'){
      if(!device.farmId||!farmIds.includes(device.farmId))add('high','assigned_device_missing_farm',`devices/${deviceId}/farmId`,{deviceId,farmId:device.farmId??null});
      else if(!device.pondId||!pondKeysByFarm.get(device.farmId)?.has(device.pondId))add('high','assigned_device_missing_pond',`devices/${deviceId}/pondId`,{deviceId,farmId:device.farmId,pondId:device.pondId??null});
    }else if(device?.farmId||device?.pondId)add('medium','unassigned_device_has_links',`devices/${deviceId}`,{deviceId,status:device?.status??null});
  }
  for(const [uid,mapping] of Object.entries(deviceUsers))if(!devices[mapping?.deviceId]||devices[mapping.deviceId]?.authUid!==uid)add('high','orphan_device_user_mapping',`deviceUsers/${uid}`,{uid,deviceId:mapping?.deviceId??null});
  const bySeverity=Object.fromEntries(['critical','high','medium','safe_fix','info','review'].map(level=>[level,findings.filter(item=>item.severity===level).length]));
  console.log(JSON.stringify({projectId,scanned:{authUsers:authUsers.length,profiles:Object.keys(profiles).length,farms:farmIds.length,memberships:[...membershipByUid.values()].reduce((n,list)=>n+list.length,0),requests:Object.keys(requests).length,devices:Object.keys(devices).length,deviceUsers:Object.keys(deviceUsers).length},bySeverity,findings},null,2));
}finally{await deleteApp(app);}
