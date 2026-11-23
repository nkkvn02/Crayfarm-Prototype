import {Action} from './core';
export interface Effects {
  markRunning():Promise<void>;
  block():Promise<void>;
  mutate(action:Action):Promise<void>;
  checkpoint(step:string):Promise<void>;
  revoke():Promise<void>;
  verify(action:Action):Promise<boolean>;
  finish():Promise<void>;
  pending():Promise<void>;
}
// Auth and RTDB are not atomic. Durable intent + retained target lock permit retry.
// finish must atomically write audit, mark success, and release the target lock.
export async function execute(action:Action, effects:Effects):Promise<'succeeded'|'needs_reconciliation'> {
  try {
    await effects.markRunning();
    if(action==='revokeAdmin') await effects.block();
    await effects.mutate(action);
    await effects.checkpoint('authMutation');
    if(action!=='enable') await effects.revoke();
    await effects.checkpoint('sessionPolicy');
    if(!await effects.verify(action)) throw new Error('postcondition');
    await effects.finish();
    return 'succeeded';
  } catch {
    await effects.pending().catch(()=>{});
    return 'needs_reconciliation';
  }
}
