import {createRequire} from 'node:module';

const rootRequire = createRequire(new URL('../package.json', import.meta.url));
const functionsRequire = createRequire(new URL('../functions/package.json', import.meta.url));
const cliAuth = rootRequire('firebase-tools/lib/auth');
const {initializeApp} = functionsRequire('firebase-admin/app');

export const projectId = 'crayfarm-eb2ea';
export const databaseURL = 'https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app';

export function initializeCliAdmin(name) {
  const account = cliAuth.getGlobalDefaultAccount();
  if (!account?.tokens?.refresh_token) {
    throw new Error('Firebase CLI login is unavailable. Run firebase login --reauth.');
  }
  cliAuth.setActiveAccount({}, account);
  const credential = {
    async getAccessToken() {
      const token = await cliAuth.getAccessToken(account.tokens.refresh_token, []);
      if (!token?.access_token) throw new Error('Firebase CLI did not return an access token.');
      return {access_token: token.access_token, expires_in: token.expires_in ?? 3600};
    }
  };
  return initializeApp({credential, projectId, databaseURL}, name);
}
