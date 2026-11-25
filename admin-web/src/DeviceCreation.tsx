import {useEffect, useRef, useState} from 'react';
import {Download, Plus, X} from 'lucide-react';
import {QRCodeSVG} from 'qrcode.react';
import {downloadDeviceSetup, type DeviceSetup} from './deviceSetupFile';

type Setup = DeviceSetup & {setupAvailable:boolean};
type Request = {operationId:string; proof:string};
type Props = {
  open:boolean;
  actorUid:string;
  apiKey:string;
  api:(name:string, data:Request)=>Promise<Setup>;
  onClose:()=>void;
  onCreated:(deviceId:string)=>void;
};

export default function DeviceCreation({open, actorUid, apiKey, api, onClose, onCreated}:Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const pendingKey = `crayfarm-device-creation:${actorUid}`;
  const [request, setRequest] = useState<Request|null>(() => {
    try {
      const stored = sessionStorage.getItem(pendingKey);
      const pending = stored ? JSON.parse(stored) : null;
      return typeof pending?.operationId === 'string' && typeof pending?.proof === 'string' ? pending : null;
    } catch {
      return null;
    }
  });
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [setup, setSetup] = useState<Setup|null>(null);

  useEffect(() => {
    const element = dialog.current;
    if (open && element && !element.open) element.showModal();
  }, [open]);

  async function create() {
    if (creating) return;
    setCreating(true);
    setError('');
    try {
      const pending = request ?? {
        operationId:crypto.randomUUID(),
        proof:Array.from(crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join(''),
      };
      if (!request) {
        sessionStorage.setItem(pendingKey, JSON.stringify(pending));
        setRequest(pending);
      }
      const result = await api('provisionDevice', pending);
      sessionStorage.removeItem(pendingKey);
      setRequest(null);
      setSetup(result);
      onCreated(result.deviceId);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Device creation could not finish. Try again.');
    } finally {
      setCreating(false);
    }
  }

  function close() {
    setSetup(null);
    onClose();
  }

  return (
    <dialog className="panel device-details" ref={dialog} aria-labelledby="device-creation-title"
      onClose={close} onCancel={event => { if (creating) event.preventDefault(); }}>
      <div className="device-details-heading">
        <div>
          <h2 id="device-creation-title">{setup ? 'Device created' : 'Create device'}</h2>
          {setup && <small>{setup.deviceId}</small>}
        </div>
        <button type="button" className="icon-button" aria-label="Close device setup" disabled={creating}
          onClick={() => dialog.current?.close()}><X size={18} aria-hidden="true"/></button>
      </div>
      {setup ? (
        <div className="device-details-body">
          <div className="device-qr">
            <h3>Device QR code</h3>
            <QRCodeSVG value={`CRAYFARM_DEVICE:${setup.deviceId}`} size={164} marginSize={4}
              level="M" title={`CrayFarm QR code for ${setup.deviceId}`}/>
            <small>Scan in CrayFarm to pair with a pond.</small>
          </div>
          <div className="device-creation-download">
            <p>{setup.setupAvailable ? 'You can download the private setup file again from Device info.' : 'Download the private setup file before closing.'} Place it in the ESP32 sketch folder and flash the board.</p>
            <button type="button" onClick={() => downloadDeviceSetup(setup, apiKey)}><Download size={17} aria-hidden="true"/>Download setup file</button>
          </div>
        </div>
      ) : (
        <>
          <p>CrayFarm will generate the Device ID and register a dedicated ESP32 sign-in.</p>
          {error && <p role="alert" className="error">{error}</p>}
          <button type="button" disabled={creating} onClick={() => void create()}>
            <Plus size={17} aria-hidden="true"/>{creating ? 'Creating…' : request ? 'Resume setup' : 'Create device'}
          </button>
        </>
      )}
    </dialog>
  );
}
