import type {Database} from 'firebase-admin/database';
import {key} from './core';

type Location = {farmId: string; pondId: string};
type AssignmentRequest = {deviceId: string; operationId: string; from: Location | null; to: Location | null};
type RecordMap = Record<string, any>;

function assignmentKey(value: unknown) {
  const id = key(value);
  if (Object.hasOwn(Object.prototype, id)) throw new Error('invalid-argument');
  return id;
}

function location(value: unknown): Location | null {
  if (value === null) return null;
  if (!value || typeof value !== 'object') throw new Error('invalid-argument');
  const fields = value as Record<string, unknown>;
  return {farmId: assignmentKey(fields.farmId), pondId: assignmentKey(fields.pondId)};
}

function sameLocation(a: Location | null, b: Location | null) {
  return a?.farmId === b?.farmId && a?.pondId === b?.pondId;
}

export async function listAssignmentPonds(db: Database, farm: unknown, cursor: unknown) {
  let query = db.ref(`farms/${assignmentKey(farm)}/ponds`).orderByKey();
  if (cursor !== undefined && cursor !== null) query = query.startAfter(key(cursor));
  const snapshot = await query.limitToFirst(26).get();
  const ponds: Array<{pondId: string; pondName: string; available: boolean}> = [];
  snapshot.forEach(pond => {
    const name = pond.child('pondName').val();
    const owner = pond.child('deviceId').val();
    ponds.push({pondId: pond.key!, pondName: typeof name === 'string' ? name : pond.key!,
      available: pond.child('active').val() === true && (owner === null || owner === '')});
  });
  return {ponds: ponds.slice(0, 25), cursor: ponds.length > 25 ? ponds[24].pondId : null};
}

export function applyAssignment(root: RecordMap, request: AssignmentRequest, actor: string, now: number) {
  const {deviceId, operationId, from, to} = request;
  const control = root._crayfarmAdmin ?? {};
  const previous = control.deviceOperations?.[operationId];
  if (previous) {
    if (previous.actorUid !== actor || previous.deviceId !== deviceId ||
        !Object.hasOwn(previous, 'assignment') ||
        !sameLocation(previous.assignment.from ?? null, from) ||
        !sameLocation(previous.assignment.to ?? null, to)) throw new Error('conflict');
    return root;
  }

  const device = root.devices?.[deviceId];
  if (!device) throw new Error('not-found');
  if (control.deviceLocks?.[deviceId] || device.active !== true || device.deviceId !== deviceId ||
      device.status !== (from ? 'assigned' : 'available') ||
      (device.farmId ?? '') !== (from?.farmId ?? '') ||
      (device.pondId ?? '') !== (from?.pondId ?? '')) throw new Error('failed-precondition');

  const source = from && root.farms?.[from.farmId]?.ponds?.[from.pondId];
  if (from && source?.deviceId !== deviceId) throw new Error('failed-precondition');
  const destination = to && root.farms?.[to.farmId]?.ponds?.[to.pondId];
  if (to && (!destination || destination.active !== true ||
      (destination.deviceId != null && destination.deviceId !== ''))) throw new Error('failed-precondition');
  if (to) {
    for (const other of Object.values(root.devices ?? {}) as RecordMap[]) {
      if (other?.farmId === to.farmId && other?.pondId === to.pondId) throw new Error('failed-precondition');
    }
  }

  if (source) source.deviceId = '';
  if (destination) destination.deviceId = deviceId;
  device.farmId = to?.farmId ?? '';
  device.pondId = to?.pondId ?? '';
  device.status = to ? 'assigned' : 'available';
  if (to) {
    device.linkedAt = now;
    device.linkedBy = actor;
  } else {
    delete device.linkedAt;
    delete device.linkedBy;
  }

  const action = !from ? 'assign' : to ? 'move' : 'unassign';
  const assignment = {...(from && {from}), ...(to && {to})};
  const operation = {operationId, actorUid: actor, deviceId, action, assignment,
    createdAt: now, updatedAt: now, status: 'succeeded'};
  root._crayfarmAdmin = control;
  (control.deviceOperations ??= {})[operationId] = operation;
  (control.deviceAudit ??= {})[operationId] = {...operation, outcome: 'succeeded', timestamp: now};
  return root;
}

export async function updateDeviceAssignment(db: Database, data: Record<string, unknown>, actor: string) {
  const request = {deviceId: assignmentKey(data.deviceId), operationId: assignmentKey(data.operationId),
    from: location(data.from), to: location(data.to)};
  if (sameLocation(request.from, request.to)) throw new Error('invalid-argument');
  const now = Date.now();
  let failure: unknown;
  // The registry and pond records have different parents. A transaction at
  // their common ancestor checks both sides and commits them together.
  const result = await db.ref().transaction(current => {
    failure = undefined;
    if (current === null) return null; // The SDK can initially have an empty cache.
    if (Buffer.byteLength(JSON.stringify(current)) > 8 * 1024 * 1024) {
      failure = new Error('assignment-database-too-large');
      return undefined;
    }
    try {
      return applyAssignment(current, request, actor, now);
    } catch (error) {
      failure = error;
      return undefined;
    }
  }, undefined, false);
  if (failure) throw failure;
  if (!result.committed || !result.snapshot.hasChild(`_crayfarmAdmin/deviceOperations/${request.operationId}`)) {
    throw new Error('not-found');
  }
  return {deviceId: request.deviceId, status: 'succeeded'};
}
