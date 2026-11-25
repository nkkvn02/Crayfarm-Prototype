import {FormEvent, useEffect, useRef, useState} from 'react';
import {AlertTriangle, Cpu, Plus, QrCode, RefreshCw, Search, ShieldCheck, ToggleLeft, ToggleRight, X} from 'lucide-react';
import {QRCodeSVG} from 'qrcode.react';
import DeviceAssignment from './DeviceAssignment';

type Device = {
  deviceId:string;active:boolean|null;status:string|null;farmId:string|null;pondId:string|null;
  linkedAt:number|null;registeredAt:number|null;authConfigured:boolean;farmName:string|null;pondName:string|null;
};
type Props = {active:boolean;api:(name:string,data?:unknown)=>Promise<any>;onOperation:(message:string, registered?:boolean)=>Promise<void>};
type DeviceInfo = {displayName:string;maintenanceNotes:string};
type DeviceFilters = {search:string;status:string};
const defaultDeviceFilters: DeviceFilters = {search:'',status:''};

function DeviceDetails({device, api, onClose}: {device:Device;api:Props['api'];onClose:()=>void}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [info, setInfo] = useState<DeviceInfo | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const deviceId = device.deviceId;
  const canScan = /^[A-Z0-9_-]{6,32}$/.test(deviceId);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  useEffect(() => {
    let active = true;
    void api('deviceInfo', {deviceId})
      .then(result => { if (active) setInfo(result); })
      .catch(() => { if (active) setError('Device information could not be loaded.'); });
    return () => { active = false; };
  }, [api, deviceId]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!info || saving) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      setInfo(await api('updateDeviceInfo', {deviceId, ...info}));
      setNotice('Device info saved.');
    } catch {
      setError('Device information could not be saved. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <dialog className="device-details panel" ref={dialog} aria-labelledby="device-details-title"
      onClose={onClose} onCancel={event => { if (saving) event.preventDefault(); }}>
      <div className="device-details-heading">
        <div><h2 id="device-details-title">Device info</h2><small>{deviceId}</small></div>
        <button type="button" className="icon-button" aria-label="Close device info" disabled={saving}
          onClick={() => dialog.current?.close()}><X size={18}/></button>
      </div>
      <div className="device-details-body">
        <div className="device-qr">
          <h3>Device QR code</h3>
          {canScan
            ? <QRCodeSVG value={`CRAYFARM_DEVICE:${deviceId}`} size={200} marginSize={4}
                level="M" title={`CrayFarm QR code for ${deviceId}`}/>
            : <p>This device ID is incompatible with the CrayFarm scanner.</p>}
          {canScan && <small>Scan in CrayFarm when adding a pond.</small>}
        </div>
        <div>
          <dl className="device-info-data">
            <dt>Status</dt><dd>{device.status || 'Unknown'}</dd>
            <dt>Farm</dt><dd>{device.farmName || device.farmId || 'Not assigned'}</dd>
            <dt>Pond</dt><dd>{device.pondName || device.pondId || 'Not assigned'}</dd>
            <dt>Linked</dt><dd>{device.linkedAt ? new Date(device.linkedAt).toLocaleString() : '—'}</dd>
          </dl>
          {info && (
            <form className="device-info-form" onSubmit={save}>
              <label>Display name
                <input maxLength={80} value={info.displayName} disabled={saving}
                  onChange={event => { setNotice(''); setInfo({...info, displayName:event.target.value}); }}/>
              </label>
              <label>Maintenance notes
                <textarea rows={3} maxLength={1000} value={info.maintenanceNotes} disabled={saving}
                  onChange={event => { setNotice(''); setInfo({...info, maintenanceNotes:event.target.value}); }}/>
              </label>
              <button disabled={saving}>{saving ? 'Saving…' : 'Save device info'}</button>
            </form>
          )}
          {!info && !error && <p role="status">Loading device info…</p>}
          {error && <p role="alert" className="error">{error}</p>}
          {notice && <p role="status" className="banner">{notice}</p>}
        </div>
      </div>
    </dialog>
  );
}

