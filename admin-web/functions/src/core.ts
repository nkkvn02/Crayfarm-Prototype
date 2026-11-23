export function requireAdmin(token: {uid: string; crayfarmAdmin?: unknown}, blocked: boolean) {
  if (token.crayfarmAdmin !== true || blocked) throw new Error('permission-denied');
  return token.uid;
}
export function key(value: unknown): string {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) throw new Error('invalid-argument');
  return value;
}
export type Action = 'disable' | 'enable' | 'revokeAdmin';
export function action(value: unknown): Action {
  if (value !== 'disable' && value !== 'enable' && value !== 'revokeAdmin') throw new Error('invalid-argument');
  return value;
}
export type DeviceAction = 'register' | 'activate' | 'deactivate';
export function deviceAction(value: unknown): DeviceAction {
  if (value !== 'register' && value !== 'activate' && value !== 'deactivate') throw new Error('invalid-argument');
  return value;
}
export function sameOperation(a: any, b: any) {
  return a.actorUid === b.actorUid && a.targetUid === b.targetUid && a.action === b.action;
}
export function publicUser(user: any, profile: any) {
  const str = (v: unknown) => typeof v === 'string' ? v : null;
  const bool = (v: unknown) => typeof v === 'boolean' ? v : null;
  return {uid: user.uid, email: user.email ?? null, name: str(profile?.fullName) ?? user.displayName ?? null,
    createdAt: user.metadata.creationTime, disabled: user.disabled, websiteAdmin: user.customClaims?.crayfarmAdmin === true,
    profileExists: !!profile, role: str(profile?.role), active: bool(profile?.active), approved: bool(profile?.approved), farmId: str(profile?.farmId),
    previousFarmId:str(profile?.previousFarmId),employmentStatus:str(profile?.employmentStatus)};
}

export function publicFormerWorker(uid: string, profile: any, user?: any) {
  return {
    uid,
    email: stringOrNull(profile?.email) ?? user?.email ?? null,
    name: stringOrNull(profile?.fullName) ?? user?.displayName ?? null,
    role: 'worker',
    disabled: typeof user?.disabled === 'boolean' ? user.disabled : null,
    authExists: !!user,
    historical: true,
    profileExists: true,
    websiteAdmin: false,
    active: booleanOrNull(profile?.active),
    approved: booleanOrNull(profile?.approved),
    farmId: stringOrNull(profile?.farmId),
    previousFarmId: stringOrNull(profile?.previousFarmId),
    employmentStatus: stringOrNull(profile?.employmentStatus),
    removedAt: numberOrNull(profile?.removedAt),
    resignedAt: numberOrNull(profile?.resignedAt)
  };
}

const stringOrNull = (value: unknown) => typeof value === 'string' ? value : null;
const booleanOrNull = (value: unknown) => typeof value === 'boolean' ? value : null;
const numberOrNull = (value: unknown) => typeof value === 'number' && Number.isFinite(value) ? value : null;

export function publicFarm(farmId: string, farm: any, owner: any) {
  return {
    farmId,
    farmName: stringOrNull(farm?.farmName),
    description: stringOrNull(farm?.description),
    ownerUid: stringOrNull(farm?.ownerUid) ?? owner?.uid ?? null,
    ownerName: owner?.name ?? null,
    ownerEmail: owner?.email ?? null,
    createdAt: numberOrNull(farm?.createdAt)
  };
}

export function publicMember(uid: string, member: any) {
  return {
    uid,
    fullName: stringOrNull(member?.fullName),
    email: stringOrNull(member?.email),
    role: stringOrNull(member?.role),
    status: stringOrNull(member?.status),
    active: booleanOrNull(member?.active),
    joinedAt: numberOrNull(member?.joinedAt),
    leftAt: numberOrNull(member?.leftAt)
  };
}

export function publicPond(pondId: string, pond: any) {
  return {
    pondId,
    pondName: stringOrNull(pond?.pondName),
    deviceId: stringOrNull(pond?.deviceId),
    active: booleanOrNull(pond?.active),
    createdAt: numberOrNull(pond?.createdAt)
  };
}

export function publicDevice(deviceId: string, device: any) {
  return {
    deviceId,
    active: booleanOrNull(device?.active),
    status: stringOrNull(device?.status),
    farmId: stringOrNull(device?.farmId),
    pondId: stringOrNull(device?.pondId),
    linkedAt: numberOrNull(device?.linkedAt),
    registeredAt: numberOrNull(device?.registeredAt),
    authConfigured: typeof device?.authUid === 'string' && device.authUid.length > 0
  };
}

export function nextDeviceRecord(current: any, kind: DeviceAction, input: {deviceId: string; authUid?: string; actor: string; operationId: string; now: number}) {
  if(current?.lastAdminOperationId === input.operationId && current?.lastAdminAction === kind) return current;
  if(kind === 'register') {
    if(current !== null && current !== undefined) throw new Error('already-exists');
    if(!input.authUid) throw new Error('invalid-argument');
    return {deviceId:input.deviceId,authUid:input.authUid,active:true,status:'available',farmId:'',pondId:'',registeredAt:input.now,registeredBy:input.actor,
      lastAdminOperationId:input.operationId,lastAdminAction:kind,lastAdminUpdatedAt:input.now};
  }
  if(current === null || current === undefined) throw new Error('not-found');
  if((typeof current.farmId === 'string' && current.farmId) || (typeof current.pondId === 'string' && current.pondId) || current.status === 'assigned') {
    throw new Error('failed-precondition');
  }
  return {...current,deviceId:input.deviceId,active:kind === 'activate',status:kind === 'activate' ? 'available' : 'inactive',
    lastAdminOperationId:input.operationId,lastAdminAction:kind,lastAdminUpdatedAt:input.now};
}
