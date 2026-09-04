#include "CrayFarmWiFiProvisioning.h"

namespace
{
constexpr char PREFERENCES_NAMESPACE[] = "crayfarm_wifi";
constexpr unsigned long STARTUP_CONNECTION_TIMEOUT_MS = 20000UL;
constexpr unsigned long RECONNECT_INTERVAL_MS = 10000UL;
constexpr unsigned long RUNTIME_PORTAL_DELAY_MS = 2UL * 60UL * 1000UL;
constexpr unsigned long PORTAL_TIMEOUT_MS = 10UL * 60UL * 1000UL;
constexpr unsigned long RESET_HOLD_MS = 5000UL;
constexpr unsigned long RESTART_DELAY_MS = 2000UL;

String sanitizeSuffix(const String &deviceId)
{
    String compact;

    for (size_t index = 0; index < deviceId.length(); index++)
    {
        char value = deviceId.charAt(index);

        if (value >= 'a' && value <= 'z')
        {
            value = static_cast<char>(value - 'a' + 'A');
        }

        if (
            (value >= 'A' && value <= 'Z') ||
            (value >= '0' && value <= '9')
        )
        {
            compact += value;
        }
    }

    if (compact.length() > 6)
    {
        compact = compact.substring(compact.length() - 6);
    }

    if (compact.length() < 6)
    {
        const uint32_t chipSuffix = static_cast<uint32_t>(
            ESP.getEfuseMac() & 0xFFFFFF
        );

        compact = String(chipSuffix, HEX);
        compact.toUpperCase();

        while (compact.length() < 6)
        {
            compact = "0" + compact;
        }
    }

    return compact;
}

String escapeHtml(const String &value)
{
    String escaped;
    escaped.reserve(value.length() + 16);

    for (size_t index = 0; index < value.length(); index++)
    {
        switch (value.charAt(index))
        {
        case '&':
            escaped += "&amp;";
            break;
        case '<':
            escaped += "&lt;";
            break;
        case '>':
            escaped += "&gt;";
            break;
        case '"':
            escaped += "&quot;";
            break;
        case '\'':
            escaped += "&#39;";
            break;
        default:
            escaped += value.charAt(index);
            break;
        }
    }

    return escaped;
}

String signalLabel(int32_t rssi)
{
    if (rssi >= -55)
    {
        return "Excellent";
    }

    if (rssi >= -67)
    {
        return "Good";
    }

    if (rssi >= -75)
    {
        return "Fair";
    }

    return "Weak";
}
} // namespace

void CrayFarmWiFiProvisioning::begin(const char *deviceId, uint8_t resetPin)
{
    deviceId_ = deviceId == nullptr ? "CrayFarm" : String(deviceId);
    resetPin_ = resetPin;
    pinMode(resetPin_, INPUT_PULLUP);

    const String suffix = sanitizeSuffix(deviceId_);
    accessPointName_ = "CrayFarm-Setup-" + suffix;
    accessPointPassword_ = "CrayFarm-" + suffix;

    WiFi.setAutoReconnect(true);
    configurePortalRoutes();
    loadCredentials();
}

bool CrayFarmWiFiProvisioning::connectOrProvision()
{
    if (savedSsid_.isEmpty())
    {
        Serial.println("[WiFi] No saved credentials");
        return startProvisioningPortal();
    }

    Serial.println("[WiFi] Saved credentials found");

    if (connectSavedWiFi(STARTUP_CONNECTION_TIMEOUT_MS))
    {
        return true;
    }

    return startProvisioningPortal();
}

bool CrayFarmWiFiProvisioning::maintain()
{
    checkResetButton();

    if (WiFi.status() == WL_CONNECTED)
    {
        if (!wasConnected_)
        {
            Serial.println("[WiFi] Connected");
            Serial.print("[WiFi] IP: ");
            Serial.println(WiFi.localIP());
        }

        wasConnected_ = true;
        disconnectedAt_ = 0;
        return true;
    }

    wasConnected_ = false;

    if (savedSsid_.isEmpty())
    {
        startProvisioningPortal();
        return WiFi.status() == WL_CONNECTED;
    }

    const unsigned long now = millis();

    if (disconnectedAt_ == 0)
    {
        disconnectedAt_ = now;
    }

    if (
        lastReconnectAttempt_ == 0 ||
        now - lastReconnectAttempt_ >= RECONNECT_INTERVAL_MS
    )
    {
        lastReconnectAttempt_ = now;
        Serial.println("[WiFi] Reconnecting...");

        WiFi.mode(WIFI_STA);
        WiFi.begin(savedSsid_.c_str(), savedPassword_.c_str());
    }

    if (
        disconnectedAt_ != 0 &&
        now - disconnectedAt_ >= RUNTIME_PORTAL_DELAY_MS
    )
    {
        Serial.println("[WiFi] Saved network remains unavailable");
        startProvisioningPortal();
        disconnectedAt_ = millis();
        lastReconnectAttempt_ = 0;
    }

    return WiFi.status() == WL_CONNECTED;
}

