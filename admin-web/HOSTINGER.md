# Hostinger deployment

This package deploys the CrayFarm admin frontend and its trusted Node.js API as one Hostinger Web App. Use a Hostinger Business/Unlimited Web Hosting or Cloud plan that supports Node.js applications.

## Hostinger build settings

- Framework preset: `Other`
- Node.js version: `22.x`
- Package manager: `pnpm`
- Build command: `pnpm run hostinger:build`
- Entry file: `scripts/local-production-server.mjs`

The application listens on Hostinger's `PORT` and serves the compiled Vite frontend and `/api/*` endpoints from the same origin.

## Required environment variables

Add these in Hostinger's Environment Variables screen. Do not put their values in the repository or upload archive.

| Variable | Value |
| --- | --- |
| `NODE_ENV` | `production` |
| `CRAYFARM_DEPLOYMENT_MODE` | `hostinger` |
| `CRAYFARM_ADMIN_EMAIL` | `admincf@gmail.com` |
| `CRAYFARM_PUBLIC_ORIGIN` | Exact HTTPS origin, for example `https://admin.example.com` with no trailing slash |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | Base64 of a service-account JSON file belonging to Firebase project `crayfarm-eb2ea` |

Hostinger supplies `PORT`; do not set it manually.

## Create the service-account value locally

Download a new service-account JSON file from Firebase Console → Project settings → Service accounts. Keep it outside this repository. Convert it in PowerShell without printing the secret:

```powershell
$credentialPath = 'C:\secure\crayfarm-service-account.json'
$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes($credentialPath))
$encoded | Set-Clipboard
```

Paste the clipboard value into Hostinger as `FIREBASE_SERVICE_ACCOUNT_BASE64`. Delete the downloaded JSON when it is no longer needed or store it in a secure credential vault. If it is ever exposed, revoke that service-account key immediately.

## Deployment

1. In Hostinger, choose **Websites → Create site → Web App**.
2. Choose a domain or temporary Hostinger domain.
3. Select **Upload your files** and upload `artifacts/crayfarm-admin-hostinger.zip`, or import the project from a private Git repository.
4. Enter the build settings and environment variables above.
5. Deploy and review the build logs.
6. Ensure `CRAYFARM_PUBLIC_ORIGIN` exactly matches the final HTTPS address. Redeploy after changing it.
7. Add the final domain to Firebase Authentication → Settings → Authorized domains.
8. Sign in with the existing `admincf@gmail.com` Firebase account.

The service account is server-only. The browser receives only the existing public Firebase Web configuration and an authenticated API response.

