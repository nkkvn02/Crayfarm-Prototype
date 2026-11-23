import {FormEvent, useEffect, useRef, useState} from 'react';
import {X} from 'lucide-react';

type Location = {farmId: string; pondId: string};
type Farm = {farmId: string; farmName: string | null};
type Pond = {pondId: string; pondName: string; available: boolean};
type Page<T> = {items: T[]; cursor: string | null};
type Props = {
  deviceId: string;
  from: Location | null;
  currentLabel: string;
  api: (name: string, data?: unknown) => Promise<any>;
  onClose: () => void;
  onSaved: () => void;
};

export default function DeviceAssignment({deviceId, from, currentLabel, api, onClose, onSaved}: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const request = useRef<{to: Location | null; operationId: string} | null>(null);
  const [farms, setFarms] = useState<Page<Farm>>({items: [], cursor: null});
  const [ponds, setPonds] = useState<Page<Pond>>({items: [], cursor: null});
  const [farmId, setFarmId] = useState('');
  const [pondId, setPondId] = useState('');
  const [loadingFarms, setLoadingFarms] = useState(true);
  const [loadingPonds, setLoadingPonds] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function loadFarms(cursor?: string) {
    setLoadingFarms(true);
    setError('');
    try {
      const result = await api('farmDirectory', {cursor});
      setFarms(previous => {
        const items = new Map((cursor ? previous.items : []).map(farm => [farm.farmId, farm]));
        for (const farm of result.farms as Farm[]) items.set(farm.farmId, farm);
        return {items: [...items.values()], cursor: result.cursor};
      });
    } catch {
      setError('Farms could not be loaded.');
    } finally {
      setLoadingFarms(false);
    }
  }

  async function loadPonds(farm: string, cursor?: string) {
    setLoadingPonds(true);
    setError('');
    try {
      const result = await api('assignmentPonds', {farmId: farm, cursor});
      setPonds(previous => ({
        items: [...(cursor ? previous.items : []), ...result.ponds],
        cursor: result.cursor,
      }));
    } catch {
      setError('Ponds could not be loaded. Select the farm again to retry.');
    } finally {
      setLoadingPonds(false);
    }
  }

  useEffect(() => {
    dialog.current?.showModal();
    void loadFarms();
  }, []);

  function selectFarm(value: string) {
    setFarmId(value);
    setPondId('');
    setPonds({items: [], cursor: null});
    if (value) void loadPonds(value);
  }

  async function change(to: Location | null) {
    if (saving) return;
    const farm = farms.items.find(item => item.farmId === to?.farmId);
    const pond = ponds.items.find(item => item.pondId === to?.pondId);
    if (to && (!farm || !pond?.available)) return;
    const destination = to ? `${farm?.farmName || to.farmId} / ${pond?.pondName}` : 'Unassigned';
    const action = !from ? 'Assign' : to ? 'Move' : 'Unassign';
    if (!window.confirm(`${action} ${deviceId}?\n\nCurrent: ${currentLabel}\nNew: ${destination}`)) return;
    const previous = request.current;
    const operationId = previous && previous.to?.farmId === to?.farmId && previous.to?.pondId === to?.pondId
      ? previous.operationId : `${Date.now()}_${crypto.randomUUID()}`;
    request.current = {to, operationId};
    setSaving(true);
    setError('');
    try {
      await api('changeDeviceAssignment', {deviceId, operationId, from, to});
      onSaved();
      dialog.current?.close();
    } catch (error) {
      setError(error instanceof Error ? error.message : 'The assignment could not be updated.');
    } finally {
      setSaving(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (farmId && pondId) void change({farmId, pondId});
  }

  return (
    <dialog className="device-details device-assignment panel" ref={dialog} aria-labelledby="device-assignment-title"
      onClose={onClose} onCancel={event => { if (saving) event.preventDefault(); }}>
      <div className="device-details-heading">
        <div><h2 id="device-assignment-title">Farm and pond assignment</h2><small>{deviceId}</small></div>
        <button type="button" className="icon-button" aria-label="Close assignment" disabled={saving}
          onClick={() => dialog.current?.close()}><X size={18}/></button>
      </div>
      <p>Current location: <b>{currentLabel}</b></p>
      <form className="device-info-form" onSubmit={submit}>
        <label>Farm
          <select value={farmId} disabled={loadingFarms || loadingPonds || saving}
            onChange={event => selectFarm(event.target.value)} required>
            <option value="">{loadingFarms ? 'Loading farms…' : 'Select a farm'}</option>
            {farms.items.map(farm => <option key={farm.farmId} value={farm.farmId}>{farm.farmName || farm.farmId}</option>)}
          </select>
        </label>
        {farms.cursor && <button type="button" className="secondary compact" disabled={loadingFarms || saving}
          onClick={() => void loadFarms(farms.cursor!)}>Load more farms</button>}
        <label>Pond
          <select value={pondId} onChange={event => setPondId(event.target.value)}
            disabled={!farmId || loadingPonds || saving} required>
            <option value="">{loadingPonds ? 'Loading ponds…' : 'Select a pond'}</option>
            {ponds.items.map(pond => <option key={pond.pondId} value={pond.pondId} disabled={!pond.available}>
              {pond.pondName}{pond.available ? '' : ' (unavailable)'}
            </option>)}
          </select>
        </label>
        {ponds.cursor && <button type="button" className="secondary compact" disabled={loadingPonds || saving}
          onClick={() => void loadPonds(farmId, ponds.cursor!)}>Load more ponds</button>}
        {farmId && !loadingPonds && !ponds.cursor && !ponds.items.some(pond => pond.available) && !error &&
          <small>No available ponds in this farm.</small>}
        <div className="actions">
          <button disabled={!farmId || !pondId || saving || loadingPonds}>{from ? 'Move device' : 'Assign device'}</button>
          {from && <button type="button" className="secondary" disabled={saving}
            onClick={() => void change(null)}>Unassign device</button>}
        </div>
      </form>
      {error && <p role="alert" className="error">{error}</p>}
    </dialog>
  );
}
