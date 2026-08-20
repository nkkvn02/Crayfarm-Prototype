# CrayFarm ESP32 setup

1. Copy `CrayFarmTest/wifi_credentials.example.h` to
   `CrayFarmTest/wifi_credentials.h`.
2. Copy `CrayFarmTest/firebase_credentials.example.h` to
   `CrayFarmTest/firebase_credentials.h`.
3. Fill in the copied files. They are ignored by Git and must not be committed.
4. Use a dedicated Firebase Authentication account for the device rather than
   an owner or worker account.
5. Open `CrayFarmTest/CrayFarmTest.ino`, confirm the device ID and calibration
   constants, and upload it from the Arduino IDE.

The build deliberately stops with a clear error if either local credential
header is missing.

Each successful latest-reading upload now includes `lastSeen`,
`firmwareVersion`, `wifiRssi`, and `uptimeMs` in the pond's existing `latest`
node. `lastSeen` and the compatibility field `updatedAt` use Firebase server
timestamps, so Android freshness checks do not depend on the ESP32 clock.
