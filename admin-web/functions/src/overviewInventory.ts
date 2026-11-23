import type {App} from 'firebase-admin/app';

export async function countOverviewInventory(app: App, emulatorHost?: string) {
  const {databaseURL, credential} = app.options;
  if (!databaseURL || (!emulatorHost && !credential)) {
    throw new Error('Inventory database is not configured.');
  }
  const database = new URL(databaseURL);
  if (emulatorHost && !/^127\.0\.0\.1:\d+$/.test(emulatorHost)) {
    throw new Error('Inventory emulator must be on loopback.');
  }
  const token = emulatorHost ? 'owner' : (await credential!.getAccessToken()).access_token;
  const origin = emulatorHost ? `http://${emulatorHost}` : database.origin;

  async function count(collection: 'farms' | 'devices') {
    const url = new URL(`/${collection}.json`, origin);
    url.searchParams.set('shallow', 'true');
    if (emulatorHost) url.searchParams.set('ns', database.hostname.split('.')[0]);
    const response = await fetch(url, {
      headers: {Authorization: `Bearer ${token}`},
      redirect: 'error',
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) throw new Error(`Could not load the ${collection} count.`);
    const entries: unknown = await response.json();
    if (entries === null) return 0;
    if (typeof entries !== 'object' || Array.isArray(entries)) {
      throw new Error(`Invalid ${collection} registry response.`);
    }
    return Object.keys(entries).length;
  }

  const [farms, devices] = await Promise.all([count('farms'), count('devices')]);
  return {farms, devices};
}
