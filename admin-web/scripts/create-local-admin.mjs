import {randomBytes} from 'node:crypto';
import {localAdmin} from './admin.mjs';

const email = 'local-admin@crayfarm.test';
const password = `CrayFarm-${randomBytes(9).toString('base64url')}!`;
const uid = `local-admin-${randomBytes(6).toString('hex')}`;
const {auth} = localAdmin();

const user = await auth.createUser({uid, email, password, emailVerified: true});
await auth.setCustomUserClaims(user.uid, {crayfarmAdmin: true});

console.log(JSON.stringify({email, password, uid}));
process.exit(0);
