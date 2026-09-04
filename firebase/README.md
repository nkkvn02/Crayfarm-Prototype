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

## Device registry contract

Add Pond now consumes only device records that already exist under
`devices/{deviceId}`. A registry record must contain the matching `deviceId`,
`active: true`, `status: "available"`, blank `farmId` and `pondId` fields, and
the dedicated ESP32 Firebase Authentication UID in `authUid`. See
`device-registry.seed.example.json` for the current device example.

Existing assigned device nodes created by an older app version must be migrated
before using this validation. Preserve their current `farmId`, `pondId`, and
`authUid`, then add the matching `deviceId`, `active: true`, and
`status: "assigned"`. Do not replace an assigned record with the available seed
example because that could make an in-use device claimable.

When an owner links a pond, the Android transaction changes only the assignment
fields and preserves registration metadata. Pond deletion restores
`status: "available"` and blank assignment fields instead of deleting the
device record. Unknown Device IDs are never created by the Android app.

Production rules must reject device-record creation and deletion from mobile
clients. They should allow only trusted owners to transition an existing active
record from available to assigned, preferably through a callable backend using
custom claims. Do not deploy the baseline file as a workaround; its writes are
intentionally denied.
