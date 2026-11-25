import {createHash, createHmac, randomBytes, randomUUID} from 'node:crypto';
import type {Auth} from 'firebase-admin/auth';
import type {Database} from 'firebase-admin/database';
import {nextDeviceRecord} from './core';

type Provisioning = {
  actor: string;
  proofHash: string;
  deviceId: string;
  authUid: string;
  email: string;
  createdAt: number;
  completedAt?: number;
};

export class DeviceProvisioningError extends Error {
  constructor(message: string, readonly status: 400|409|503) {
    super(message);
  }
}

export async function createDevice(db: Database, auth: Auth, input: {operationId?: unknown; proof?: unknown}, actor: string) {
  const {operationId, proof} = input;
  if (typeof operationId !== 'string' || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(operationId)
    || typeof proof !== 'string' || !/^[a-f0-9]{64}$/.test(proof)) {
    throw new DeviceProvisioningError('Invalid device creation request.', 400);
  }

  const control = db.ref('_crayfarmAdmin');
  const operation = control.child(`deviceProvisioning/${operationId}`);
  const lease = control.child(`deviceProvisioningLeases/${operationId}`);
  const token = randomUUID();
  const hex = randomBytes(8).toString('hex');
  const proposed: Provisioning = {
    actor, proofHash:createHash('sha256').update(proof).digest('hex'),
    deviceId:'CRAY-' + hex.toUpperCase().match(/.{4}/g)!.join('-'),
    authUid:`esp32_${operationId}`,
    email:`esp-${hex}@crayfarm.local`,
    createdAt:Date.now(),
  };
  let leased = false;
  try {
    const intent = await operation.transaction(current => current ?? proposed);
    const record = intent.snapshot.val() as Provisioning;
    if (record.actor !== actor || record.proofHash !== proposed.proofHash) {
      throw new DeviceProvisioningError('This device creation request belongs to a different setup.', 409);
    }
    const password = createHmac('sha256', proof).update(`crayfarm-esp32:${record.deviceId}`).digest('hex');
    const setup = {deviceId:record.deviceId, email:record.email, password};
    if (record.completedAt) return setup;

    const lock = await lease.transaction(current => current && current.expiresAt > Date.now()
      ? undefined : {token, expiresAt:Date.now() + 120000});
    if (!lock.committed) throw new DeviceProvisioningError('Device creation is still running. Retry in a moment.', 409);
    leased = true;
    if ((await operation.child('completedAt').get()).exists()) return setup;

    const {deviceId, authUid, email, createdAt} = record;
    const device = db.ref(`devices/${deviceId}`);
    const reservation = await device.transaction(current => current === null ? {
      ...nextDeviceRecord(null, 'register', {deviceId, authUid, actor, operationId, now:createdAt}),
      provisioningOperationId:operationId,
      active:false,
      status:'provisioning',
    } : current);
    const reserved = reservation.snapshot.val();
    if (reserved?.provisioningOperationId !== operationId || reserved.authUid !== authUid) {
      throw new DeviceProvisioningError('The generated device ID is already in use.', 409);
    }

    let identity;
    try {
      identity = await auth.getUser(authUid);
    } catch (error) {
      if ((error as {code?: string}).code !== 'auth/user-not-found') throw error;
      identity = await auth.createUser({uid:authUid, email, password, disabled:true});
    }
    if (identity.email !== email || identity.customClaims?.crayfarmAdmin) {
      throw new DeviceProvisioningError('The generated sign-in belongs to a different account.', 409);
    }

    const mapping = await db.ref(`deviceUsers/${authUid}/deviceId`).transaction(current =>
      current === null || current === deviceId ? deviceId : undefined);
    if (!mapping.committed) {
      throw new DeviceProvisioningError('The generated sign-in is linked to a different device.', 409);
    }
    if (identity.disabled) await auth.updateUser(authUid, {disabled:false});
    const activated = await device.transaction(current =>
      current?.provisioningOperationId === operationId && current.authUid === authUid && current.status === 'provisioning'
        ? {...current, active:true, status:'available'}
        : current);
    const finalDevice = activated.snapshot.val();
    if (finalDevice?.provisioningOperationId !== operationId || finalDevice.status === 'provisioning') {
      throw new DeviceProvisioningError('The device record changed during setup.', 409);
    }

    const completedAt = Date.now();
    const audit = {operationId, actorUid:actor, deviceId, action:'register', createdAt, updatedAt:completedAt, status:'succeeded'};
    await control.update({
      [`deviceProvisioning/${operationId}/completedAt`]:completedAt,
      [`deviceOperations/${operationId}`]:audit,
      [`deviceAudit/${operationId}`]:{...audit, outcome:'succeeded', timestamp:completedAt},
    });
    return setup;
  } catch (error) {
    if (error instanceof DeviceProvisioningError) throw error;
    throw new DeviceProvisioningError('Device creation could not finish. Retry from this window.', 503);
  } finally {
    if (leased) await lease.transaction(current => current?.token === token ? null : current).catch(() => {});
  }
}
