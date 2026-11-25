export type DeviceSetup = {deviceId:string; email:string; password:string};

export function downloadDeviceSetup(setup: DeviceSetup, apiKey: string) {
  const header = [
    '#pragma once',
    `#define CRAYFARM_DEVICE_ID ${JSON.stringify(setup.deviceId)}`,
    `#define CRAYFARM_FIREBASE_API_KEY ${JSON.stringify(apiKey)}`,
    `#define CRAYFARM_FIREBASE_USER_EMAIL ${JSON.stringify(setup.email)}`,
    `#define CRAYFARM_FIREBASE_USER_PASSWORD ${JSON.stringify(setup.password)}`,
    '',
  ].join('\n');
  const url = URL.createObjectURL(new Blob([header], {type:'text/plain;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'firebase_credentials.h';
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