void CrayFarmWiFiProvisioning::resetWiFiCredentials()
{
    clearCredentials();
    Serial.println("[WiFi] Credentials reset");
    Serial.println("[WiFi] Restarting setup mode");
    WiFi.disconnect(true, true);
    delay(250);
    ESP.restart();
}

const String &CrayFarmWiFiProvisioning::accessPointName() const
{
    return accessPointName_;
}

const String &CrayFarmWiFiProvisioning::accessPointPassword() const
{
    return accessPointPassword_;
}

bool CrayFarmWiFiProvisioning::loadCredentials()
{
    Preferences preferences;

    if (!preferences.begin(PREFERENCES_NAMESPACE, true))
    {
        savedSsid_ = "";
        savedPassword_ = "";
        return false;
    }

    savedSsid_ = preferences.getString("ssid", "");
    savedPassword_ = preferences.getString("password", "");
    preferences.end();
    return !savedSsid_.isEmpty();
}

bool CrayFarmWiFiProvisioning::saveCredentials(
    const String &ssid,
    const String &password
)
{
    Preferences preferences;

    if (!preferences.begin(PREFERENCES_NAMESPACE, false))
    {
        return false;
    }

    const bool ssidSaved = preferences.putString("ssid", ssid) > 0;
    preferences.putString("password", password);
    preferences.end();

    if (ssidSaved)
    {
        savedSsid_ = ssid;
        savedPassword_ = password;
    }

    return ssidSaved;
}

void CrayFarmWiFiProvisioning::clearCredentials()
{
    Preferences preferences;

    if (preferences.begin(PREFERENCES_NAMESPACE, false))
    {
        preferences.clear();
        preferences.end();
    }

    savedSsid_ = "";
    savedPassword_ = "";
}

bool CrayFarmWiFiProvisioning::connectSavedWiFi(unsigned long timeoutMs)
{
    if (savedSsid_.isEmpty())
    {
        return false;
    }

    WiFi.mode(WIFI_STA);
    WiFi.begin(savedSsid_.c_str(), savedPassword_.c_str());
    Serial.println("[WiFi] Connecting...");
    return waitForConnection(timeoutMs);
}

bool CrayFarmWiFiProvisioning::waitForConnection(unsigned long timeoutMs)
{
    const unsigned long startedAt = millis();

    while (
        WiFi.status() != WL_CONNECTED &&
        millis() - startedAt < timeoutMs
    )
    {
        checkResetButton();
        delay(100);
    }

    if (WiFi.status() != WL_CONNECTED)
    {
        Serial.println("[WiFi] Connection timeout");
        return false;
    }

    wasConnected_ = true;
    disconnectedAt_ = 0;
    Serial.println("[WiFi] Connected");
    Serial.print("[WiFi] IP: ");
    Serial.println(WiFi.localIP());
    return true;
}

bool CrayFarmWiFiProvisioning::startProvisioningPortal()
{
    if (portalRunning_)
    {
        return false;
    }

    Serial.println("[WiFi] Starting CrayFarm-Setup");
    WiFi.disconnect(false);
    WiFi.mode(WIFI_AP_STA);

    if (!WiFi.softAP(accessPointName_.c_str(), accessPointPassword_.c_str()))
    {
        Serial.println("[WiFi] Setup access point failed");
        WiFi.mode(WIFI_STA);
        return false;
    }

    const IPAddress portalAddress = WiFi.softAPIP();
    dnsServer_.start(53, "*", portalAddress);
    webServer_.begin();
    portalRunning_ = true;
    portalStartedAt_ = millis();
    restartScheduled_ = false;
    portalMessage_ = "";

    Serial.print("[WiFi] Setup network: ");
    Serial.println(accessPointName_);
    Serial.print("[WiFi] Setup page: http://");
    Serial.println(portalAddress);

    while (
        portalRunning_ &&
        millis() - portalStartedAt_ < PORTAL_TIMEOUT_MS
    )
    {
        checkResetButton();
        dnsServer_.processNextRequest();
        webServer_.handleClient();

        if (
            restartScheduled_ &&
            static_cast<long>(millis() - restartAt_) >= 0
        )
        {
            delay(100);
            ESP.restart();
        }

        delay(10);
    }

    if (portalRunning_)
    {
        Serial.println("[WiFi] Setup portal timed out");
    }

    stopProvisioningPortal();

    if (!savedSsid_.isEmpty())
    {
        WiFi.begin(savedSsid_.c_str(), savedPassword_.c_str());
    }

    return WiFi.status() == WL_CONNECTED;
}

