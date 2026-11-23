# CrayFarm admin website

Private React/TypeScript administration website for the existing Firebase project `crayfarm-eb2ea`. The runtime does not seed, manufacture, or display placeholder records. Registered users come from Firebase Authentication and profile fields are joined from the existing Realtime Database paths.

See [AUDIT.md](AUDIT.md) for the deployed-rule review and [DEPLOYMENT.md](DEPLOYMENT.md) for the guarded Blaze release procedure.

## Current status

- Frontend and privileged backend are implemented.
- Production configuration uses the existing CrayFarm project and regional Realtime Database.
- A separate `firebase.production.json` deploys only Functions and Hosting; it cannot deploy database rules.
- Production has not been changed or deployed.
- The first local review version is implemented and can run entirely against isolated emulators.
- Project identity, Web app registration, and the default Hosting site are verified. Functions remain blocked until Blaze is enabled and `cloudfunctions.googleapis.com` is activated.

## Build and preview

Use Node 22 and pnpm 11 for release validation.

```powershell
cd C:\Users\Nikko\AndroidStudioProjects\CrayFarm\admin-web
pnpm install
pnpm --dir functions install
pnpm run build
pnpm run backend:build
pnpm run serve
```

Open `http://127.0.0.1:5173/`. A normal production preview connects to the existing Firebase project. It becomes fully usable only after the backend Functions are deployed and an existing Auth account receives the `crayfarmAdmin: true` custom claim.

For first-version review without billing, start the Firebase emulators, create the temporary local administrator, and build with `VITE_USE_EMULATORS=true`. Emulator mode uses only project `demo-crayfarm`; it cannot reach production services.

## Local production data mode (no Blaze required)

The trusted localhost mode serves the frontend and API together on `127.0.0.1:5173`. Firebase administrator access stays in the Node process and is obtained from the authenticated Firebase CLI session; it is never embedded in browser code or written to the repository.

The localhost backend can list Firebase Authentication accounts, join allowlisted fields from `users/{uid}`, calculate live owner/worker totals, inspect operation history, disable or re-enable accounts, and revoke website-administrator access. Mutations use stable operation IDs, per-account locks, execution leases, refresh-token revocation, postcondition checks, and deterministic audit records. Self-administration is rejected.

```powershell
firebase login --reauth
.\scripts\start-local-production.ps1 -AdminEmail admincf@gmail.com
```

The authorized account must exist in Firebase Authentication and have the exact boolean custom claim `crayfarmAdmin: true`. The launcher builds with `VITE_LOCAL_PRODUCTION=true`, binds only to loopback, and stops when its terminal process is stopped.

The Firebase client configuration in `.env.example` is public application configuration from the existing Android project. No service-account credential belongs in this directory.

## Admin provisioning

The trusted provisioning script targets only `crayfarm-eb2ea`, checks exact UID/email, refuses disabled or blocked accounts, preserves unrelated custom claims, and does not create an account.

It requires Application Default Credentials with permission to administer Firebase Authentication. Never store the credential in the repository.

```powershell
$env:CRAYFARM_FIREBASE_PROJECT_ID='crayfarm-eb2ea'
node scripts/provision.mjs EXISTING_UID EXPECTED_EMAIL
```

Sign out and sign back in after provisioning to receive a fresh ID token. Database role `admin` continues to mean farm owner and never grants website access.

## API and data contract

Every privileged endpoint verifies the raw ID token with revocation checking, requires the exact `crayfarmAdmin === true` claim, checks the backend access block, and fails closed.

| Function | Contract |
| --- | --- |
| `session` | Verify website access |
| `directory` | 25 Auth accounts per cursor page with allowlisted profile fields |
| `overview` | Bounded Auth/profile counts, 100 accounts per cursor page |
| `recentOperations` | Latest 30 administrative operations |
| `operationPage` | Bounded complete recovery scan |
| `orphanProfiles` | Bounded database-profile scan for missing Auth accounts |
| `mutateAccount` | Disable, enable, or revoke website-admin access |
| `reconcileOperation` | Resume an interrupted operation under another authorized administrator |

Registered users means Firebase Authentication accounts. Accounts missing profiles remain visible. Exact email or UID search covers the Auth directory; arbitrary name/substring search is deliberately unsupported without a maintained search index.

Disable/re-enable changes Firebase Auth state and session policy only. It does not rewrite `active`, `approved`, `farmId`, membership, or farm-owner/worker roles.

## Operations and recovery

Consequential actions persist an operation intent before side effects. Stable operation IDs, per-account locks, execution leases, deterministic audit IDs, postcondition checks, and reconciliation handle retries and cross-service partial failures.

The browser cannot write audit or operation records. A revocation block is written before removing the website claim and revoking refresh tokens. Self-administration is rejected.

## Tests

```powershell
pnpm run build
pnpm run backend:build
pnpm test
```

The automated integration suite uses isolated Firebase emulators and deletes its temporary fixtures. It never connects to `crayfarm-eb2ea`:

```powershell
pnpm run emulators
# In a second terminal:
node scripts/integration.mjs
```

Recorded results on 2026-10-28:

- Frontend type check and production build: passed.
- Backend TypeScript build: passed.
- Security and failure-injection unit tests: 15 passed.
- Isolated emulator integration checks: 12 passed.
- Existing Android unit tests: 47 passed.
- Live production authentication, real-token revocation, deployed Functions, and Android end-to-end regressions: not yet verified.

## Production deployment

Do not use the emulator `firebase.json` for deployment. Its deployment hooks deliberately fail and its database rules deny writes.

Run the complete fail-closed release check before deployment:

```powershell
pnpm run release:check
```

It verifies the production project and Web app, confirms emulator endpoints are absent from the bundle, and refuses a production configuration containing database rules.

After Blaze upgrade, budget alerts, Cloud Functions API activation, review of the intended existing account, and explicit deployment approval, use the guarded script:

```powershell
.\scripts\deploy-production.ps1 -ConfirmProject crayfarm-eb2ea -ConfirmBlaze
```

The production configuration deploys only the `crayfarm-admin` Functions codebase and the existing Hosting site. It contains no database-rules target, so existing Android behavior remains untouched by this release command.
