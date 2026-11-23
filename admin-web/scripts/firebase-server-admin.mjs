import {createRequire} from 'node:module';

const functionsRequire = createRequire(new URL('../functions/package.json', import.meta.url));
const {cert, initializeApp} = functionsRequire('firebase-admin/app');

const projectId = 'crayfarm-eb2ea';
const databaseURL = 'https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app';

function serviceAccountFromEnvironment() {
  const encoded = process.env.FIREBASE_SERVICE_ACCOUNT_BASE64?.trim();
  if (!encoded) return null;
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(Buffer.from(encoded, 'base64').toString('utf8'));
  } catch {
    throw new Error('FIREBASE_SERVICE_ACCOUNT_BASE64 must contain a base64-encoded Firebase service account JSON file.');
  }
  if (serviceAccount?.project_id !== projectId) {
    throw new Error(`The Firebase service account must belong to ${projectId}.`);
  }
  if (!serviceAccount?.client_email || !serviceAccount?.private_key) {
    throw new Error('The Firebase service account is missing client_email or private_key.');
  }
  return serviceAccount;
}

export async function initializeServerAdmin(name) {
  const serviceAccount = serviceAccountFromEnvironment();
  if (serviceAccount) {
    return initializeApp({credential: cert(serviceAccount), projectId, databaseURL}, name);
  }
  if (process.env.CRAYFARM_DEPLOYMENT_MODE === 'hostinger' || process.env.NODE_ENV === 'production') {
    throw new Error('Set FIREBASE_SERVICE_ACCOUNT_BASE64 in the hosting environment. Never commit the service account file.');
  }
  const {initializeCliAdmin} = await import('./firebase-cli-admin.mjs');
  return initializeCliAdmin(name);
}

