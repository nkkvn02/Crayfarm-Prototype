/**
 * CrayFarm ESP32
 *
 * Real sensors:
 * - DS18B20 temperature sensor on GPIO 4
 * - Analog turbidity sensor on GPIO 34
 *
 * Turbidity is uploaded as a calibrated relative percentage:
 * - 0%   = clear-water reference
 * - 100% = cloudy-water reference
 *
 * Sensors not yet connected:
 * - pH
 * - TDS
 * - Water level
 *
 * These unconnected sensors are NOT uploaded to Firebase.
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

const char *DEVICE_ID = "CRAY-ESP32-001";

#define ENABLE_DATABASE
#define ENABLE_USER_AUTH

#include <WiFi.h>
#include <FirebaseClient.h>
#include "ExampleFunctions.h"

#include <OneWire.h>
#include <DallasTemperature.h>
#include <time.h>

#define WIFI_SSID "WIFI NAME"
#define WIFI_PASSWORD "WIFI PASS"

#define API_KEY "API KEY"
#define USER_EMAIL "REDACTED"
#define USER_PASSWORD "REDACTED"

#define DATABASE_URL \
    "https://crayfarm-eb2ea-default-rtdb.asia-southeast1.firebasedatabase.app"

#define ONE_WIRE_BUS 4
#define TURBIDITY_PIN 34

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
    API_KEY,
    USER_EMAIL,
    USER_PASSWORD
);

FirebaseApp app;
RealtimeDatabase Database;

SSL_CLIENT ssl_client;

using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

String linkedFarmId = "";
String linkedPondId = "";
String latestPath = "";
String historyPath = "";

bool deviceLinked = false;
bool clockReady = false;

unsigned long lastLatestUpload = 0;
unsigned long lastHistoryUpload = 0;
unsigned long lastLinkCheck = 0;
unsigned long lastWiFiRetry = 0;

const unsigned long LATEST_UPLOAD_INTERVAL =
    5000UL;

const unsigned long HISTORY_UPLOAD_INTERVAL =
    15UL * 60UL * 1000UL;

const unsigned long LINK_CHECK_INTERVAL =
    10000UL;

const unsigned long WIFI_RETRY_INTERVAL =
    10000UL;

void connectWiFi();
void maintainWiFi();
void synchronizeClock();
bool loadDeviceAssignment();
void readSensors(
    float &temperature,
    bool &temperatureConnected,
    float &turbidity,
    bool &turbidityConnected
);
float readRelativeTurbidityPercent();
void uploadLatestReadings(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected
);
void uploadHistoryRecord(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected
);
uint64_t currentEpochMilliseconds();
String uint64ToString(uint64_t value);
String buildReadingJson(
    float temperature,
    bool temperatureConnected,
    float turbidity,
    bool turbidityConnected,
    uint64_t timestamp,
    const char *timestampField
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
    analogReadResolution(12);
    analogSetPinAttenuation(
        TURBIDITY_PIN,
        ADC_11db
    );

    connectWiFi();
    synchronizeClock();

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

    if (app.ready()) {
    Serial.println("Firebase authentication successful.");
    } else {
        Serial.println("Firebase authentication failed.");
    }

    app.getApp<RealtimeDatabase>(Database);
    Database.url(DATABASE_URL);

    Serial.println("Firebase initialized.");
}

void loop()
{
    maintainWiFi();

    app.loop();
    Database.loop();

    if (WiFi.status() != WL_CONNECTED)
    {
        return;
    }

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

    readSensors(
        temperature,
        temperatureConnected,
        turbidity,
        turbidityConnected
    );

    if (latestDue)
    {
        lastLatestUpload = now;

        uploadLatestReadings(
            temperature,
            temperatureConnected,
            turbidity,
            turbidityConnected
        );
    }

    if (historyDue && clockReady)
    {
        lastHistoryUpload = now;

        uploadHistoryRecord(
            temperature,
            temperatureConnected,
            turbidity,
            turbidityConnected
        );
    }
}

void connectWiFi()
{
    WiFi.mode(WIFI_STA);
    WiFi.begin(
        WIFI_SSID,
        WIFI_PASSWORD
    );

    Serial.print("Connecting to Wi-Fi");

    const unsigned long startedAt = millis();

    while (
        WiFi.status() != WL_CONNECTED &&
        millis() - startedAt < 20000UL
    )
    {
        Serial.print(".");
        delay(300);
    }

    Serial.println();

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.print("Connected. IP: ");
        Serial.println(WiFi.localIP());
    }
    else
    {
        Serial.println(
            "Wi-Fi connection timed out."
        );
    }
}

void maintainWiFi()
{
    if (WiFi.status() == WL_CONNECTED)
    {
        return;
    }

    const unsigned long now = millis();

    if (
        now - lastWiFiRetry <
        WIFI_RETRY_INTERVAL
    )
    {
        return;
    }

    lastWiFiRetry = now;

    Serial.println(
        "Wi-Fi disconnected. Reconnecting..."
    );

    WiFi.disconnect();
    WiFi.begin(
        WIFI_SSID,
        WIFI_PASSWORD
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
    bool &turbidityConnected
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

        // For the current hardware behavior, an unplugged turbidity
        // sensor is producing a calculated 0.0% reading. Treat that
        // condition as disconnected so the Android dashboard shows
        // "Sensor not connected".
        if (turbidity <= 0.0f)
        {
            turbidityConnected = false;
            turbidity = NAN;

            Serial.println(
                "Turbidity sensor: DISCONNECTED (0.0% detected)"
            );
            return;
        }

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

    Serial.println("pH: NOT CONNECTED");
    Serial.println("TDS: NOT CONNECTED");
    Serial.println("Water level: NOT CONNECTED");
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
    bool turbidityConnected
)
{
    const uint64_t timestamp =
        currentEpochMilliseconds();

    const String json = buildReadingJson(
        temperature,
        temperatureConnected,
        turbidity,
        turbidityConnected,
        timestamp,
        "updatedAt"
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
    bool turbidityConnected
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
        recordedAt,
        "recordedAt"
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
    uint64_t timestamp,
    const char *timestampField
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
        ",\"tdsConnected\":false"
        ",\"waterLevelConnected\":false";

    json +=
        ",\"" +
        String(timestampField) +
        "\":" +
        uint64ToString(
            timestamp
        );

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