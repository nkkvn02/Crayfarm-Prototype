# Phase 0 audit — 2026-10-28 (Asia/Manila)

## Identity and deployed rules

The Android `app/google-services.json` identifies project **crayfarm-eb2ea**, project number **356838404753**, and regional database **crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app**. An authorized, read-only `firebase projects:list` returned that same project ID and number. The CLI's current default project was not trusted.

Retrieved actual deployed rules using an explicit project and database instance:

```
firebase database:get /.settings/rules --project crayfarm-eb2ea --instance crayfarm-eb2ea-default-rtdb --output audit/deployed.rules.json
```

Snapshot: `audit/deployed.rules.json`. No production rules or data were changed. Retrieval initially failed because the destination directory did not exist; the retry succeeded after creating it.

**Project identity and rules retrieval: VERIFIED. Production security and Android compatibility: NOT VERIFIED. Production release: BLOCKED.**

## Local/deployed differences and security findings

`firebase/README.md` says the local `database.rules.baseline.json` is not deployable and intentionally denies writes. No pre-existing `firebase.json`, `.firebaserc`, web app, or Functions backend was found in the repository.

The deployed rules materially differ from the baseline:

- They permit profile creation, owner/worker management, device assignment, farm creation, membership changes, and telemetry writes.
- They include `deviceUsers/{uid}` authorization, absent from the baseline. Android/ESP32 compatibility must be tested against the deployed snapshot, not inferred from the baseline.
- A new user may create their own profile with `role: admin` or `worker` without validation of farm association or approval. Reads subsequently trust the profile's `farmId`. This creates an apparent cross-farm access path for a malicious registering client. This finding is from static review; no production exploit was attempted.
- Existing users can change `active` while preserving the fields checked in the update condition. Application `active` cannot be treated as a tamper-proof authorization control.
- Employee request reads permit any database owner role; farm writes inherit broad owner permissions. Broader authorization and field validation require separate review.
- The root denies browser reads/writes, with no grant at `_crayfarmAdmin`. The proposed control namespace is therefore denied to clients under this snapshot. There is no need to grant browser admin reads or writes. Any production index addition requires approval.

Do not silently replace these rules: Android directly performs operations that the baseline denies. Resolve or explicitly accept the risks through a separate reviewed security change before release.

## Existing schema and application semantics

Inspected `AppModels.kt`, `AuthenticationRules.kt`, `AuthRepository.kt`, `WorkerRepository.kt`, owner registration in `AuthScreens.kt`, and the local rules/README.

| Source | Meaning |
| --- | --- |
| Firebase Auth | Registered accounts, UID, email, creation time, disabled state, custom claims |
| `users/{uid}` | `fullName`, `role`, `active`, `approved`, `farmId`, employment/profile fields |
| `role: admin` | Farm owner, never website authorization |
| `role: worker` | Farm worker |
| `active`, `approved` | Booleans; Android mapper defaults missing values to false |
| `farmId` | String; Android defaults missing value to empty |
| `employeeRequests/{code}` | Worker approval requests; pending/approved and related transition state |
| `farms/{farmId}/members/{uid}` | Membership, active state, employment status and timestamps |
| `farms/{farmId}/ponds/{pondId}` | Pond configuration, monitoring data and alerts |
| `devices/{deviceId}` | Registry and assignment metadata |
| `deviceUsers/{uid}` | Deployed device identity mapping |

Android rejects inactive profiles, waits for worker approval/farm association, and rejects owner profiles missing approval/farm association. Website developer accounts need no farm profile; valid revocation-checked authentication, exact website claim and no backend block authorize them. Unapproved workers without that claim are denied.

## Implementation decisions

- Separate `admin-web/` app. Existing uncommitted Android/ESP32 changes were present before work and are preserved.
- React/TypeScript/Vite with plain scoped application CSS; Tailwind was optional and is not required for this small UI.
- All directory/dashboard/control reads use authenticated callable Functions. The browser only uses Firebase Authentication and Functions.
- The frontend and backend now target the existing `crayfarm-eb2ea` project in production builds. No records are seeded. Automated security tests remain isolated in Firebase emulators and delete their temporary fixtures.
- Disable toggles **only Auth.disabled**, revokes refresh tokens, and preserves all profile/farm fields. Enable toggles Auth.disabled back to false and preserves application status. An inactive/unapproved Android profile remains inactive/unapproved.
- Revocation persists a backend access block before claim removal/token revocation. The block remains after completion; regranting requires separately reviewed recovery. Verification/database failures deny privileged requests.
- Durable intent, request binding, per-account locks and execution leases prevent competing changes. Unfinished locks are retained. Auth has no cross-service transaction with RTDB; final audit/status/lock release are one atomic RTDB update.
- Trusted `reconcileOperation` lets another authorized administrator resume an existing operation; original actor is retained and reconciler identity is audited. `operationPage` supports a complete bounded reconciliation scan. No scheduler is deployed.
- The current UI displays recent operations; older records can be recovered by the bounded operation API. No account deletion, role editing, membership editing, or public admin registration exists.

## Remaining release work

1. Resolve/accept the deployed authorization findings and approve the intended production project/rules.
2. Run staging tests with real signed tokens, refresh-token exchange, disabled sessions, and revocation propagation. Emulator checks are not proof of production token behavior.
3. Validate Android registration/login, approvals, membership, farm/pond access and device monitoring against the intended rules. These end-to-end regressions have not been run.
4. Reauthenticate the Firebase CLI, then verify Web app registration, Hosting, billing/IAM/region/runtime availability; target runtime is Node 22, while this machine tested with Node 24.
5. Review production origin/security headers, Firebase authorized domains, service identity privileges and production provisioning procedure. No service-account keys are included.
6. Confirm UI responsive behavior on target devices. Browser checks are recorded in README.

No production deployment approval has been requested or granted in this work.
