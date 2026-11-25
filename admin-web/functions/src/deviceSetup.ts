import {createCipheriv, createDecipheriv, createHash, createHmac, randomBytes} from 'node:crypto';
import type {Database} from 'firebase-admin/database';
import {key} from './core';

export type DeviceSetup = {deviceId:string; email:string; password:string};
type DeviceIdentity = {authUid:string; provisioningOperationId:string};

export function deviceSetupKey(env = process.env) {
  const configured = env.CRAYFARM_DEVICE_SETUP_KEY?.trim();
  if (configured) {
    if (!/^[a-f0-9]{64}$/i.test(configured)) throw new Error('CRAYFARM_DEVICE_SETUP_KEY must be a 64-character hex key.');
    return Buffer.from(configured, 'hex');
  }
  if (!env.FIREBASE_SERVICE_ACCOUNT_BASE64) return;
  const {private_key:privateKey} = JSON.parse(Buffer.from(env.FIREBASE_SERVICE_ACCOUNT_BASE64, 'base64').toString('utf8'));
  if (typeof privateKey === 'string' && privateKey) {
    return createHmac('sha256', privateKey).update('crayfarm/device-setup/encryption/v1').digest();
  }
}

const keyId = (secret: Buffer) => createHash('sha256').update(secret).digest('hex').slice(0, 16);
const associatedData = (deviceId:string, identity:DeviceIdentity) =>
  Buffer.from(JSON.stringify([deviceId, identity.authUid, identity.provisioningOperationId]));

export function sealDeviceSetup(setup: DeviceSetup, identity: DeviceIdentity, secret: Buffer) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', secret, iv);
  cipher.setAAD(associatedData(setup.deviceId, identity));
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(setup), 'utf8'), cipher.final()]);
  return {version:1, ...identity, keyId:keyId(secret), iv:iv.toString('base64'),
    ciphertext:ciphertext.toString('base64'), tag:cipher.getAuthTag().toString('base64')};
}

export function hasDeviceSetup(
  device: (Partial<DeviceIdentity> & {status?:string})|null|undefined,
  encrypted: Partial<ReturnType<typeof sealDeviceSetup>>|null|undefined,
  secret?: Buffer,
) {
  return Boolean(secret && device?.status !== 'provisioning' && typeof device?.authUid === 'string'
    && typeof device?.provisioningOperationId === 'string' && encrypted?.version === 1
    && encrypted.keyId === keyId(secret) && encrypted.authUid === device.authUid
    && encrypted.provisioningOperationId === device.provisioningOperationId
    && typeof encrypted.iv === 'string' && typeof encrypted.tag === 'string' && typeof encrypted.ciphertext === 'string');
}

export async function readDeviceSetup(db:Database, value:unknown, secret?:Buffer): Promise<DeviceSetup> {
  const deviceId = key(value);
  if (!secret) throw new Error('failed-precondition');
  const [device, setup] = await Promise.all([
    db.ref(`devices/${deviceId}`).get(),
    db.ref(`_crayfarmAdmin/deviceSetups/${deviceId}`).get(),
  ]);
  const identity = device.val();
  const encrypted = setup.val();
  if (!hasDeviceSetup(identity, encrypted, secret)) throw new Error('not-found');
  try {
    const decipher = createDecipheriv('aes-256-gcm', secret, Buffer.from(encrypted.iv, 'base64'));
    decipher.setAAD(associatedData(deviceId, identity));
    decipher.setAuthTag(Buffer.from(encrypted.tag, 'base64'));
    const plaintext = Buffer.concat([decipher.update(Buffer.from(encrypted.ciphertext, 'base64')), decipher.final()]);
    const result = JSON.parse(plaintext.toString('utf8'));
    if (result.deviceId !== deviceId || typeof result.email !== 'string' || typeof result.password !== 'string') {
      throw new Error('invalid-backup');
    }
    return {deviceId, email:result.email, password:result.password};
  } catch {
    throw new Error('failed-precondition');
  }
}
