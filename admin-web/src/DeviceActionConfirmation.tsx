import {useEffect, useRef, useState} from 'react';
import {ToggleLeft, Trash2} from 'lucide-react';

export type ConfirmedDeviceAction = 'deactivate' | 'delete';

type Props = {
  action: ConfirmedDeviceAction;
  deviceId: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
};

export default function DeviceActionConfirmation({action, deviceId, onConfirm, onClose}: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const deleting = action === 'delete';
  const title = deleting ? 'Delete device?' : 'Deactivate device?';
  const Icon = deleting ? Trash2 : ToggleLeft;

  useEffect(() => {
    const element = dialog.current;
    if (!element || element.open) return;
    element.showModal();
    cancel.current?.focus();
  }, []);

  async function confirm() {
    if (submitting) return;
    setSubmitting(true);
    await onConfirm();
    dialog.current?.close();
  }

  return (
    <dialog className={`panel device-confirmation ${action}`} ref={dialog}
      aria-labelledby="device-confirmation-title" aria-describedby="device-confirmation-id device-confirmation-description"
      onClose={onClose} onCancel={event => { if (submitting) event.preventDefault(); }}>
      <div className="device-confirmation-body">
        <span className="device-confirmation-icon"><Icon size={22} aria-hidden="true"/></span>
        <h2 id="device-confirmation-title">{title}</h2>
        <code id="device-confirmation-id">{deviceId}</code>
        <p id="device-confirmation-description">
          {deleting
            ? 'This permanently removes the device from the registry and revokes its device access. Pond history is kept.'
            : 'This makes the device inactive. You can reactivate it later to assign it to a pond.'}
        </p>
      </div>
      <div className="device-confirmation-actions">
        <button ref={cancel} type="button" className="secondary" disabled={submitting}
          onClick={() => dialog.current?.close()}>Cancel</button>
        <button type="button" className="device-confirmation-submit" disabled={submitting} onClick={() => void confirm()}>
          {deleting ? (submitting ? 'Deleting…' : 'Delete device') : (submitting ? 'Deactivating…' : 'Deactivate device')}
        </button>
      </div>
    </dialog>
  );
}
