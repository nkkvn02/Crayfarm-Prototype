# CrayFarm ESP32 setup

## Before uploading

1. Create `CrayFarmTest/firebase_credentials.h` with the required
   `CRAYFARM_FIREBASE_API_KEY`, `CRAYFARM_FIREBASE_USER_EMAIL`, and
   `CRAYFARM_FIREBASE_USER_PASSWORD` constants.
2. Keep that file local. It is ignored by Git and must not be committed.
3. Use a dedicated Firebase Authentication account for the device rather than
   an owner or worker account.
4. Open `CrayFarmTest/CrayFarmTest.ino`, confirm the device ID and calibration
   constants, and upload it from the Arduino IDE.

Farm Wi-Fi credentials are no longer compiled into the sketch. The ESP32 saves
them separately in non-volatile storage after phone-based provisioning.

## First-time Wi-Fi provisioning

1. Power the ESP32 and wait for the `CrayFarm-Setup-XXXXXX` Wi-Fi network.
2. The six-character suffix identifies the device.
3. Connect a phone to that network using `CrayFarm-XXXXXX` as the password,
   where `XXXXXX` is the same suffix shown in the network name.
4. If the setup page does not open automatically, visit `http://192.168.4.1`.
5. Select a detected 2.4 GHz network or enter a hidden SSID manually.
6. Enter the farm Wi-Fi password and press **Save and connect**.
7. The ESP32 validates the connection before saving, then restarts and begins
   its normal Firebase and sensor-monitoring operation.

The setup portal closes after ten minutes when unused. Restart the ESP32 to try
again. A phone may report that the setup network has no internet; remain
connected until provisioning finishes.

## Changing the farm Wi-Fi

While the ESP32 is already powered and running, hold its **BOOT** button for at
least five seconds. A short press does not erase anything. After the confirmed
long press, the ESP32 clears only its saved Wi-Fi configuration, restarts, and
opens the setup portal.

Do not hold BOOT while powering or resetting the board. On common ESP32 boards,
that can enter firmware-download mode instead of starting the application.

During a temporary outage, the ESP32 retries the saved network every ten
seconds. It preserves the credentials and opens the setup portal only after the
network has remained unavailable for two minutes.

## Security notes

- The captive portal never displays Firebase credentials or calibration data.
- The farm Wi-Fi password is stored only in the ESP32's Wi-Fi preferences.
- Wi-Fi and Firebase passwords are never printed to Serial.
- The setup access point is WPA2-protected with the per-device password
  described above.
- Firebase authentication begins only after Wi-Fi is connected.

The build deliberately stops with a clear error if either local credential
header is missing.

## Device QR label

Place a QR label and the human-readable Device ID on each prepared ESP32. The
QR payload must use this exact format:

```text
CRAYFARM_DEVICE:CRAY-X7K9_Q2M8-V4R6_N1P3
```

Replace `CRAY-X7K9_Q2M8-V4R6_N1P3` with the value assigned to `DEVICE_ID` in
`CrayFarmTest.ino`. The label contains only the device identity; never include
Wi-Fi passwords, Firebase credentials, or other secrets. The Android app fills
the Add Pond Device ID field after a valid scan, but the owner must still press
**Link Device** before the existing Firebase transaction claims the device.

Before linking, a system administrator must create the matching official record
under `devices/{deviceId}` using the registry contract documented in
`firebase/README.md`. A QR label identifies the device but does not register it.

Each successful latest-reading upload now includes `lastSeen`,
`firmwareVersion`, `wifiRssi`, and `uptimeMs` in the pond's existing `latest`
node. `lastSeen` and the compatibility field `updatedAt` use Firebase server
timestamps, so Android freshness checks do not depend on the ESP32 clock.