export default function DeviceRegistry({active,api,onOperation}:Props) {
  const [page,setPage]=useState<{devices:Device[];filters:DeviceFilters;cursor:string|null;next:string|null}|null>(null);
  const initialLoad = useRef(false);
  const devices = page?.devices ?? [];
  const [search,setSearch]=useState(''),[status,setStatus]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const [showRegister,setShowRegister]=useState(false),[deviceId,setDeviceId]=useState(''),[authUid,setAuthUid]=useState('');
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [assignmentDevice, setAssignmentDevice] = useState<Device | null>(null);
  const pending=sessionStorage.getItem('crayfarm-device-pending');
  const appliedSearch = page?.filters.search.trim();
  const appliedStatus = page?.filters.status;
  async function run(task:()=>Promise<void>){setBusy(true);setError('');try{await task();}catch(reason:any){setError(reason.message||'Request failed.');}finally{setBusy(false);}}
  async function load(filters:DeviceFilters, cursor:string|null = null) {
    const result = await api('deviceDirectory', {cursor, search:filters.search.trim(), status:filters.status});
    setPage({devices:result.devices, filters, cursor, next:result.cursor});
  }
  useEffect(() => {
    if (!active || page || initialLoad.current) return;
    initialLoad.current = true;
    void run(async () => {
      try { await load(defaultDeviceFilters); }
      finally { initialLoad.current = false; }
    });
  }, [active, page]);
  const refresh = () => load(page?.filters ?? defaultDeviceFilters, page?.cursor);
  async function showAllDevices() {
    await load(defaultDeviceFilters);
    setSearch('');
    setStatus('');
  }
  async function perform(request:any) {
    sessionStorage.setItem('crayfarm-device-pending', JSON.stringify(request));
    const result = await api(request.actorUid ? 'reconcileDeviceOperation' : 'mutateDevice',
      request.actorUid ? {operationId:request.operationId} : request);
    const succeeded = result.status === 'succeeded';
    if (succeeded) sessionStorage.removeItem('crayfarm-device-pending');
    await Promise.all([
      onOperation(`Device ${result.deviceId}: ${result.status}`, succeeded && result.action === 'register'),
      refresh(),
    ]);
  }
  async function register(event:FormEvent){event.preventDefault();const request={deviceId:deviceId.trim().toUpperCase(),authUid:authUid.trim(),action:'register',operationId:`${Date.now()}_${crypto.randomUUID()}`};await perform(request);setDeviceId('');setAuthUid('');setShowRegister(false);}
  async function toggle(device:Device,action:'activate'|'deactivate'){if(!window.confirm(`${action==='activate'?'Activate':'Deactivate'} ${device.deviceId}?`))return;await perform({deviceId:device.deviceId,action,operationId:`${Date.now()}_${crypto.randomUUID()}`});}
  return <>
    {assignmentDevice && <DeviceAssignment
      deviceId={assignmentDevice.deviceId}
      from={assignmentDevice.farmId && assignmentDevice.pondId
        ? {farmId: assignmentDevice.farmId, pondId: assignmentDevice.pondId} : null}
      currentLabel={assignmentDevice.farmId && assignmentDevice.pondId
        ? `${assignmentDevice.farmName || assignmentDevice.farmId} / ${assignmentDevice.pondName || assignmentDevice.pondId}`
        : 'Unassigned'}
      api={api} onClose={() => setAssignmentDevice(null)}
      onSaved={() => void run(async () => {
        await refresh();
        await onOperation(`Assignment updated for ${assignmentDevice.deviceId}.`);
      })}/>}
    {selectedDevice && <DeviceDetails key={selectedDevice.deviceId} device={selectedDevice}
      api={api} onClose={() => setSelectedDevice(null)}/>}
    {error&&<p role="alert" className="error"><AlertTriangle size={18}/>{error}</p>}
    {pending&&<div className="banner device-recovery"><span><AlertTriangle size={18}/>An earlier device request may need reconciliation.</span><button className="secondary compact" disabled={busy} onClick={()=>void run(()=>perform(JSON.parse(pending)))}><RefreshCw size={15}/>Recover</button></div>}
    <div className="device-toolbar"><form onSubmit={event=>{event.preventDefault();void run(()=>load({search,status}));}}><div className="search-field"><Search size={17}/><input aria-label="Search Device ID" placeholder="Search device ID" value={search} onChange={event=>setSearch(event.target.value)}/></div><select aria-label="Device status" value={status} onChange={event=>setStatus(event.target.value)}><option value="">All states</option><option value="available">Available</option><option value="assigned">Assigned</option><option value="inactive">Inactive</option></select><button className="secondary" disabled={busy}><Search size={16}/>Search</button></form><button disabled={busy} onClick={()=>setShowRegister(value=>!value)}>{showRegister?<><X size={17}/>Cancel</>:<><Plus size={17}/>Register device</>}</button></div>
    {showRegister&&<form className="panel register-device" onSubmit={event=>void run(()=>register(event))}><div className="section-title"><span className="device-icon"><ShieldCheck size={19}/></span><div><h2>Register device</h2><small>Add a dedicated ESP32 authentication account.</small></div></div><div><label>Device ID<input required maxLength={128} placeholder="CRAY-…" value={deviceId} onChange={event=>setDeviceId(event.target.value.toUpperCase())}/></label><label>Firebase Auth UID<input required maxLength={128} placeholder="Dedicated ESP32 account UID" value={authUid} onChange={event=>setAuthUid(event.target.value)}/></label><button disabled={busy}>{busy?'Registering…':'Register device'}<Plus size={17}/></button></div></form>}
    {(appliedSearch || appliedStatus) && (
      <div className="device-filter-summary">
        <p>
          {appliedSearch && <>Device ID <b>{appliedSearch}</b></>}
          {appliedSearch && appliedStatus && <span aria-hidden="true"> · </span>}
          {appliedStatus && <>Status <b className="device-filter-status">{appliedStatus}</b></>}
        </p>
        <button type="button" className="secondary compact" disabled={busy} onClick={() => void run(showAllDevices)}>
          Show all devices
        </button>
      </div>
    )}
    <div className="device-grid">
      {devices.map(device => (
        <article className="device-card" key={device.deviceId}>
          <div className="device-card-head">
            <span className="device-icon"><Cpu size={20}/></span>
            <div><h2>{device.deviceId}</h2><small>{device.authConfigured ? 'Authentication configured' : 'Authentication missing'}</small></div>
            <span className={`status ${device.status === 'available' || device.status === 'assigned' ? 'enabled' : 'disabled'}`}><i/>{device.status || 'Unknown'}</span>
          </div>
          <dl>
            <dt>Farm</dt><dd>{device.farmName || device.farmId || 'Not assigned'}</dd>
            <dt>Pond</dt><dd>{device.pondName || device.pondId || 'Not assigned'}</dd>
            <dt>Registered</dt><dd>{device.registeredAt ? new Date(device.registeredAt).toLocaleString() : 'Unknown'}</dd>
          </dl>
          <div className="device-actions">
            <button className="secondary compact" onClick={() => setSelectedDevice(device)}
              aria-label={`Device info for ${device.deviceId}`}><QrCode size={16}/>Device info</button>
            {device.active === true && (device.status === 'available' || device.status === 'assigned') &&
              <button className="secondary compact" disabled={busy} onClick={() => setAssignmentDevice(device)}>
                {device.status === 'assigned' ? 'Change assignment' : 'Assign to pond'}
              </button>}
            {device.status !== 'assigned' && (
              <button className="secondary compact" disabled={busy}
                onClick={() => void run(() => toggle(device, device.active === false ? 'activate' : 'deactivate'))}>
                {device.active === false ? <ToggleRight size={16}/> : <ToggleLeft size={16}/>}
                {device.active === false ? 'Activate' : 'Deactivate'}
              </button>
            )}
          </div>
        </article>
      ))}
    </div>
    {!devices.length&&!busy&&<p className="empty panel">No devices found.</p>}
    <div className="pagination"><span/><button className="secondary" disabled={!page?.next||busy} onClick={()=>{if(page?.next)void run(()=>load(page.filters,page.next));}}>Next page</button></div>
  </>;
}
