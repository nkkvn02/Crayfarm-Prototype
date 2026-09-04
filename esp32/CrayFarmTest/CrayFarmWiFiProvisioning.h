#pragma once

#include <Arduino.h>
#include <DNSServer.h>
#include <Preferences.h>
#include <WebServer.h>
#include <WiFi.h>

class CrayFarmWiFiProvisioning
{
public:
    void begin(const char *deviceId, uint8_t resetPin = 0);
    bool connectOrProvision();
    bool maintain();
    void resetWiFiCredentials();

    const String &accessPointName() const;
    const String &accessPointPassword() const;

private:
    bool loadCredentials();
    bool saveCredentials(const String &ssid, const String &password);
    void clearCredentials();
    bool connectSavedWiFi(unsigned long timeoutMs);
    bool waitForConnection(unsigned long timeoutMs);
    bool startProvisioningPortal();
    void stopProvisioningPortal();
    void configurePortalRoutes();
    void handlePortalHome();
    void handlePortalSave();
    void redirectToPortal();
    void checkResetButton();
    String buildPortalPage(const String &message, bool isError);

    String deviceId_;
    String savedSsid_;
    String savedPassword_;
    String accessPointName_;
    String accessPointPassword_;
    String portalMessage_;

    uint8_t resetPin_ = 0;
    bool routesConfigured_ = false;
    bool portalRunning_ = false;
    bool restartScheduled_ = false;
    bool resetPressActive_ = false;
    bool wasConnected_ = false;

    unsigned long portalStartedAt_ = 0;
    unsigned long restartAt_ = 0;
    unsigned long resetPressedAt_ = 0;
    unsigned long disconnectedAt_ = 0;
    unsigned long lastReconnectAttempt_ = 0;

    DNSServer dnsServer_;
    WebServer webServer_{80};
};