void CrayFarmWiFiProvisioning::stopProvisioningPortal()
{
    dnsServer_.stop();
    webServer_.stop();
    WiFi.softAPdisconnect(true);
    WiFi.mode(WIFI_STA);
    portalRunning_ = false;
}

void CrayFarmWiFiProvisioning::configurePortalRoutes()
{
    if (routesConfigured_)
    {
        return;
    }

    webServer_.on("/", HTTP_GET, [this]() { handlePortalHome(); });
    webServer_.on("/save", HTTP_POST, [this]() { handlePortalSave(); });

    const char *probePaths[] = {
        "/generate_204",
        "/hotspot-detect.html",
        "/connecttest.txt",
        "/fwlink"
    };

    for (const char *path : probePaths)
    {
        webServer_.on(path, HTTP_ANY, [this]() { redirectToPortal(); });
    }

    webServer_.onNotFound([this]() { redirectToPortal(); });
    routesConfigured_ = true;
}

void CrayFarmWiFiProvisioning::handlePortalHome()
{
    webServer_.sendHeader("Cache-Control", "no-store");
    webServer_.send(
        200,
        "text/html",
        buildPortalPage(portalMessage_, !portalMessage_.isEmpty())
    );
}

void CrayFarmWiFiProvisioning::handlePortalSave()
{
    String ssid = webServer_.arg("manualSsid");
    ssid.trim();

    if (ssid.isEmpty())
    {
        ssid = webServer_.arg("network");
        ssid.trim();
    }

    const String password = webServer_.arg("password");

    if (ssid.isEmpty() || ssid.length() > 32)
    {
        portalMessage_ = "Enter or select a valid Wi-Fi network.";
        handlePortalHome();
        return;
    }

    if (
        !password.isEmpty() &&
        (password.length() < 8 || password.length() > 63)
    )
    {
        portalMessage_ = "The Wi-Fi password must contain 8 to 63 characters, or remain empty for an open network.";
        handlePortalHome();
        return;
    }

    Serial.println("[WiFi] Testing submitted network");
    WiFi.begin(ssid.c_str(), password.c_str());

    if (!waitForConnection(STARTUP_CONNECTION_TIMEOUT_MS))
    {
        WiFi.disconnect(false);
        portalMessage_ = "Connection failed. Check the network and password, then try again.";
        handlePortalHome();
        return;
    }

    if (!saveCredentials(ssid, password))
    {
        portalMessage_ = "Connected, but the ESP32 could not save the settings. Please try again.";
        handlePortalHome();
        return;
    }

    Serial.println("[WiFi] Credentials saved");

    const String successPage =
        "<!doctype html><html><head><meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<title>CrayFarm Wi-Fi Setup</title>"
        "<style>body{margin:0;background:#c5cdd9;font-family:Arial,sans-serif;color:#17204a;}"
        ".wrap{min-height:100vh;display:grid;place-items:center;padding:24px;box-sizing:border-box;}"
        ".card{width:min(420px,100%);background:#fff;border:1px solid #7d88a8;border-radius:20px;"
        "box-shadow:0 14px 34px rgba(10,30,95,.18);padding:30px;box-sizing:border-box;text-align:center;}"
        ".mark{width:52px;height:52px;border-radius:16px;background:#2639a6;color:#fff;"
        "display:grid;place-items:center;margin:0 auto 18px;font-size:26px;font-weight:700;}"
        "h1{font-size:24px;margin:0 0 10px;}p{line-height:1.55;margin:0;color:#536080;}</style>"
        "</head><body><main class='wrap'><section class='card'>"
        "<div class='mark'>&#10003;</div><h1>Wi-Fi settings saved</h1>"
        "<p>The ESP32 is connected and will restart. You can close this page and reconnect your phone to the farm Wi-Fi.</p>"
        "</section></main></body></html>";

    webServer_.sendHeader("Cache-Control", "no-store");
    webServer_.send(200, "text/html", successPage);
    restartScheduled_ = true;
    restartAt_ = millis() + RESTART_DELAY_MS;
}

void CrayFarmWiFiProvisioning::redirectToPortal()
{
    webServer_.sendHeader(
        "Location",
        "http://" + WiFi.softAPIP().toString() + "/",
        true
    );
    webServer_.send(302, "text/plain", "");
}

