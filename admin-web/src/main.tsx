import React, {useEffect, useRef, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {initializeApp} from 'firebase/app';
import {connectAuthEmulator, getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, User} from 'firebase/auth';
import {connectFunctionsEmulator, getFunctions, httpsCallable} from 'firebase/functions';
import {AlertTriangle, ArrowLeft, ArrowRight, Building2, CheckCircle2, ChevronRight, CircleOff, Cpu, Database, LayoutDashboard, LogIn, LogOut, RefreshCw, ScrollText, ShieldCheck, Sprout, UserRound, UsersRound} from 'lucide-react';
import './style.css';
import DeviceRegistry from './Devices';
import Activity from './Activity';
const useEmulators = import.meta.env.VITE_USE_EMULATORS === 'true';
const localProduction = import.meta.env.VITE_LOCAL_PRODUCTION === 'true';
const productionConfig = {
 apiKey:import.meta.env.VITE_FIREBASE_API_KEY || 'AIzaSyCM8AuSYJw7l2qxnvbKTxS2a8p2Lktja6U',
 appId:import.meta.env.VITE_FIREBASE_APP_ID || '1:356838404753:web:7f79b20d4c68935b188530',
 projectId:import.meta.env.VITE_FIREBASE_PROJECT_ID || 'crayfarm-eb2ea',
 authDomain:import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || 'crayfarm-eb2ea.firebaseapp.com',
 databaseURL:import.meta.env.VITE_FIREBASE_DATABASE_URL || 'https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app',
 storageBucket:import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || 'crayfarm-eb2ea.firebasestorage.app',
 messagingSenderId:import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '356838404753',
 measurementId:import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || 'G-6FFXTBYF8L'
};
const emulatorConfig = {
 apiKey:'demo-key',
 appId:'demo-crayfarm-web',
 projectId:'demo-crayfarm',
 authDomain:'demo-crayfarm.firebaseapp.com',
 databaseURL:'https://demo-crayfarm-default-rtdb.firebaseio.com'
};
const app = initializeApp(useEmulators ? emulatorConfig : productionConfig);
const auth = getAuth(app), functions = getFunctions(app,import.meta.env.VITE_FIREBASE_FUNCTIONS_REGION || 'asia-southeast1');
if(useEmulators){connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});connectFunctionsEmulator(functions,'127.0.0.1',5001);}
const call = async (name:string,data:unknown = {}) => {
 if(localProduction){
  const token=await auth.currentUser?.getIdToken();
  if(!token)throw new Error('Sign in required.');
  const response=await fetch(`/api/${encodeURIComponent(name)}`,{method:'POST',headers:{'Authorization':`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify(data)});
  const payload=await response.json().catch(()=>({error:{message:'Invalid local backend response.'}}));
  if(!response.ok)throw new Error(payload.error?.message || 'Local backend request failed.');
  return payload.result;
 }
 return (await httpsCallable(functions,name)(data)).data as any;
};
type Person = {uid:string;email:string|null;name:string|null;role:string|null;disabled:boolean|null;active:boolean|null;approved:boolean|null;farmId:string|null;profileExists:boolean;createdAt?:string;websiteAdmin:boolean;historical?:boolean;authExists?:boolean;previousFarmId?:string|null;employmentStatus?:string|null;removedAt?:number|null;resignedAt?:number|null};
type Farm = {farmId:string;farmName:string|null;description:string|null;ownerUid:string|null;ownerName:string|null;ownerEmail:string|null;createdAt:number|null};
type FarmDetail = {farm:Farm;owner:Person|null;members:Array<{uid:string;fullName:string|null;email:string|null;role:string|null;status:string|null;active:boolean|null;joinedAt:number|null;leftAt:number|null}>;ponds:Array<{pondId:string;pondName:string|null;deviceId:string|null;active:boolean|null;createdAt:number|null}>;devices:Array<{deviceId:string;active:boolean|null;status:string|null;farmId:string|null;pondId:string|null;linkedAt:number|null;registeredAt:number|null}>;truncated:{members:boolean;devices:boolean}};
const date = (value:number|null) => value ? new Date(value).toLocaleString() : 'Unknown';
type AccountCounts = {total:number; owners:number; workers:number; disabled:number; missingProfiles:number};
type InventoryCounts = {farms:number; devices:number};
const isActiveWorker=(member:any)=>member.role==='worker'&&member.active!==false&&!['removed','resigned'].includes(member.status??'');
const isFormerWorker=(member:any)=>member.role==='worker'&&!isActiveWorker(member);
const isFormerPerson=(person:Person)=>person.historical===true||['removed','resigned'].includes(person.employmentStatus??'');
const navItems=[
 {label:'Overview',icon:LayoutDashboard},
 {label:'Users',icon:UsersRound},
 {label:'Farms',icon:Building2},
 {label:'Devices',icon:Cpu},
 {label:'Activity',icon:ScrollText}
];
type UserFilters = {search:string; searchBy:string; role:string; status:string};
const defaultUserFilters: UserFilters = {search:'', searchBy:'email', role:'', status:''};
const overviewCards = [
 {label:'Accounts', key:'total', icon:UsersRound, section:'Users', filters:defaultUserFilters},
 {label:'Farm owners', key:'owners', icon:Building2, section:'Users', filters:{...defaultUserFilters, role:'admin'}},
 {label:'Farm workers', key:'workers', icon:UserRound, section:'Users', filters:{...defaultUserFilters, role:'worker'}},
 {label:'Disabled', key:'disabled', icon:CircleOff, section:'Users', filters:{...defaultUserFilters, status:'disabled'}},
 {label:'Farms', key:'farms', icon:Sprout, section:'Farms'},
 {label:'Devices', key:'devices', icon:Cpu, section:'Devices'},
] as const;

function AccountBreakdown({counts}: {counts: AccountCounts}) {
 const {total, owners, workers} = counts;
 const breakdown = [
  {key:'owners', label:'Farm owners', count:owners},
  {key:'workers', label:'Farm workers', count:workers},
  {key:'other', label:'Other accounts', count:total - owners - workers},
 ];
 const ownerShare = total ? owners / total * 100 : 0;
 const workerEnd = total ? (owners + workers) / total * 100 : 0;
 const background = total
  ? `conic-gradient(var(--account-owners) 0 ${ownerShare}%, var(--account-workers) ${ownerShare}% ${workerEnd}%, var(--account-other) ${workerEnd}% 100%)`
  : undefined;

 return (
  <section className="panel account-breakdown" aria-labelledby="account-breakdown-title">
   <h2 id="account-breakdown-title">Account breakdown</h2>
   <div className="account-breakdown-body">
    <div className="account-breakdown-ring" style={{background}} role="img" aria-label={`${total.toLocaleString()} total accounts`}>
     <div aria-hidden="true"><strong>{total.toLocaleString()}</strong><span>Total accounts</span></div>
    </div>
    <ul className="account-breakdown-legend">
     {breakdown.map(({key, label, count}) => (
      <li key={key}>
       <span className={`account-breakdown-swatch ${key}`} aria-hidden="true"/>
       <span>{label}</span>
       <b>{count.toLocaleString()}</b>
      </li>
     ))}
    </ul>
   </div>
  </section>
 );
}

function App(){
 const [user,setUser]=useState<User|null>(null),[ready,setReady]=useState(false),[allowed,setAllowed]=useState(false);
 const [email,setEmail]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const [tab,setTab]=useState('Overview'),[selected,setSelected]=useState<Person|null>(null),[ops,setOps]=useState<any[]>([]),[counts,setCounts]=useState<AccountCounts|null>(null),[notice,setNotice]=useState('');
 const [userFilters, setUserFilters] = useState(defaultUserFilters);
 const [userPage, setUserPage] = useState<{
  people:Person[]; filters:UserFilters; cursor:string|null; next:string|null;
 } | null>(null);
 const userRequest = useRef(0);
 const activeRuns = useRef(0);
 const heading = useRef<HTMLHeadingElement>(null);
 const [inventory, setInventory] = useState<InventoryCounts|null>(null);
 const [farms,setFarms]=useState<Farm[]>([]),[farmCursor,setFarmCursor]=useState<string|null>(null),[farmDetail,setFarmDetail]=useState<FarmDetail|null>(null);
 const [currentFarmCursor, setCurrentFarmCursor] = useState<string|null>(null);
 const [farmsLoaded, setFarmsLoaded] = useState(false);
 const loadingFarms = useRef(false);
 async function run(fn:()=>Promise<void>){
  activeRuns.current++;
  setBusy(true);
  setError('');
  try { await fn(); }
  catch (e:any) { setError(e.message || 'Request failed.'); }
  finally { if (--activeRuns.current === 0) setBusy(false); }
 }
 useEffect(()=>onAuthStateChanged(auth,async u=>{setUser(u);setAllowed(false);setReady(false);setError('');setTab('Overview');userRequest.current++;setUserPage(null);setUserFilters(defaultUserFilters);setSelected(null);setCounts(null);setInventory(null);setOps([]);setFarms([]);setFarmsLoaded(false);setFarmCursor(null);setCurrentFarmCursor(null);setFarmDetail(null);try{if(u){await call('session');setAllowed(true);}}catch{setError('Website administrator access could not be verified.');}finally{setReady(true);}}),[]);
 useEffect(() => { heading.current?.focus(); }, [tab]);

 async function loadUsers(filters:UserFilters, cursor:string|null = null) {
  const request = ++userRequest.current;
  const {search, searchBy, role, status} = filters;
  try {
    const result = await call(role === 'former' ? 'formerWorkers' : 'directory',
      {cursor, search:search.trim(), searchBy, ...(role === 'former' ? {} : {role,status})});
    if (request !== userRequest.current) return;
    setUserPage({people:result.users, filters, cursor, next:result.cursor});
    setSelected(null);
  } catch (error) {
    if (request === userRequest.current) throw error;
  }
 }

 function openOverviewCard(card: typeof overviewCards[number]) {
  if (card.section === 'Users') {
    userRequest.current++;
    setUserFilters(card.filters);
    setSelected(null);
    const applied = userPage?.filters;
    if (!applied || applied.search.trim() || applied.role !== card.filters.role || applied.status !== card.filters.status) {
      setUserPage(null);
      void run(() => loadUsers(card.filters));
    }
  } else if (card.section === 'Farms') {
    setFarmDetail(null);
  }
  setTab(card.section);
 }

 async function loadFarms(cursor:string|null = null) {
  if (loadingFarms.current) return;
  loadingFarms.current = true;
  try {
    const result = await call('farmDirectory', {cursor});
    setFarms(result.farms);
    setFarmCursor(result.cursor);
    setCurrentFarmCursor(cursor);
    setFarmsLoaded(true);
    setFarmDetail(null);
  } finally {
    loadingFarms.current = false;
  }
 }
 async function openFarm(farmId:string){setFarmDetail(await call('farmDetails',{farmId}));}
 useEffect(() => {
  if (!allowed) return;
  void run(async () => {
    const results = await Promise.allSettled([
      count(),
      loadUsers(defaultUserFilters),
      call('recentOperations').then(setOps),
    ]);
    const failure = results.find(result => result.status === 'rejected');
    if (failure?.status === 'rejected') throw failure.reason;
  });
 }, [allowed]);
 useEffect(()=>{if(allowed&&tab==='Farms'&&!farmsLoaded)void run(()=>loadFarms());},[allowed,tab,farmsLoaded]);
 async function countAccounts() {
  const totals: AccountCounts = {total:0, owners:0, workers:0, disabled:0, missingProfiles:0};
  const keys = Object.keys(totals) as Array<keyof AccountCounts>;
  let cursor: string | undefined;
  let pages = 0;
  do {
    const page = await call('overview', {cursor});
    for (const key of keys) totals[key] += page[key];
    cursor = page.cursor || undefined;
    pages++;
  } while (cursor && pages < 100);
  setCounts(totals);
 }

 async function count() {
  const [accounts, inventoryResult] = await Promise.allSettled([
    countAccounts(),
    call('overviewInventory'),
  ]);
  if (inventoryResult.status === 'fulfilled') setInventory(inventoryResult.value);
  else setInventory(null);
  if (accounts.status === 'rejected') throw accounts.reason;
  if (inventoryResult.status === 'rejected') throw new Error('Farm and device totals could not be loaded.');
 }
 async function mutate(p:Person,action:string){
  if(!window.confirm(`${action === 'enable'?'Re-enable':action === 'disable'?'Disable':'Revoke website access for'} ${p.email || p.uid}? Farm approval and membership will be preserved.`))return;
  const request={targetUid:p.uid,action,operationId:`${Date.now()}_${crypto.randomUUID()}`};
  // Persist intent before the request so an uncertain response can reuse the exact ID.
  sessionStorage.setItem('crayfarm-pending',JSON.stringify(request));
  await perform(request);
 }
 async function perform(request:any){
  const directoryRequest = userRequest.current;
  const result = await call(request.actorUid?'reconcileOperation':'mutateAccount',
    request.actorUid?{operationId:request.operationId}:request);
  setNotice(`Operation ${result.operationId}: ${result.status}`);
  if(result.status==='succeeded')sessionStorage.removeItem('crayfarm-pending');
  if(userPage && directoryRequest === userRequest.current)await loadUsers(userPage.filters,userPage.cursor);
  setOps(await call('recentOperations'));
 }
 const overviewCounts = {...counts, ...inventory};
 if(!ready)return <div className="center">Verifying your session…</div>;
 if(!user)return <main className="login"><section className="intro"><div className="brand"><span className="brand-mark"><Sprout size={21}/></span><span>CrayFarm</span></div><div className="intro-copy"><span className="eyebrow">OPERATIONS CONSOLE</span><h1>Manage every farm<br/>with confidence.</h1><p>Secure oversight for people, farms, ponds, and connected devices.</p></div></section><form onSubmit={e=>{e.preventDefault();void run(async()=>{await signInWithEmailAndPassword(auth,email,password);});}}><div className="mobile-brand brand"><span className="brand-mark"><Sprout size={20}/></span><span>CrayFarm</span></div><span className="form-icon"><ShieldCheck size={22}/></span><h2>Welcome back</h2><p>Sign in to the administration console.</p><label>Email address<input type="email" required autoComplete="username" placeholder="admin@example.com" value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Password<input type="password" required autoComplete="current-password" placeholder="Enter your password" value={password} onChange={e=>setPassword(e.target.value)}/></label>{error&&<p role="alert" className="error">{error}</p>}<button disabled={busy}>{busy?'Signing in…':'Sign in securely'}<LogIn size={17}/></button></form></main>;
 if(!allowed)return <main className="center"><span className="state-icon danger-state"><CircleOff size={28}/></span><h1>Access not authorized</h1><p>{error || 'This account does not have website administrator access.'}</p><button onClick={()=>void signOut(auth)}><LogOut size={17}/>Sign out</button></main>;
 return <div className="shell"><aside><div className="brand"><span className="brand-mark"><Sprout size={20}/></span><span>CrayFarm</span></div><span className="console-label">ADMIN CONSOLE</span><nav aria-label="Primary navigation">{navItems.map(({label,icon:Icon})=><button className={tab===label?'nav active':'nav'} key={label} aria-current={tab===label?'page':undefined} onClick={()=>setTab(label)}><span className="nav-icon"><Icon size={18}/></span>{label}</button>)}</nav><div className="aside-bottom"><div className="admin-account"><span className="admin-avatar"><UserRound size={16}/></span><div><small>Administrator</small><p>{user.email}</p></div></div><button className="nav signout" onClick={()=>void signOut(auth)}><span className="nav-icon"><LogOut size={18}/></span>Sign out</button></div></aside><main className="workspace"><header><div><span className="breadcrumb">CrayFarm / {tab}</span><h1 ref={heading} tabIndex={-1}>{tab}</h1></div></header>{useEmulators&&<div className="banner"><Database size={18}/>Test environment</div>}{error&&<p role="alert" className="error"><AlertTriangle size={18}/>{error}</p>}{notice&&<p role="status" className="banner"><CheckCircle2 size={18}/>{notice}</p>}
 {tab==='Overview'&&<><div className="page-actions"><button className="secondary" disabled={busy} onClick={()=>void run(count)}><RefreshCw size={16} className={busy?'spin':''}/>{busy?'Refreshing…':'Refresh data'}</button></div><div className="cards">{overviewCards.map(card => (
  <button type="button" key={card.key} className={`metric metric-${card.key}`} onClick={() => openOverviewCard(card)}>
    <span className="metric-icon"><card.icon size={23} aria-hidden="true"/></span>
    <span className="metric-content">
      <span className="metric-label">{card.label}</span>
      <strong>{overviewCounts[card.key]?.toLocaleString() ?? '—'}</strong>
    </span>
    <ChevronRight className="metric-arrow" size={18} aria-hidden="true"/>
  </button>
 ))}</div>
  {counts && <AccountBreakdown counts={counts}/>}
  {counts && counts.missingProfiles > 0 && (
    <div className="quick-row panel">
      <div className="quick-copy">
        <span className="quick-icon"><AlertTriangle size={18} aria-hidden="true"/></span>
        <div>
          <b>{counts.missingProfiles} incomplete {counts.missingProfiles === 1 ? 'profile' : 'profiles'}</b>
          <small>Review accounts that need profile information.</small>
        </div>
      </div>
      <button className="secondary compact" onClick={() => setTab('Users')}>
        <UsersRound size={15}/>Manage users
      </button>
    </div>
  )}
 </>}
 {tab==='Users'&&<><form className="filters" onSubmit={e=>{e.preventDefault();void run(()=>loadUsers(userFilters));}}><select aria-label="Search field" value={userFilters.searchBy} onChange={e=>setUserFilters({...userFilters,searchBy:e.target.value})}><option value="email">Email</option><option value="uid">UID</option></select><input aria-label="Search users" placeholder="Email or UID" value={userFilters.search} onChange={e=>setUserFilters({...userFilters,search:e.target.value})}/><select aria-label="Role" value={userFilters.role} onChange={e=>setUserFilters({...userFilters,role:e.target.value})}><option value="">All roles</option><option value="admin">Farm owner</option><option value="worker">Worker</option><option value="former">Former worker</option><option value="unknown">Unknown</option></select><select aria-label="Auth status" value={userFilters.status} disabled={userFilters.role==='former'} onChange={e=>setUserFilters({...userFilters,status:e.target.value})}><option value="">All states</option><option value="enabled">Enabled</option><option value="disabled">Disabled</option></select><button disabled={busy}>Search</button></form><div className="table-wrap"><table><thead><tr><th>Account</th><th>Role</th><th>Status</th><th>Record</th><th/></tr></thead><tbody>{(userPage?.people ?? []).map(p=><tr key={p.uid}><td><div className="account-cell"><span className="avatar">{(p.name||p.email||'?').charAt(0).toUpperCase()}</span><div><b>{p.name||'Unnamed account'}</b><small>{p.email||p.uid}</small></div></div></td><td><span className="role-badge">{isFormerPerson(p)?'Former worker':p.role==='admin'?'Farm owner':p.role==='worker'?'Farm worker':'Unknown'}</span></td><td><span className={isFormerPerson(p)?'status disabled':p.disabled?'status disabled':'status enabled'}><i/>{isFormerPerson(p)?(p.employmentStatus||'Former'):p.disabled?'Disabled':'Enabled'}</span></td><td><span className={`record-state ${isFormerPerson(p)?'archived':p.profileExists?'complete':'incomplete'}`}>{isFormerPerson(p)?'Archived':p.profileExists?'Complete':'Incomplete'}</span></td><td><button className="secondary compact" onClick={()=>setSelected(p)}>Details</button></td></tr>)}</tbody></table>{!busy&&!userPage?.people.length&&<p className="empty">{userPage?.filters.role==='former'?'No former workers found.':'No accounts found.'}</p>}</div><div className="pagination"><span/><button className="secondary" disabled={!userPage?.next||busy} onClick={()=>{if(userPage?.next)void run(()=>loadUsers(userPage.filters,userPage.next));}}>Next page</button></div>{selected&&<section className="panel details"><div className="section-head"><h2>{selected.name||selected.email||'Account details'}</h2><button className="secondary compact" onClick={()=>setSelected(null)}>Close</button></div><dl>{Object.entries(selected).map(([k,v])=><React.Fragment key={k}><dt>{k}</dt><dd>{v===null?'Unknown':String(v)}</dd></React.Fragment>)}</dl>{!isFormerPerson(selected)&&<div className="actions"><button disabled={busy||selected.uid===user.uid} onClick={()=>void run(()=>mutate(selected,selected.disabled?'enable':'disable'))}>{selected.disabled?'Re-enable':'Disable account'}</button>{selected.websiteAdmin&&<button className="danger" disabled={busy||selected.uid===user.uid} onClick={()=>void run(()=>mutate(selected,'revokeAdmin'))}>Revoke admin access</button>}</div>}</section>}</>}
 {tab==='Farms'&&<>{!farmDetail&&<><div className="page-actions"><button className="secondary" disabled={busy} onClick={()=>void run(()=>loadFarms(currentFarmCursor))}><RefreshCw size={16} className={busy?'spin':''}/>{busy?'Refreshing…':'Refresh farms'}</button></div><div className="farm-grid">{farms.map(farm=><article className="farm-card" key={farm.farmId}><span className="farm-avatar"><Building2 size={21}/></span><div><h2>{farm.farmName||'Unnamed farm'}</h2><p>{farm.ownerName||farm.ownerEmail||'Owner unavailable'}</p><small>{farm.farmId}</small></div><button className="icon-button" aria-label={`View ${farm.farmName||'farm'}`} onClick={()=>void run(()=>openFarm(farm.farmId))}><ChevronRight size={18}/></button></article>)}</div>{!farms.length&&!busy&&<p className="empty panel">No farms found.</p>}<div className="pagination"><span/><button className="secondary" disabled={!farmCursor||busy} onClick={()=>void run(()=>loadFarms(farmCursor!))}>Next<ArrowRight size={16}/></button></div></>}{farmDetail&&<section className="farm-detail"><div className="section-head farm-heading"><div><span className="eyebrow">FARM DETAILS</span><h2>{farmDetail.farm.farmName||'Unnamed farm'}</h2><small>{farmDetail.farm.farmId}</small></div><button className="secondary compact" onClick={()=>setFarmDetail(null)}><ArrowLeft size={15}/>Farms</button></div>{farmDetail.farm.description&&<p className="farm-description">{farmDetail.farm.description}</p>}<div className="farm-stats"><article><span>Workers</span><strong>{farmDetail.members.filter(isActiveWorker).length}</strong></article><article><span>Ponds</span><strong>{farmDetail.ponds.length}</strong></article><article><span>Devices</span><strong>{farmDetail.devices.length}</strong></article></div><div className="farm-sections"><section className="panel farm-section"><h2>Owner</h2>{farmDetail.owner?<div className="person-line"><span className="avatar">{(farmDetail.owner.name||farmDetail.owner.email||'?').charAt(0).toUpperCase()}</span><div><b>{farmDetail.owner.name||'Unnamed owner'}</b><small>{farmDetail.owner.email||farmDetail.owner.uid}</small></div><span className={farmDetail.owner.disabled?'status disabled':'status enabled'}><i/>{farmDetail.owner.disabled?'Disabled':'Enabled'}</span></div>:<p className="empty compact-empty">Owner account unavailable.</p>}</section><section className="panel farm-section"><h2>Workers</h2>{farmDetail.members.filter(isActiveWorker).map(member=><div className="person-line" key={member.uid}><span className="avatar">{(member.fullName||member.email||'?').charAt(0).toUpperCase()}</span><div><b>{member.fullName||'Unnamed worker'}</b><small>{member.email||member.uid}</small></div><span className={member.active===false?'status disabled':'status enabled'}><i/>{member.status||(member.active===false?'Inactive':'Active')}</span></div>)}{!farmDetail.members.some(isActiveWorker)&&<p className="empty compact-empty">No workers.</p>}</section>{farmDetail.members.some(isFormerWorker)&&<section className="panel farm-section wide former-workers"><h2>Former workers</h2>{farmDetail.members.filter(isFormerWorker).map(member=><div className="person-line" key={member.uid}><span className="avatar">{(member.fullName||member.email||'?').charAt(0).toUpperCase()}</span><div><b>{member.fullName||'Unnamed worker'}</b><small>{member.email||member.uid}</small></div><span className="status disabled"><i/>{member.status||'Former'}</span></div>)}</section>}<section className="panel farm-section wide"><h2>Ponds and devices</h2>{farmDetail.ponds.map(pond=>{const device=farmDetail.devices.find(item=>item.deviceId===pond.deviceId);return <div className="pond-line" key={pond.pondId}><div><b>{pond.pondName||'Unnamed pond'}</b><small>{pond.pondId}</small></div><div><span>Device</span><b>{pond.deviceId||'Not linked'}</b></div><span className={device?.status==='assigned'?'status enabled':'status disabled'}><i/>{device?.status||'Unknown'}</span></div>})}{!farmDetail.ponds.length&&<p className="empty compact-empty">No linked ponds or devices.</p>}</section></div>{(farmDetail.truncated.members||farmDetail.truncated.devices)&&<p className="banner">Showing the first 100 records in a section.</p>}<small className="farm-created">Created {date(farmDetail.farm.createdAt)}</small></section>}</>}
 <div hidden={tab!=='Devices'}><DeviceRegistry active={tab==='Devices'} api={call} onOperation={async message=>{setNotice(message);setOps(await call('recentOperations'));}}/></div>
 <div hidden={tab!=='Activity'}><Activity operations={ops} busy={busy} onRefresh={()=>run(async()=>setOps(await call('recentOperations')))} onRecover={kind=>run(async()=>{const storageKey=kind==='device'?'crayfarm-device-pending':'crayfarm-pending',saved=sessionStorage.getItem(storageKey);if(!saved){setNotice('No interrupted request.');return;}const request=JSON.parse(saved),result=await call(kind==='device'?'mutateDevice':'mutateAccount',request);if(result.status==='succeeded')sessionStorage.removeItem(storageKey);setNotice(`Operation ${result.operationId}: ${result.status}`);setOps(await call('recentOperations'));})} onReconcile={op=>run(async()=>{const result=await call(op.kind==='device'?'reconcileDeviceOperation':'reconcileOperation',{operationId:op.operationId});setNotice(`Operation ${result.operationId}: ${result.status}`);setOps(await call('recentOperations'));})}/></div>
 </main></div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
