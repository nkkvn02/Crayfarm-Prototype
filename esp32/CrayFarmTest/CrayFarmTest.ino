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
 * Temporary dummy values:
 * - pH
 * - TDS
 * - Water level
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

#define WIFI_SSID "wifi name"
#define WIFI_PASSWORD "wifi pass"

#define API_KEY "REDACTED_GOOGLE_API_KEY"
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
bool readSensors(
    float &temperature,
    float &ph,
    float &turbidity,
    float &tds,
    float &waterLevel
);
float readRelativeTurbidityPercent();
void uploadLatestReadings(
    float temperature,
    float ph,
    float turbidity,
    float tds,
    float waterLevel
);
void uploadHistoryRecord(
    float temperature,
    float ph,
    float turbidity,
    float tds,
    float waterLevel
);
uint64_t currentEpochMilliseconds();
String uint64ToString(uint64_t value);
String buildReadingJson(
    float temperature,
    float ph,
    float turbidity,
    float tds,
    float waterLevel,
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

    float temperature;
    float ph;
    float turbidity;
    float tds;
    float waterLevel;

    if (!readSensors(
            temperature,
            ph,
            turbidity,
            tds,
            waterLevel))
    {
        return;
    }

    if (latestDue)
    {
        lastLatestUpload = now;

        uploadLatestReadings(
            temperature,
            ph,
            turbidity,
            tds,
            waterLevel
        );
    }

    if (historyDue && clockReady)
    {
        lastHistoryUpload = now;

        uploadHistoryRecord(
            temperature,
            ph,
            turbidity,
            tds,
            waterLevel
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

bool readSensors(
    float &temperature,
    float &ph,
    float &turbidity,
    float &tds,
    float &waterLevel
)
{
    sensors.requestTemperatures();

    temperature =
        sensors.getTempCByIndex(0);

    if (
        temperature ==
        DEVICE_DISCONNECTED_C
    )
    {
        Serial.println(
            "DS18B20 was not detected."
        );
        return false;
    }

    turbidity =
        readRelativeTurbidityPercent();

    // Temporary dummy values.
    ph = 7.2;
    tds = 420.0;
    waterLevel = 81.0;

    Serial.println();
    Serial.println("Sensor readings");
    Serial.print("Temperature: ");
    Serial.print(temperature, 1);
    Serial.println(" C");

    Serial.print("Relative turbidity: ");
    Serial.print(turbidity, 1);
    Serial.println(" %");

    return true;
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
    float ph,
    float turbidity,
    float tds,
    float waterLevel
)
{
    const uint64_t timestamp =
        currentEpochMilliseconds();

    const String json = buildReadingJson(
        temperature,
        ph,
        turbidity,
        tds,
        waterLevel,
        timestamp,
        "updatedAt"
    );

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
    float ph,
    float turbidity,
    float tds,
    float waterLevel
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
        ph,
        turbidity,
        tds,
        waterLevel,
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
    float ph,
    float turbidity,
    float tds,
    float waterLevel,
    uint64_t timestamp,
    const char *timestampField
)
{
    return
        "{"
        "\"temperature\":" +
        String(temperature, 1) +
        ","
        "\"ph\":" +
        String(ph, 1) +
        ","
        "\"turbidity\":" +
        String(turbidity, 1) +
        ","
        "\"tds\":" +
        String(tds, 0) +
        ","
        "\"waterLevel\":" +
        String(waterLevel, 0) +
        ",\"" +
        String(timestampField) +
        "\":" +
        uint64ToString(timestamp) +
        ","
        "\"deviceId\":\"" +
        String(DEVICE_ID) +
        "\""
        "}";
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