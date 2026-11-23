import type {Database} from 'firebase-admin/database';
import {key} from './core';

async function deviceInfoRef(db: Database, value: unknown) {
  const deviceId = key(value);
  if (!(await db.ref(`devices/${deviceId}`).get()).exists()) {
    throw new Error('not-found');
  }
  return db.ref(`_crayfarmAdmin/deviceInfo/${deviceId}`);
}

export async function readDeviceInfo(db: Database, deviceId: unknown) {
  const ref = await deviceInfoRef(db, deviceId);
  const record = (await ref.get()).val();
  return {
    displayName: typeof record?.displayName === 'string' ? record.displayName : '',
    maintenanceNotes: typeof record?.maintenanceNotes === 'string' ? record.maintenanceNotes : '',
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
  const ref = await deviceInfoRef(db, deviceId);
  const info = {displayName: displayName.trim(), maintenanceNotes: maintenanceNotes.trim()};
  await ref.set({...info, updatedAt: Date.now(), updatedBy: actor});
  return info;
}
