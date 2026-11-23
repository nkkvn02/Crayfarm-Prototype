import {createRequire} from 'node:module';
import {initializeCliAdmin, projectId} from './firebase-cli-admin.mjs';

const functionsRequire=createRequire(new URL('../functions/package.json',import.meta.url));
const {deleteApp}=functionsRequire('firebase-admin/app');
const {getAuth}=functionsRequire('firebase-admin/auth');
const {getDatabase}=functionsRequire('firebase-admin/database');
const apply=process.argv.includes('--apply');
const app=initializeCliAdmin('production-data-cleanup');
const auth=getAuth(app),db=getDatabase(app);
const object=value=>value&&typeof value==='object'&&!Array.isArray(value)?value:{};

async function allAuthUsers(){const users=[];let token;do{const page=await auth.listUsers(1000,token);users.push(...page.users);token=page.pageToken;}while(token);return users;}

try{
  const [authUsers,profilesSnap,requestsSnap]=await Promise.all([allAuthUsers(),db.ref('users').get(),db.ref('employeeRequests').get()]);
  const authIds=new Set(authUsers.map(user=>user.uid)),profiles=object(profilesSnap.val()),requests=object(requestsSnap.val());
  const farmIds=new Set();for(const profile of Object.values(profiles)){if(profile?.farmId)farmIds.add(profile.farmId);if(profile?.previousFarmId)farmIds.add(profile.previousFarmId);}
  const memberships=new Map(),ghostMemberships=[];
  for(const farmId of farmIds){const values=object((await db.ref(`farms/${farmId}/members`).get()).val());for(const [uid,value] of Object.entries(values)){const list=memberships.get(uid)??[];list.push({farmId,value});memberships.set(uid,list);if(!authIds.has(uid)&&!profiles[uid])ghostMemberships.push({farmId,uid,value});}}
  const abandoned=[];
  for(const [uid,profile] of Object.entries(profiles)){
    if(authIds.has(uid)||profile?.role!=='worker'||profile?.farmId||profile?.previousFarmId||memberships.has(uid))continue;
    const pending=Object.entries(requests).filter(([,request])=>request?.workerUid===uid&&request?.status==='pending');
    const other=Object.entries(requests).filter(([,request])=>request?.workerUid===uid&&request?.status!=='pending');
    if(pending.length&&other.length===0)abandoned.push({uid,profile,pending});
  }
  const preview={projectId,apply,ghostMemberships:ghostMemberships.map(item=>({path:`farms/${item.farmId}/members/${item.uid}`,uid:item.uid})),abandonedSignups:abandoned.map(item=>({profilePath:`users/${item.uid}`,uid:item.uid,requestPaths:item.pending.map(([code])=>`employeeRequests/${code}`)}))};
  if(!apply){console.log(JSON.stringify(preview,null,2));process.exitCode=preview.ghostMemberships.length||preview.abandonedSignups.length?2:0;}
  else{
    if(!ghostMemberships.length&&!abandoned.length){console.log(JSON.stringify({...preview,changed:false},null,2));}
    else{
      const cleanupId=`cleanup-${Date.now()}`,backup={createdAt:Date.now(),reason:'Remove records with no Firebase Auth identity and no valid farm relationship',ghostMemberships:ghostMemberships.map(item=>({path:`farms/${item.farmId}/members/${item.uid}`,value:item.value})),abandonedSignups:abandoned.map(item=>({profilePath:`users/${item.uid}`,profile:item.profile,requests:Object.fromEntries(item.pending)}))},updates={};
      updates[`_crayfarmAdmin/cleanupBackups/${cleanupId}`]=backup;
      for(const item of ghostMemberships)updates[`farms/${item.farmId}/members/${item.uid}`]=null;
      for(const item of abandoned){updates[`users/${item.uid}`]=null;for(const [code] of item.pending)updates[`employeeRequests/${code}`]=null;}
      await db.ref().update(updates);
      console.log(JSON.stringify({...preview,changed:true,backupPath:`_crayfarmAdmin/cleanupBackups/${cleanupId}`},null,2));
    }
  }
}finally{await deleteApp(app);}
