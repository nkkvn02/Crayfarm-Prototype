import {useEffect, useRef, useState} from 'react';
import {Building2, Check, CircleOff, Copy, RotateCcw, ShieldCheck, ShieldOff, X} from 'lucide-react';

export type Person = {
  uid: string;
  email: string|null;
  name: string|null;
  role: string|null;
  disabled: boolean|null;
  active: boolean|null;
  approved: boolean|null;
  farmId: string|null;
  profileExists: boolean;
  createdAt?: string;
  websiteAdmin: boolean;
  historical?: boolean;
  authExists?: boolean;
  previousFarmId?: string|null;
  employmentStatus?: string|null;
  removedAt?: number|null;
  resignedAt?: number|null;
};

export type AccountAction = 'enable'|'disable'|'revokeAdmin';
export const isFormerPerson = (person: Person) =>
  person.historical === true || ['removed', 'resigned'].includes(person.employmentStatus ?? '');
export const personRole = (person: Person) =>
  isFormerPerson(person) ? 'Former worker' : person.role === 'admin' ? 'Farm owner' : person.role === 'worker' ? 'Farm worker' : 'Unknown';

function formatDate(value?: string|number|null) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toLocaleString(undefined, {dateStyle:'medium', timeStyle:'short'});
}

type Props = {
  person: Person;
  ownAccount: boolean;
  busy: boolean;
  error: string;
  onClose: () => void;
  onAction: (action: AccountAction) => void;
};

export default function UserDetails({person, ownAccount, busy, error, onClose, onAction}: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [copyStatus, setCopyStatus] = useState('');
  const [actionRequested, setActionRequested] = useState(false);
  const former = isFormerPerson(person);
  const name = person.name || person.email || 'Unnamed account';
  const signInStatus = person.authExists === false ? 'Account missing'
    : person.disabled === null ? 'Unknown' : person.disabled ? 'Disabled' : 'Enabled';
  const status = former ? person.employmentStatus || 'Former' : signInStatus;
  const record = former ? 'Archived' : person.profileExists ? 'Complete' : 'Incomplete';
  const created = formatDate(person.createdAt);
  const resigned = formatDate(person.resignedAt);
  const removed = formatDate(person.removedAt);

  useEffect(() => {
    const element = dialog.current;
    if (element && !element.open) element.showModal();
  }, []);

  async function copyUid() {
    try {
      await navigator.clipboard.writeText(person.uid);
      setCopyStatus('UID copied');
    } catch {
      setCopyStatus('Could not copy UID');
    }
  }

  function requestAction(action: AccountAction) {
    setActionRequested(true);
    onAction(action);
  }

  return (
    <dialog className="panel user-details" ref={dialog} aria-labelledby="user-details-title" onClose={onClose}>
      <div className="user-details-heading">
        <span className="user-details-avatar" aria-hidden="true">{(person.name || person.email || '?').charAt(0).toUpperCase()}</span>
        <div className="user-details-identity">
          <h2 id="user-details-title">{name}</h2>
          {person.name && person.email && <p>{person.email}</p>}
          <div className="user-details-badges">
            <span className="role-badge">{personRole(person)}</span>
            <span className={`status ${former || person.disabled ? 'disabled' : status === 'Enabled' ? 'enabled' : 'neutral'}`}><i/>{status}</span>
          </div>
        </div>
        <button type="button" className="icon-button" aria-label="Close user details" onClick={() => dialog.current?.close()}>
          <X size={18} aria-hidden="true"/>
        </button>
      </div>
      <div className="user-details-body">
        <div className="user-details-uid">
          <div><span>User ID</span><code>{person.uid}</code></div>
          <button type="button" className="secondary compact" onClick={() => void copyUid()}>
            {copyStatus === 'UID copied' ? <Check size={15} aria-hidden="true"/> : <Copy size={15} aria-hidden="true"/>}
            Copy UID
          </button>
          {copyStatus && <small role="status">{copyStatus}</small>}
        </div>
        <div className="user-details-sections">
          <section>
            <h3><ShieldCheck size={17} aria-hidden="true"/>Account access</h3>
            <dl>
              <div><dt>Profile record</dt><dd>{record}</dd></div>
              {!former && <div><dt>Website access</dt><dd>{person.websiteAdmin ? 'Administrator' : 'Standard'}</dd></div>}
              {former && <div><dt>Sign-in</dt><dd>{signInStatus}</dd></div>}
              {created && <div><dt>Account created</dt><dd>{created}</dd></div>}
            </dl>
          </section>
          {person.profileExists && (
            <section>
              <h3><Building2 size={17} aria-hidden="true"/>Farm membership</h3>
              <dl>
                <div><dt>Farm ID</dt><dd>{person.farmId || 'Unassigned'}</dd></div>
                {person.previousFarmId && <div><dt>Previous farm ID</dt><dd>{person.previousFarmId}</dd></div>}
                {typeof person.active === 'boolean' && <div><dt>Profile activity</dt><dd>{person.active ? 'Active' : 'Inactive'}</dd></div>}
                {typeof person.approved === 'boolean' && <div><dt>Farm approval</dt><dd>{person.approved ? 'Approved' : 'Not approved'}</dd></div>}
                {resigned && <div><dt>Resigned on</dt><dd>{resigned}</dd></div>}
                {removed && <div><dt>Removed on</dt><dd>{removed}</dd></div>}
              </dl>
            </section>
          )}
        </div>
        {actionRequested && error && <p className="error" role="alert">{error}</p>}
      </div>
      {!former && (
        <footer className="user-details-actions">
          <button type="button" className={person.disabled ? 'secondary' : 'secondary user-danger'}
            disabled={busy || ownAccount} onClick={() => requestAction(person.disabled ? 'enable' : 'disable')}>
            {person.disabled ? <RotateCcw size={16} aria-hidden="true"/> : <CircleOff size={16} aria-hidden="true"/>}
            {person.disabled ? 'Re-enable account' : 'Disable account'}
          </button>
          {person.websiteAdmin && (
            <button type="button" className="secondary user-danger" disabled={busy || ownAccount} onClick={() => requestAction('revokeAdmin')}>
              <ShieldOff size={16} aria-hidden="true"/>Revoke admin access
            </button>
          )}
        </footer>
      )}
    </dialog>
  );
}
