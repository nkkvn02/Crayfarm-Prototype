import {createRequire} from 'node:module';
import {databaseURL, initializeCliAdmin, projectId} from './firebase-cli-admin.mjs';

const functionsRequire = createRequire(new URL('../functions/package.json', import.meta.url));
const {deleteApp} = functionsRequire('firebase-admin/app');
const {getAuth} = functionsRequire('firebase-admin/auth');
const {getDatabase} = functionsRequire('firebase-admin/database');

const app = initializeCliAdmin('production-access-check');

try {
  const [authPage, usersSnapshot] = await Promise.all([
    getAuth(app).listUsers(1),
    getDatabase(app).ref('users').limitToFirst(1).get()
  ]);
  console.log(JSON.stringify({
    projectId,
    databaseURL,
    authReadable: Array.isArray(authPage.users),
    profilesReadable: usersSnapshot.exists()
  }));
} finally {
  await deleteApp(app);
}
