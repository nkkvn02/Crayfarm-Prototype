# Firebase security rules

`database.rules.baseline.json` is a strict, reviewable starting point. It is
not referenced by `firebase.json` and must not be deployed as-is: writes are
intentionally denied until CrayFarm's trusted-write design is completed.

The current Android client creates owner accounts, approves workers, links
devices, changes membership, and creates alerts directly from the client. The
ESP32 also signs in with an email/password account. Allowing those writes using
only client-controlled `users/{uid}/role` values would let a malicious client
grant itself owner privileges.

Before enabling production write rules:

1. Assign owner and device authorization with Firebase custom claims or a
   trusted backend/Cloud Function.
2. Give every ESP32 a dedicated authentication identity and store its UID as
   `devices/{deviceId}/authUid` from a trusted environment.
3. Move owner creation, worker approval/removal, and device linking to callable
   backend operations.
4. Allow device writes only when its UID matches the pond's assigned device.
5. Add Emulator Suite tests for owner, worker, device, unauthenticated, and
   cross-farm access before deployment.

Until then, keep the production database rules under explicit manual control.
