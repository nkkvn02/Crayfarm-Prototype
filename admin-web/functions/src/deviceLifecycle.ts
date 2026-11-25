import type {Database, Reference} from 'firebase-admin/database';
import {key, nextDeviceRecord, type DeviceAction} from './core';

type RecordChange = Parameters<typeof nextDeviceRecord>[2];
type Records = Record<string, any>;
export type DeviceOperationResult = {operationId:string; deviceId:string; action:string; status:string; kind:'device'; message?:string};

export async function resumeMatchingDeviceOperation(control: Reference, lockedId: unknown, operation: Records,
  resume: (previous: Records) => Promise<DeviceOperationResult>) {
  if (typeof lockedId !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(lockedId)) return;
  const previous = (await control.child(`deviceOperations/${lockedId}`).get()).val();
  if (!previous || previous.actorUid !== operation.actorUid || previous.deviceId !== operation.deviceId
    || previous.action !== operation.action || previous.authUid !== operation.authUid) return;

  const result = await resume(previous);
  if (result.status === 'succeeded' || result.status === 'failed') {
    await control.child(`deviceOperations/${operation.operationId}`).update({
      status:'superseded', resolvedStatus:result.status, supersededBy:lockedId,
      updatedAt:Date.now(), message:result.message ?? null,
    });
  }
  return result;
}

export async function changeDeviceRecord(db: Database, action: DeviceAction, change: RecordChange) {
  let rejection: string | undefined;
  const result = await db.ref(`devices/${change.deviceId}`).transaction(current => {
    rejection = undefined;
    // A cold cache can return null before Firebase supplies the actual record.
    if (current === null && action !== 'register') return null;
    try {
      return nextDeviceRecord(current, action, change);
    } catch (error) {
      if (!(error instanceof Error)) throw error;
      if (error.message === 'already-exists') rejection = 'That Device ID is already registered.';
      else if (error.message === 'failed-precondition') rejection = 'This device is assigned or still being set up.';
      else throw error;
      return undefined;
    }
  }, undefined, false);
  if (rejection) return rejection;
  if (!result.committed) throw new Error('The device change could not be confirmed.');
  if (!result.snapshot.exists()) return 'This device is no longer registered.';
}

type DeleteRequest = {deviceId: string; operationId: string};

export function applyDeviceDeletion(root: Records, {deviceId, operationId}: DeleteRequest, actor: string, now: number) {
  const control = root._crayfarmAdmin ?? {};
  const previous = control.deviceOperations?.[operationId];
  if (previous) {
    if (previous.action !== 'delete' || previous.deviceId !== deviceId || previous.actorUid !== actor) {
      return 'That request ID has already been used.';
    }
    return;
  }
  const device = root.devices?.[deviceId];
  if (!device) return 'This device is no longer registered.';
  if (control.deviceLocks?.[deviceId]) return 'Finish the earlier request for this device before deleting it.';
  if (device.status !== 'inactive' || device.active !== false || device.farmId || device.pondId) {
    return 'Unassign and deactivate the device before deleting it.';
  }
  for (const farm of Object.values(root.farms ?? {}) as Records[]) {
    const linked = (Object.values(farm?.ponds ?? {}) as Records[]).some(pond => pond?.deviceId === deviceId);
    if (linked) return 'A pond still references this device. Unassign it before deleting.';
  }

  if (typeof device.authUid === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(device.authUid)) {
    const uid = device.authUid;
    if (!Object.hasOwn(Object.prototype, uid)) {
      if (root.deviceUsers?.[uid]?.deviceId === deviceId) delete root.deviceUsers[uid];
      const role = root.users?.[uid]?.role;
      if (role !== 'admin' && role !== 'worker') {
        (control.retiredDeviceUsers ??= {})[uid] = {deviceId, retiredAt:now};
      }
    }
  }
  delete root.devices[deviceId];
  if (control.deviceInfo) delete control.deviceInfo[deviceId];
  root._crayfarmAdmin = control;
  const record = {deviceId, operationId, actorUid:actor, action:'delete', status:'succeeded', createdAt:now, updatedAt:now};
  (control.deviceOperations ??= {})[operationId] = record;
  (control.deviceAudit ??= {})[operationId] = {...record, timestamp:now, outcome:'succeeded'};
}

export async function deleteRegisteredDevice(db: Database, data: Record<string, unknown>, actor: string): Promise<DeviceOperationResult> {
  const request = {deviceId:key(data.deviceId), operationId:key(data.operationId)};
  if (Object.hasOwn(Object.prototype, request.deviceId) || Object.hasOwn(Object.prototype, request.operationId)) {
    throw new Error('invalid-argument');
  }
  const operationPath = `_crayfarmAdmin/deviceOperations/${request.operationId}`;
  const now = Date.now();
  let rejection: string | undefined;
  const result = await db.ref().transaction(current => {
    rejection = undefined;
    if (current === null) return null;
    // Check both the registry and pond backlinks in the same transaction, as for assignment.
    if (Buffer.byteLength(JSON.stringify(current)) > 8 * 1024 * 1024) {
      rejection = 'The database is too large to verify the device’s pond links safely.';
      return undefined;
    }
    rejection = applyDeviceDeletion(current, request, actor, now);
    return rejection ? undefined : current;
  }, undefined, false);
  const response = {...request, action:'delete', kind:'device' as const};
  if (rejection) return {...response, status:'failed', message:rejection};
  if (!result.committed) throw new Error('The device deletion could not be confirmed.');
  if (!result.snapshot.hasChild(operationPath)) {
    return {...response, status:'failed', message:'This device is no longer registered.'};
  }
  return {...response, status:'succeeded'};
}
