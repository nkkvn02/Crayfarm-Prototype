import type {Auth} from 'firebase-admin/auth';
import type {Database} from 'firebase-admin/database';
import {publicFormerWorker, type publicUser} from './core';

export function isInactiveAccount(account: {disabled?: boolean|null; role?: string|null; employmentStatus?: string|null}) {
  return account.disabled === true || (account.role === 'worker' && account.employmentStatus === 'resigned');
}

export function matchesDirectoryFilters(user: ReturnType<typeof publicUser>, role?: string, status?: string) {
  const inactive = status === 'inactive';
  const matchesRole = !role || (role === 'unknown' ? !['admin','worker'].includes(user.role ?? '')
    : user.role === role && (role !== 'worker' || inactive || !['removed','resigned'].includes(user.employmentStatus ?? '')));
  return matchesRole && (!status || (inactive ? isInactiveAccount(user) : user.disabled === (status === 'disabled')));
}

export async function archivedResignations(db: Database, auth: Auth, search = '', searchBy = 'email') {
  const query = db.ref('users').orderByKey();
  const pageSize = 100;
  const searchValue = search.toLowerCase();
  const archived: Array<ReturnType<typeof publicFormerWorker>> = [];
  let cursor: string | null = null;
  let pageLength: number;
  // Production indexes farm membership, so page by key when looking up archived resignations.
  do {
    const snapshot = await (cursor ? query.startAfter(cursor) : query).limitToFirst(pageSize).get();
    pageLength = snapshot.numChildren();
    const matches: typeof archived = [];
    snapshot.forEach(child => {
      cursor = child.key!;
      const profile = child.val();
      if (profile?.role !== 'worker' || profile.employmentStatus !== 'resigned') return;
      const worker = publicFormerWorker(child.key!, profile);
      const field = searchBy === 'uid' ? worker.uid : worker.email;
      if (!searchValue || field?.toLowerCase() === searchValue) matches.push(worker);
    });
    if (!matches.length) continue;
    const {users} = await auth.getUsers(matches.map(({uid}) => ({uid})));
    const existing = new Set(users.map(({uid}) => uid));
    const missing = matches.filter(({uid}) => !existing.has(uid));
    const deviceIdentities = await Promise.all(missing.map(({uid}) => db.ref(`deviceUsers/${uid}/deviceId`).get()));
    archived.push(...missing.filter((_, index) => !deviceIdentities[index].exists()));
  } while (pageLength === pageSize);
  return archived;
}
