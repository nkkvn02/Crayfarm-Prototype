import type {Database} from 'firebase-admin/database';
import {key} from './core';
import {hasDeviceSetup} from './deviceSetup';

async function deviceInfoRef(db: Database, value: unknown) {
  const deviceId = key(value);
  const device = await db.ref(`devices/${deviceId}`).get();
  if (!device.exists()) {
    throw new Error('not-found');
  }
  return {deviceId, device:device.val(), ref:db.ref(`_crayfarmAdmin/deviceInfo/${deviceId}`)};
}

export async function readDeviceInfo(db: Database, value: unknown, backupKey?:Buffer) {
  const {deviceId, device, ref} = await deviceInfoRef(db, value);
  const [info, backup] = await Promise.all([
    ref.get(),
    backupKey && device.provisioningOperationId ? db.ref(`_crayfarmAdmin/deviceSetups/${deviceId}`).get() : undefined,
  ]);
  const record = info.val();
  return {
    displayName: typeof record?.displayName === 'string' ? record.displayName : '',
    maintenanceNotes: typeof record?.maintenanceNotes === 'string' ? record.maintenanceNotes : '',
    setupAvailable: hasDeviceSetup(device, backup?.val(), backupKey),
  };
}

export async function saveDeviceInfo(
  db: Database,
  deviceId: unknown,
  displayName: unknown,
  maintenanceNotes: unknown,
  actor: string,
) {
  if (typeof displayName !== 'string' || displayName.length > 80 ||
      typeof maintenanceNotes !== 'string' || maintenanceNotes.length > 1000) {
    throw new Error('invalid-argument');
  }
  const {ref} = await deviceInfoRef(db, deviceId);
  const info = {displayName: displayName.trim(), maintenanceNotes: maintenanceNotes.trim()};
  await ref.set({...info, updatedAt: Date.now(), updatedBy: actor});
  return info;
}
