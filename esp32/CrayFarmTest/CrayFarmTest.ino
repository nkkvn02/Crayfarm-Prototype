/**
 * CrayFarm ESP32
 *
 * Real sensors:
 * - DS18B20 temperature sensor on GPIO 4
 * - Analog turbidity sensor on GPIO 34
 * - AJ-SR04M waterproof ultrasonic water-level sensor (depth in cm)
 *      TRIG -> GPIO 26
 *      ECHO -> GPIO 27 (through a 5V-to-3.3V voltage divider)
 *
 * Turbidity is uploaded as a calibrated relative percentage:
 * - 0%   = clear-water reference
 * - 100% = cloudy-water reference
 *
 * Sensors not yet connected:
 * - pH
 * - TDS
 *
 * pH and TDS are NOT uploaded to Firebase.
 *
 * Upload behavior:
 * - Latest readings: every 5 seconds
 * - History record: every 15 minutes
 *
 * Firebase paths:
 * /devices/{DEVICE_ID}
 * /farms/{farmId}/ponds/{pondId}/latest
 * /farms/{farmId}/ponds/{pondId}/history/{epochMilliseconds}
 */

const char *DEVICE_ID = "CRAY-X7K9_Q2M8-V4R6_N1P3";
const char *FIRMWARE_VERSION = "1.1.0";

#define ENABLE_DATABASE
#define ENABLE_USER_AUTH

#include <WiFi.h>
#include <FirebaseClient.h>
#include "ExampleFunctions.h"
#include "CrayFarmWiFiProvisioning.h"

#include <OneWire.h>
#include <DallasTemperature.h>
#include <time.h>

#if !__has_include("firebase_credentials.h")
#error "Copy firebase_credentials.example.h to firebase_credentials.h and enter the ESP32 Firebase account credentials."
#endif

#include "firebase_credentials.h"

#define DATABASE_URL \
    "https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app"

#define ONE_WIRE_BUS 4
#define TURBIDITY_PIN 34

#define WATER_TRIG_PIN 26
#define WATER_ECHO_PIN 27

// IMPORTANT:
// Measure from the AJ-SR04M sensor face straight down to the pond/container bottom.
// Change this value to your real installation measurement.
const float SENSOR_TO_BOTTOM_CM = 40.0f;

// The Android app uses adjustable safe thresholds.
// Default safe range there is 15 cm to 25 cm.
// The ESP32 only measures and uploads the actual water depth in centimeters.

// AJ-SR04M timeout. A timeout means no echo was received.
const unsigned long WATER_ECHO_TIMEOUT_US = 35000UL;

// Calibration references from the user's water tests.
// Clear water ADC voltage: 1.810 V
// Cloudy water ADC voltage: 0.715 V
const float TURBIDITY_CLEAR_VOLTAGE = 1.810f;
const float TURBIDITY_CLOUDY_VOLTAGE = 0.715f;

const int TURBIDITY_SAMPLE_COUNT = 50;
const int TURBIDITY_SAMPLE_DELAY_MS = 10;

// With a 100kΩ pull-down resistor from GPIO 34 to GND,
// a disconnected turbidity signal reads close to 0 V.
const float TURBIDITY_DISCONNECTED_VOLTAGE = 0.05f;

UserAuth user_auth(
    CRAYFARM_FIREBASE_API_KEY,
    CRAYFARM_FIREBASE_USER_EMAIL,
    CRAYFARM_FIREBASE_USER_PASSWORD
);

FirebaseApp app;
RealtimeDatabase Database;

SSL_CLIENT ssl_client;

using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
CrayFarmWiFiProvisioning wifiProvisioning;

String linkedFarmId = "";
String linkedPondId = "";
String latestPath = "";
String historyPath = "";

bool deviceLinked = false;
bool clockReady = false;
bool firebaseInitialized = false;

unsigned long lastLatestUpload = 0;
unsigned long lastHistoryUpload = 0;
unsigned long lastLinkCheck = 0;

const unsigned long LATEST_UPLOAD_INTERVAL =
    5000UL;

const unsigned long HISTORY_UPLOAD_INTERVAL =
    15UL * 60UL * 1000UL;

const unsigned long LINK_CHECK_INTERVAL =
    10000UL;

