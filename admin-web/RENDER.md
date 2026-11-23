# Render deployment

The repository includes a Render Blueprint at `../render.yaml`.

## Service settings

- Type: Web Service
- Plan: Free
- Region: Singapore
- Root directory: `admin-web`
- Build command: `corepack enable && pnpm install --frozen-lockfile && pnpm run render:build`
- Start command: `pnpm start`
- Health check: `/healthz`

## Required secret

`FIREBASE_SERVICE_ACCOUNT_BASE64` must be a base64-encoded Firebase service-account JSON key for project `crayfarm-eb2ea`. Add it only through Render's secret environment-variable field. Never commit the JSON file or its encoded contents.

After Render assigns the final URL, confirm that `CRAYFARM_PUBLIC_ORIGIN` exactly matches its HTTPS origin and add the hostname to Firebase Authentication's authorized domains.
