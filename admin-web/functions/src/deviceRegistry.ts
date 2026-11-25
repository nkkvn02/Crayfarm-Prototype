import type {Database} from 'firebase-admin/database';
import {key, type publicUser} from './core';

export async function readDeviceRegistryPage(db: Database, pageSize: number, cursor?: string) {
  const registry = db.ref('devices').orderByKey();
  const query = cursor ? registry.startAfter(key(cursor)) : registry;
  const snapshot = await query.limitToFirst(pageSize + 1).get();
  const entries: Array<{id: string; value: any}> = [];
  snapshot.forEach(child => {
    entries.push({id: child.key!, value: child.val()});
  });
  const page = entries.slice(0, pageSize);
  return {entries: page, cursor: entries.length > pageSize ? page.at(-1)!.id : null};
}

export async function filterHumanAccounts(db: Database, users: Array<ReturnType<typeof publicUser>>) {
  const candidates = users.filter(user => !user.websiteAdmin || user.profileExists);
  if (!candidates.length) return [];

  const candidateIds = new Set(candidates.map(user => user.uid));
  const deviceIds = new Set<string>();
  let cursor: string | null = null;
  do {
    const page = await readDeviceRegistryPage(db, 100, cursor ?? undefined);
    for (const {value} of page.entries) {
      if (candidateIds.has(value?.authUid)) deviceIds.add(value.authUid);
    }
    cursor = page.cursor;
  } while (cursor && deviceIds.size < candidateIds.size);

  const unmatched = candidates.filter(user => !deviceIds.has(user.uid));
  const legacyMappings = await Promise.all(unmatched.map(user => db.ref(`deviceUsers/${key(user.uid)}/deviceId`).get()));
  return unmatched.filter((_, index) => !legacyMappings[index].exists());
}
