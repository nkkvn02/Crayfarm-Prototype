import {createRequire} from 'node:module';
import {randomBytes} from 'node:crypto';
import {initializeCliAdmin} from './firebase-cli-admin.mjs';

const functionsRequire = createRequire(new URL('../functions/package.json', import.meta.url));
const {deleteApp} = functionsRequire('firebase-admin/app');
const {getAuth} = functionsRequire('firebase-admin/auth');
const email = process.argv[2]?.trim().toLowerCase();
if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new Error('Usage: node scripts/create-production-web-admin.mjs EMAIL');

const app = initializeCliAdmin('create-production-web-admin');
const auth = getAuth(app);
try {
  let user;
  let password = null;
  try {
    user = await auth.getUserByEmail(email);
  } catch (error) {
    if (error.code !== 'auth/user-not-found') throw error;
    password = `CrayFarm-${randomBytes(18).toString('base64url')}!`;
    user = await auth.createUser({email, password, emailVerified: false, disabled: false});
  }
  await auth.setCustomUserClaims(user.uid, {...user.customClaims, crayfarmAdmin: true});
  console.log(JSON.stringify({uid:user.uid, email, created:password !== null, password}));
} finally {
  await deleteApp(app);
}
