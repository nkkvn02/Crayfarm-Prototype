import {FormEvent, useEffect, useState} from 'react';
import {AlertTriangle, Cpu, Plus, RefreshCw, Search, ShieldCheck, ToggleLeft, ToggleRight, X} from 'lucide-react';

type Device = {
  deviceId:string;active:boolean|null;status:string|null;farmId:string|null;pondId:string|null;
  linkedAt:number|null;registeredAt:number|null;authConfigured:boolean;farmName:string|null;pondName:string|null;
};
type Props = {api:(name:string,data?:unknown)=>Promise<any>;onOperation:(message:string)=>Promise<void>};

export default function DeviceRegistry({api,onOperation}:Props) {
  const [devices,setDevices]=useState<Device[]>([]),[cursor,setCursor]=useState<string|null>(null);
  const [search,setSearch]=useState(''),[status,setStatus]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const [showRegister,setShowRegister]=useState(false),[deviceId,setDeviceId]=useState(''),[authUid,setAuthUid]=useState('');
  const pending=sessionStorage.getItem('crayfarm-device-pending');
  async function run(task:()=>Promise<void>){setBusy(true);setError('');try{await task();}catch(reason:any){setError(reason.message||'Request failed.');}finally{setBusy(false);}}
  async function load(next?:string){const result=await api('deviceDirectory',{cursor:next,search:search.trim(),status});setDevices(result.devices);setCursor(result.cursor);}
  useEffect(()=>{void run(()=>load());},[]);
  async function perform(request:any){sessionStorage.setItem('crayfarm-device-pending',JSON.stringify(request));const result=await api(request.actorUid?'reconcileDeviceOperation':'mutateDevice',request.actorUid?{operationId:request.operationId}:request);if(result.status==='succeeded')sessionStorage.removeItem('crayfarm-device-pending');await onOperation(`Device ${result.deviceId}: ${result.status}`);await load();}
  async function register(event:FormEvent){event.preventDefault();const request={deviceId:deviceId.trim().toUpperCase(),authUid:authUid.trim(),action:'register',operationId:`${Date.now()}_${crypto.randomUUID()}`};await perform(request);setDeviceId('');setAuthUid('');setShowRegister(false);}
  async function toggle(device:Device,action:'activate'|'deactivate'){if(!window.confirm(`${action==='activate'?'Activate':'Deactivate'} ${device.deviceId}?`))return;await perform({deviceId:device.deviceId,action,operationId:`${Date.now()}_${crypto.randomUUID()}`});}
  return <>
    {error&&<p role="alert" className="error"><AlertTriangle size={18}/>{error}</p>}
    {pending&&<div className="banner device-recovery"><span><AlertTriangle size={18}/>An earlier device request may need reconciliation.</span><button className="secondary compact" disabled={busy} onClick={()=>void run(()=>perform(JSON.parse(pending)))}><RefreshCw size={15}/>Recover</button></div>}
    <div className="device-toolbar"><form onSubmit={event=>{event.preventDefault();void run(()=>load());}}><div className="search-field"><Search size={17}/><input aria-label="Search Device ID" placeholder="Search device ID" value={search} onChange={event=>setSearch(event.target.value)}/></div><select aria-label="Device status" value={status} onChange={event=>setStatus(event.target.value)}><option value="">All states</option><option value="available">Available</option><option value="assigned">Assigned</option><option value="inactive">Inactive</option></select><button className="secondary" disabled={busy}><Search size={16}/>Search</button></form><button disabled={busy} onClick={()=>setShowRegister(value=>!value)}>{showRegister?<><X size={17}/>Cancel</>:<><Plus size={17}/>Register device</>}</button></div>
    {showRegister&&<form className="panel register-device" onSubmit={event=>void run(()=>register(event))}><div className="section-title"><span className="device-icon"><ShieldCheck size={19}/></span><div><h2>Register device</h2><small>Add a dedicated ESP32 authentication account.</small></div></div><div><label>Device ID<input required maxLength={128} placeholder="CRAY-…" value={deviceId} onChange={event=>setDeviceId(event.target.value.toUpperCase())}/></label><label>Firebase Auth UID<input required maxLength={128} placeholder="Dedicated ESP32 account UID" value={authUid} onChange={event=>setAuthUid(event.target.value)}/></label><button disabled={busy}>{busy?'Registering…':'Register device'}<Plus size={17}/></button></div></form>}
    <div className="device-grid">{devices.map(device=><article className="device-card" key={device.deviceId}><div className="device-card-head"><span className="device-icon"><Cpu size={20}/></span><div><h2>{device.deviceId}</h2><small>{device.authConfigured?'Authentication configured':'Authentication missing'}</small></div><span className={`status ${device.status==='available'||device.status==='assigned'?'enabled':'disabled'}`}><i/>{device.status||'Unknown'}</span></div><dl><dt>Farm</dt><dd>{device.farmName||device.farmId||'Not assigned'}</dd><dt>Pond</dt><dd>{device.pondName||device.pondId||'Not assigned'}</dd><dt>Registered</dt><dd>{device.registeredAt?new Date(device.registeredAt).toLocaleString():'Unknown'}</dd></dl><div className="device-actions">{device.status==='assigned'?<small>Assigned devices cannot be changed here.</small>:<button className="secondary compact" disabled={busy} onClick={()=>void run(()=>toggle(device,device.active===false?'activate':'deactivate'))}>{device.active===false?<ToggleRight size={16}/>:<ToggleLeft size={16}/>} {device.active===false?'Activate':'Deactivate'}</button>}</div></article>)}</div>
    {!devices.length&&!busy&&<p className="empty panel">No devices found.</p>}
    <div className="pagination"><span/><button className="secondary" disabled={!cursor||busy} onClick={()=>void run(()=>load(cursor!))}>Next page</button></div>
  </>;
}
