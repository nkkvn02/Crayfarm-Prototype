# Firebase production deployment

The admin website deploys to Firebase Hosting and callable Cloud Functions in project `crayfarm-eb2ea`. The release configuration cannot deploy Realtime Database rules.

## One-time Blaze setup

1. Link a Cloud Billing account to `crayfarm-eb2ea` in Firebase Console to upgrade it to Blaze.
2. Create Google Cloud budget alerts before deploying. Alerts notify; they do not automatically cap spending.
3. Enable `cloudfunctions.googleapis.com` for the same project and wait several minutes for propagation.
4. Confirm `crayfarm-eb2ea.web.app` and any custom production domain are listed under Authentication → Settings → Authorized domains. Do not add `localhost` for the deployed site.
5. After the first Functions deployment, configure build-artifact retention:

   ```powershell
   .\node_modules\.bin\firebase.cmd functions:artifacts:setpolicy --project crayfarm-eb2ea --location asia-southeast1 --days 7
   ```

The callable Functions use Node.js 22, region `asia-southeast1`, 256 MiB memory, zero minimum instances, fractional Gen-1 CPU behavior, and a maximum of three instances per endpoint.

## Readiness and deployment

From `admin-web`:

```powershell
pnpm run deployment:status
pnpm run release:check
.\scripts\deploy-production.ps1 -ConfirmProject crayfarm-eb2ea -ConfirmBlaze
```

The guarded deployment verifies the project, Web app, Hosting site, CLI identity, production bundle, runtime, tests, and Cloud Functions API. It deploys Functions first and deploys Hosting only after the backend succeeds. It never includes database rules.

## Verification and rollback

After deployment:

1. Open `https://crayfarm-eb2ea.web.app` and sign in with the provisioned website administrator.
2. Verify Overview, Users, Farms, Devices, Former workers, and Activity using read-only actions first.
3. Test one reversible account or device action and confirm its Activity record.
4. Check Cloud Functions logs and Firebase/Google Cloud usage dashboards.

Firebase Hosting releases can be rolled back in the Hosting console. Functions do not have the same one-click rollback; redeploy the last known-good source. Existing Realtime Database rules and Android behavior remain outside this deployment.