void CrayFarmWiFiProvisioning::checkResetButton()
{
    const bool pressed = digitalRead(resetPin_) == LOW;

    if (!pressed)
    {
        resetPressActive_ = false;
        resetPressedAt_ = 0;
        return;
    }

    if (!resetPressActive_)
    {
        resetPressActive_ = true;
        resetPressedAt_ = millis();
        Serial.println("[WiFi] BOOT held; keep holding to reset Wi-Fi");
        return;
    }

    if (millis() - resetPressedAt_ >= RESET_HOLD_MS)
    {
        resetWiFiCredentials();
    }
}

String CrayFarmWiFiProvisioning::buildPortalPage(
    const String &message,
    bool isError
)
{
    String options = "<option value=''>Select a detected network</option>";
    const int networkCount = WiFi.scanNetworks();

    for (int index = 0; index < networkCount; index++)
    {
        const String ssid = WiFi.SSID(index);

        if (ssid.isEmpty())
        {
            continue;
        }

        options += "<option value=\"";
        options += escapeHtml(ssid);
        options += "\">";
        options += escapeHtml(ssid);
        options += " - ";
        options += signalLabel(WiFi.RSSI(index));

        if (WiFi.encryptionType(index) == WIFI_AUTH_OPEN)
        {
            options += " (open)";
        }

        options += "</option>";
    }

    WiFi.scanDelete();

    String html;
    html.reserve(8500);
    html +=
        "<!doctype html><html><head><meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<title>CrayFarm Wi-Fi Setup</title><style>"
        "*{box-sizing:border-box}body{margin:0;background:linear-gradient(160deg,#c5cdd9,#eaf3ff);"
        "font-family:Arial,sans-serif;color:#17204a}.wrap{min-height:100vh;display:grid;place-items:center;padding:22px}"
        ".card{width:min(440px,100%);background:#fff;border:1px solid #7d88a8;border-radius:22px;"
        "box-shadow:0 16px 38px rgba(10,30,95,.18);overflow:hidden}.head{background:#2639a6;color:#fff;padding:24px}"
        ".brand{font-size:13px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;opacity:.92}"
        "h1{font-size:25px;margin:9px 0 7px}.head p{margin:0;line-height:1.5;color:#e7edff}.body{padding:24px}"
        "label{display:block;font-size:13px;font-weight:700;margin:0 0 8px}.field{margin-bottom:17px}"
        "select,input{width:100%;height:50px;border:1.5px solid #7d88a8;border-radius:12px;background:#fff;"
        "padding:0 13px;color:#17204a;font-size:15px;outline:none}select:focus,input:focus{border:2px solid #17266f;"
        "box-shadow:0 0 0 3px rgba(38,57,166,.12)}.hint{font-size:12px;color:#64708d;margin:7px 0 0;line-height:1.45}"
        ".error{background:#fff0f0;border:1px solid #e2a4a4;color:#9f2020;border-radius:10px;padding:11px 12px;"
        "font-size:13px;line-height:1.4;margin-bottom:17px}button{width:100%;height:50px;border:0;border-radius:12px;"
        "background:#07106a;color:#fff;font-size:15px;font-weight:700;cursor:pointer}button:active{transform:translateY(1px)}"
        ".foot{text-align:center;font-size:12px;color:#64708d;margin:17px 0 0;line-height:1.45}</style>"
        "</head><body><main class='wrap'><section class='card'>"
        "<header class='head'><div class='brand'>CrayFarm</div><h1>Wi-Fi setup</h1>"
        "<p>Connect this ESP32 to the farm's 2.4 GHz Wi-Fi network.</p></header><div class='body'>";

    if (!message.isEmpty())
    {
        html += isError ? "<div class='error'>" : "<div>";
        html += escapeHtml(message);
        html += "</div>";
    }

    html +=
        "<form method='post' action='/save'>"
        "<div class='field'><label for='network'>Detected network</label>"
        "<select id='network' name='network'>";
    html += options;
    html +=
        "</select><p class='hint'>Only 2.4 GHz Wi-Fi networks are supported.</p></div>"
        "<div class='field'><label for='manualSsid'>Hidden network name</label>"
        "<input id='manualSsid' name='manualSsid' maxlength='32' autocomplete='off' placeholder='Optional manual SSID'></div>"
        "<div class='field'><label for='password'>Wi-Fi password</label>"
        "<input id='password' name='password' type='password' maxlength='63' autocomplete='new-password' "
        "placeholder='Enter the network password'><p class='hint'>Leave this empty only for an open network.</p></div>"
        "<button type='submit'>Save and connect</button></form><p class='foot'>Device ";
    html += escapeHtml(deviceId_);
    html +=
        "<br>Your Wi-Fi password is stored only on this ESP32.</p>"
        "</div></section></main></body></html>";

    return html;
}