void startFirebaseAfterWiFi();
void synchronizeClock();
bool loadDeviceAssignment();
void readSensors(
    float &temperature,
    bool &temperatureConnected,
    float &turbidity,
    bool &turbidityConnected,
    float &waterLevel,
    bool &waterLevelConnected
);
float readRelativeTurbidityPercent();
bool readWaterLevelCm(
    float &waterLevelCm,
    float &distanceCm
);

bool readWaterLevelCm(
    float &waterLevelCm,
    float &distanceCm
)
{
    // Send a 10-microsecond trigger pulse.
    digitalWrite(WATER_TRIG_PIN, LOW);
    delayMicroseconds(3);

    digitalWrite(WATER_TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(WATER_TRIG_PIN, LOW);

    const unsigned long duration =
        pulseIn(
            WATER_ECHO_PIN,
            HIGH,
            WATER_ECHO_TIMEOUT_US
        );

    // No echo = disconnected, wiring issue, or target not detected.
    if (duration == 0)
    {
        distanceCm = NAN;
        waterLevelCm = NAN;
        return false;
    }

    distanceCm =
        (duration * 0.0343f) / 2.0f;

    if (
        isnan(distanceCm) ||
        distanceCm <= 0.0f ||
        distanceCm > 600.0f
    )
    {
        waterLevelCm = NAN;
        return false;
    }

    // Actual water depth:
    // sensor-to-bottom distance minus sensor-to-water-surface distance.
    waterLevelCm =
        SENSOR_TO_BOTTOM_CM -
        distanceCm;

    // If the measured surface is below the bottom reference,
    // report 0 cm rather than a negative depth.
    if (waterLevelCm < 0.0f)
    {
        waterLevelCm = 0.0f;
    }

    // A reading above the physical sensor-to-bottom distance is invalid.
    if (waterLevelCm > SENSOR_TO_BOTTOM_CM)
    {
        waterLevelCm = SENSOR_TO_BOTTOM_CM;
    }

    return true;
}

void uploadLatestReadings(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected
);
void uploadHistoryRecord(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected
);
uint64_t currentEpochMilliseconds();
String uint64ToString(uint64_t value);
String buildReadingJson(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected,
    uint64_t timestamp,
    const char *timestampField,
    bool includeDeviceHealth
);
void printFirebaseError(const String &operation);

void setup()
{
    Serial.begin(115200);
    delay(500);

    Serial.println();
    Serial.println("================================");
    Serial.println("CrayFarm ESP32 Starting");
    Serial.print("Device ID: ");
    Serial.println(DEVICE_ID);
    Serial.println("================================");

    sensors.begin();

    pinMode(TURBIDITY_PIN, INPUT);

    pinMode(WATER_TRIG_PIN, OUTPUT);
    digitalWrite(WATER_TRIG_PIN, LOW);
    pinMode(WATER_ECHO_PIN, INPUT);

    analogReadResolution(12);
    analogSetPinAttenuation(
        TURBIDITY_PIN,
        ADC_11db
    );

    wifiProvisioning.begin(DEVICE_ID);
    wifiProvisioning.connectOrProvision();
    startFirebaseAfterWiFi();

    Serial.println("Hardware setup complete");
}

void loop()
{
    if (!wifiProvisioning.maintain())
    {
        return;
    }

    if (!firebaseInitialized)
    {
        startFirebaseAfterWiFi();
    }

    if (!firebaseInitialized)
    {
        return;
    }

    app.loop();
    Database.loop();

    if (!clockReady)
    {
        synchronizeClock();
    }

    if (!app.ready())
    {
        return;
    }

    const unsigned long now = millis();

    if (!deviceLinked &&
        now - lastLinkCheck >= LINK_CHECK_INTERVAL)
    {
        lastLinkCheck = now;
        loadDeviceAssignment();
    }

    if (!deviceLinked)
    {
        return;
    }

    const bool latestDue =
        now - lastLatestUpload >=
        LATEST_UPLOAD_INTERVAL;

    const bool historyDue =
        now - lastHistoryUpload >=
        HISTORY_UPLOAD_INTERVAL;

    if (!latestDue && !historyDue)
    {
        return;
    }

    float temperature = NAN;
    bool temperatureConnected = false;

    float turbidity = NAN;
    bool turbidityConnected = false;

    float waterLevel = NAN;
    bool waterLevelConnected = false;

    readSensors(
        temperature,
        temperatureConnected,
        turbidity,
        turbidityConnected,
        waterLevel,
        waterLevelConnected
    );

    if (latestDue)
    {
        lastLatestUpload = now;

        uploadLatestReadings(
            temperature,
            temperatureConnected,
            turbidity,
            turbidityConnected,
            waterLevel,
            waterLevelConnected
        );
    }

    if (historyDue && clockReady)
    {
        lastHistoryUpload = now;

        uploadHistoryRecord(
            temperature,
            temperatureConnected,
            turbidity,
            turbidityConnected,
            waterLevel,
            waterLevelConnected
        );
    }
}

void startFirebaseAfterWiFi()
{
    if (
        firebaseInitialized ||
        WiFi.status() != WL_CONNECTED
    )
    {
        return;
    }

    synchronizeClock();

    Serial.println(
        "[Firebase] Starting authentication"
    );

    Firebase.printf(
        "Firebase Client v%s\n",
        FIREBASE_CLIENT_VERSION
    );

    set_ssl_client_insecure_and_buffer(
        ssl_client
    );

    initializeApp(
        aClient,
        app,
        getAuth(user_auth),
        120 * 1000
    );

    app.getApp<RealtimeDatabase>(Database);
    Database.url(DATABASE_URL);

    firebaseInitialized = true;

    Serial.println(
        "[Firebase] Initialization requested"
    );
}

void synchronizeClock()
{
    if (WiFi.status() != WL_CONNECTED)
    {
        return;
    }

    Serial.print("Synchronizing clock");

    configTime(
        0,
        0,
        "pool.ntp.org",
        "time.nist.gov"
    );

    const unsigned long startedAt = millis();

    while (
        time(nullptr) < 1700000000 &&
        millis() - startedAt < 15000UL
    )
    {
        Serial.print(".");
        delay(300);
    }

    Serial.println();

    clockReady =
        time(nullptr) >= 1700000000;

    if (clockReady)
    {
        Serial.println(
            "Clock synchronized."
        );
    }
    else
    {
        Serial.println(
            "Clock synchronization failed. "
            "Latest uploads will continue, "
            "but history will wait."
        );
    }
}

bool loadDeviceAssignment()
{
    const String deviceBasePath =
        "/devices/" + String(DEVICE_ID);

    Serial.println();
    Serial.println("--------------------------------");
    Serial.print("Checking device path: ");
    Serial.println(deviceBasePath);

    String farmId = Database.get<String>(
        aClient,
        deviceBasePath + "/farmId"
    );

    if (aClient.lastError().code() != 0)
    {
        printFirebaseError("reading farmId");
        deviceLinked = false;
        return false;
    }

    delay(200);

    String pondId = Database.get<String>(
        aClient,
        deviceBasePath + "/pondId"
    );

    if (aClient.lastError().code() != 0)
    {
        printFirebaseError("reading pondId");
        deviceLinked = false;
        return false;
    }

    farmId.trim();
    pondId.trim();

    farmId.replace("\"", "");
    pondId.replace("\"", "");

    const bool invalidFarmId =
        farmId.isEmpty() ||
        farmId == "null";

    const bool invalidPondId =
        pondId.isEmpty() ||
        pondId == "null";

    if (invalidFarmId || invalidPondId)
    {
        deviceLinked = false;
        linkedFarmId = "";
        linkedPondId = "";
        latestPath = "";
        historyPath = "";

        Serial.println(
            "Device is not linked yet."
        );
        Serial.println("--------------------------------");

        return false;
    }

    linkedFarmId = farmId;
    linkedPondId = pondId;

    const String pondBasePath =
        "/farms/" +
        linkedFarmId +
        "/ponds/" +
        linkedPondId;

    latestPath =
        pondBasePath +
        "/latest";

    historyPath =
        pondBasePath +
        "/history";

    deviceLinked = true;

    Serial.println("Device link found.");
    Serial.print("Farm ID: ");
    Serial.println(linkedFarmId);
    Serial.print("Pond ID: ");
    Serial.println(linkedPondId);
    Serial.print("Latest path: ");
    Serial.println(latestPath);
    Serial.print("History path: ");
    Serial.println(historyPath);
    Serial.println("--------------------------------");

    return true;
}

void readSensors(
    float &temperature,
    bool &temperatureConnected,
    float &turbidity,
    bool &turbidityConnected,
    float &waterLevel,
    bool &waterLevelConnected
)
{
    // ---------------- Temperature ----------------
    sensors.requestTemperatures();

    temperature =
        sensors.getTempCByIndex(0);

    temperatureConnected =
        temperature != DEVICE_DISCONNECTED_C &&
        !isnan(temperature);

    if (!temperatureConnected)
    {
        temperature = NAN;
        Serial.println("Temperature sensor: DISCONNECTED");
    }
    else
    {
        Serial.print("Temperature: ");
        Serial.print(temperature, 1);
        Serial.println(" C");
    }

    // ---------------- Turbidity ----------------
    uint32_t totalMillivolts = 0;

    for (
        int sample = 0;
        sample < TURBIDITY_SAMPLE_COUNT;
        sample++
    )
    {
        totalMillivolts +=
            analogReadMilliVolts(
                TURBIDITY_PIN
            );

        delay(
            TURBIDITY_SAMPLE_DELAY_MS
        );
    }

    const float averageMillivolts =
        totalMillivolts /
        static_cast<float>(
            TURBIDITY_SAMPLE_COUNT
        );

    const float adcVoltage =
        averageMillivolts / 1000.0f;

    // Disconnect detection is electrical only. A calculated 0% is valid
    // clear-water turbidity and remains CONNECTED.
    turbidityConnected =
        adcVoltage >
        TURBIDITY_DISCONNECTED_VOLTAGE;

    if (!turbidityConnected)
    {
        turbidity = NAN;

        Serial.println(
            "Turbidity sensor: DISCONNECTED"
        );
    }
    else
    {
        float relativeTurbidity =
            (
                TURBIDITY_CLEAR_VOLTAGE -
                adcVoltage
            ) /
            (
                TURBIDITY_CLEAR_VOLTAGE -
                TURBIDITY_CLOUDY_VOLTAGE
            ) *
            100.0f;

        turbidity =
            constrain(
                relativeTurbidity,
                0.0f,
                100.0f
            );

        Serial.print(
            "Turbidity ADC voltage: "
        );
        Serial.print(
            adcVoltage,
            3
        );
        Serial.println(" V");

        Serial.print(
            "Relative turbidity: "
        );
        Serial.print(
            turbidity,
            1
        );
        Serial.println(" %");
    }

    // ---------------- Water level (AJ-SR04M) ----------------
    float waterDistanceCm = NAN;

    waterLevelConnected =
        readWaterLevelCm(
            waterLevel,
            waterDistanceCm
        );

    if (waterLevelConnected)
    {
        Serial.print("Water distance: ");
        Serial.print(waterDistanceCm, 1);
        Serial.println(" cm");

        Serial.print("Water depth: ");
        Serial.print(waterLevel, 1);
        Serial.println(" cm");
    }
    else
    {
        waterLevel = NAN;
        Serial.println(
            "Water level sensor: DISCONNECTED / NO ECHO"
        );
    }

    Serial.println("pH: NOT CONNECTED");
    Serial.println("TDS: NOT CONNECTED");
}

float readRelativeTurbidityPercent()
{
    uint32_t totalMillivolts = 0;

    for (
        int sample = 0;
        sample < TURBIDITY_SAMPLE_COUNT;
        sample++
    )
    {
        totalMillivolts +=
            analogReadMilliVolts(
                TURBIDITY_PIN
            );

        delay(
            TURBIDITY_SAMPLE_DELAY_MS
        );
    }

    const float averageMillivolts =
        totalMillivolts /
        static_cast<float>(
            TURBIDITY_SAMPLE_COUNT
        );

    const float adcVoltage =
        averageMillivolts / 1000.0f;

    float relativeTurbidity =
        (
            TURBIDITY_CLEAR_VOLTAGE -
            adcVoltage
        ) /
        (
            TURBIDITY_CLEAR_VOLTAGE -
            TURBIDITY_CLOUDY_VOLTAGE
        ) *
        100.0f;

    relativeTurbidity =
        constrain(
            relativeTurbidity,
            0.0f,
            100.0f
        );

    Serial.print("Turbidity ADC voltage: ");
    Serial.print(adcVoltage, 3);
    Serial.println(" V");

    Serial.print("Turbidity percentage: ");
    Serial.print(relativeTurbidity, 1);
    Serial.println(" %");

    return relativeTurbidity;
}

void uploadLatestReadings(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected
)
{
    const String json = buildReadingJson(
        temperature,
        temperatureConnected,
        turbidity,
        turbidityConnected,
        waterLevel,
        waterLevelConnected,
        0,
        "updatedAt",
        true
    );

    // set() replaces the entire latest node, so any old
    // dummy ph/tds/waterLevel fields are removed.
    const bool success =
        Database.set<object_t>(
            aClient,
            latestPath,
            object_t(json)
        );

    if (
        !success ||
        aClient.lastError().code() != 0
    )
    {
        printFirebaseError(
            "uploading latest readings"
        );

        deviceLinked = false;
        return;
    }

    Serial.print("Latest uploaded: ");
    Serial.println(latestPath);
}

void uploadHistoryRecord(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected
)
{
    const uint64_t recordedAt =
        currentEpochMilliseconds();

    if (recordedAt == 0)
    {
        Serial.println(
            "History skipped because clock is not ready."
        );
        return;
    }

    const String recordKey =
        uint64ToString(recordedAt);

    const String recordPath =
        historyPath +
        "/" +
        recordKey;

    const String json = buildReadingJson(
        temperature,
        temperatureConnected,
        turbidity,
        turbidityConnected,
        waterLevel,
        waterLevelConnected,
        recordedAt,
        "recordedAt",
        false
    );

    const bool success =
        Database.set<object_t>(
            aClient,
            recordPath,
            object_t(json)
        );

    if (
        !success ||
        aClient.lastError().code() != 0
    )
    {
        printFirebaseError(
            "uploading history record"
        );
        return;
    }

    Serial.print(
        "15-minute history saved: "
    );
    Serial.println(recordPath);
}

uint64_t currentEpochMilliseconds()
{
    const time_t currentTime =
        time(nullptr);

    if (currentTime < 1700000000)
    {
        return 0;
    }

    return
        static_cast<uint64_t>(currentTime) *
        1000ULL;
}

String uint64ToString(uint64_t value)
{
    char buffer[24];

    snprintf(
        buffer,
        sizeof(buffer),
        "%llu",
        static_cast<unsigned long long>(value)
    );

    return String(buffer);
}

String buildReadingJson(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    float waterLevel,
    bool waterLevelConnected,
    uint64_t timestamp,
    const char *timestampField,
    bool includeDeviceHealth
)
{
    String json = "{";

    json +=
        "\"temperatureConnected\":" +
        String(
            temperatureConnected
                ? "true"
                : "false"
        );

    if (temperatureConnected)
    {
        json +=
            ",\"temperature\":" +
            String(
                temperature,
                1
            );
    }

    json +=
        ",\"turbidityConnected\":" +
        String(
            turbidityConnected
                ? "true"
                : "false"
        );

    if (turbidityConnected)
    {
        json +=
            ",\"turbidity\":" +
            String(
                turbidity,
                1
            );
    }

    json +=
        ",\"phConnected\":false"
        ",\"tdsConnected\":false";

    json +=
        ",\"waterLevelConnected\":" +
        String(
            waterLevelConnected
                ? "true"
                : "false"
        );

    if (waterLevelConnected)
    {
        json +=
            ",\"waterLevel\":" +
            String(
                waterLevel,
                1
            );
    }

    if (includeDeviceHealth)
    {
        // Firebase resolves these placeholders atomically when the latest
        // reading is accepted, so freshness does not depend on ESP32 clock
        // synchronization.
        json +=
            ",\"updatedAt\":{\".sv\":\"timestamp\"}"
            ",\"lastSeen\":{\".sv\":\"timestamp\"}";

        json +=
            ",\"firmwareVersion\":\"" +
            String(FIRMWARE_VERSION) +
            "\"";

        json +=
            ",\"wifiRssi\":" +
            String(WiFi.RSSI());

        json +=
            ",\"uptimeMs\":" +
            uint64ToString(
                static_cast<uint64_t>(millis())
            );
    }
    else
    {
        json +=
            ",\"" +
            String(timestampField) +
            "\":" +
            uint64ToString(
                timestamp
            );
    }

    json +=
        ",\"deviceId\":\"" +
        String(DEVICE_ID) +
        "\"";

    json += "}";

    return json;
}

void printFirebaseError(
    const String &operation
)
{
    Serial.print(
        "Firebase error while "
    );
    Serial.print(operation);
    Serial.print(": ");
    Serial.print(
        aClient.lastError().message()
    );
    Serial.print(" (code ");
    Serial.print(
        aClient.lastError().code()
    );
    Serial.println(")");
}
