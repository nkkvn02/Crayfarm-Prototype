import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {setImmediate} from 'node:timers/promises';
import {deleteApp, initializeApp} from 'firebase/app';
import {connectDatabaseEmulator, getDatabase, ref, update} from 'firebase/database';

// Deliberately fixed to a local, dedicated demo namespace. No credentials required.
const host = '127.0.0.1';
const port = 19048;
const namespace = 'demo-crayfarm-pond-rules';
const rules = await readFile(new URL('../../firebase/database.rules.pond-deletion.candidate.json', import.meta.url));
const requireFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const {applyAssignment, updateDeviceAssignment} = requireFunctions('./lib/deviceAssignment.js');
const {initializeApp: initializeAdminApp, deleteApp: deleteAdminApp} = requireFunctions('firebase-admin/app');
const {getDatabase: getAdminDatabase} = requireFunctions('firebase-admin/database');
const id = 'CRAY-01';
process.env.FIREBASE_DATABASE_EMULATOR_HOST = `${host}:${port}`;
const adminApp = initializeAdminApp({projectId: namespace, databaseURL: `https://${namespace}.firebaseio.com`});
const adminDb = getAdminDatabase(adminApp);

async function admin(method, path, body) {
  const response = await fetch(`http://${host}:${port}/${path}.json?ns=${namespace}`, {
    method, headers: {Authorization: 'Bearer owner', 'Content-Type': 'application/json'}, body,
    signal: AbortSignal.timeout(10000),
  });
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${await response.text()}`);
}

function seed() {
  return {
    users: {ownerA:{role:'admin',farmId:'f1'}, ownerB:{role:'admin',farmId:'f2'}, worker:{role:'worker',farmId:'f1'}},
    deviceUsers: {esp:{deviceId:id}},
    devices: {[id]: {deviceId:id,authUid:'esp',registeredAt:1,active:true,status:'assigned',
      farmId:'f1',pondId:'source',linkedAt:2,linkedBy:'ownerA'}},
    farms: {
      f1:{ownerUid:'ownerA',ponds:{source:{pondName:'Source',deviceId:id,active:true},
        target:{pondName:'Target',active:true}}},
      f2:{ownerUid:'ownerB',ponds:{target:{pondName:'Target',active:true}}},
    },
  };
}

function move(data, farmId) {
  return applyAssignment(data, {deviceId:id, operationId:'move', from:{farmId:'f1',pondId:'source'},
    to:{farmId,pondId:'target'}}, 'web-admin', 3);
}

function release() {
  return {
    [`devices/${id}/farmId`]: '', [`devices/${id}/pondId`]: '', [`devices/${id}/status`]: 'available',
    [`devices/${id}/linkedAt`]: null, [`devices/${id}/linkedBy`]: null,
  };
}

const apps = new Map();
function asUser(uid) {
  if (!apps.has(uid)) {
    const app = initializeApp({projectId: namespace, databaseURL: `https://${namespace}.firebaseio.com`}, uid);
    const db = getDatabase(app);
    connectDatabaseEmulator(db, host, port, {mockUserToken: {sub: uid}});
    apps.set(uid, {app, db});
  }
  return ref(apps.get(uid).db);
}

const denied = /permission_denied|permission denied/i;
async function scenario(label, data, verify) {
  await admin('PUT', '', JSON.stringify(data));
  await verify();
  console.log(`PASS: ${label}`);
}

try {
  await admin('PUT', '.settings/rules', rules);
  await scenario('an unrelated account cannot delete a pond', seed(), () =>
    assert.rejects(update(asUser('outsider'), {'farms/f1/ponds/target':null}), denied));
  await scenario('valid linked deletion releases the device', seed(), () =>
    update(asUser('ownerA'), {'farms/f1/ponds/source':null, ...release()}));
  await scenario('valid unlinked deletion succeeds', seed(), () =>
    update(asUser('ownerA'), {'farms/f1/ponds/target':null}));
  await scenario('release without deleting a currently linked pond is denied', seed(), () =>
    assert.rejects(update(asUser('ownerA'), release()), denied));
  const missingPond = seed();
  delete missingPond.farms.f1.ponds.source;
  await scenario('an incomplete claim with no pond can still be rolled back', missingPond, () =>
    update(asUser('ownerA'), release()));
  await scenario('same-farm stale source and destination deletes are denied after the move', move(seed(),'f1'), async () => {
    await assert.rejects(update(asUser('ownerA'), {'farms/f1/ponds/source':null, ...release()}), denied);
    await assert.rejects(update(asUser('ownerA'), {'farms/f1/ponds/target':null}), denied);
    await assert.rejects(update(asUser('ownerA'), {'farms/f1/ponds':null}), denied);
    await update(asUser('ownerA'), {'farms/f1/ponds/target':null, ...release()});
  });
  await scenario('cross-farm stale deletes are denied after the move', move(seed(),'f2'), async () => {
    await assert.rejects(update(asUser('ownerA'), {'farms/f1/ponds/source':null, ...release()}), denied);
    await assert.rejects(update(asUser('ownerB'), {'farms/f2/ponds/target':null}), denied);
  });
  const moveRequest = {deviceId:id, operationId:'transaction', from:{farmId:'f1',pondId:'source'},
    to:{farmId:'f1',pondId:'target'}};
  await scenario('the admin transaction refuses a destination deleted first', seed(), async () => {
    await update(asUser('ownerA'), {'farms/f1/ponds/target':null});
    await assert.rejects(updateDeviceAssignment(adminDb, moveRequest, 'web-admin'), /failed-precondition/);
  });
  await scenario('a racing admin move and stale Android delete cannot both succeed', seed(), async () => {
    const [moveResult, deleteResult] = await Promise.allSettled([
      updateDeviceAssignment(adminDb, moveRequest, 'web-admin'),
      update(asUser('ownerA'), {'farms/f1/ponds/target':null}),
    ]);
    assert.notEqual(moveResult.status, deleteResult.status);
    if (moveResult.status === 'rejected') assert.match(String(moveResult.reason), /failed-precondition/);
    if (deleteResult.status === 'rejected') assert.match(String(deleteResult.reason), denied);
  });
  await scenario('pond creation after a device claim and existing farm updates still work', missingPond, async () => {
    await update(asUser('ownerA'), {'farms/f1/ponds/source':{pondName:'Source',deviceId:id,active:true}});
    await update(asUser('ownerA'), {'farms/f1/farmName':'Farm A','farms/f1/members/worker':{role:'worker'}});
    await update(asUser('ownerA'), {'farms/f1/ponds/source/pondName':'Renamed'});
    await update(asUser('worker'), {'farms/f1/activityLogs/new':{type:'test'}});
    await update(asUser('esp'), {'farms/f1/ponds/source/latest':{temperature:24}});
    await assert.rejects(update(asUser('esp'), {'farms/f1/ponds/target/latest':{temperature:24}}), denied);
  });
  await scenario('a new farm can still be created through a multi-path write', {}, () =>
    update(asUser('ownerA'), {'farms/new/ownerUid':'ownerA','farms/new/farmName':'New farm'}));
} finally {
  await Promise.all([...apps.values()].map(({app}) => deleteApp(app)));
  await deleteAdminApp(adminApp);
  await setImmediate();
}
