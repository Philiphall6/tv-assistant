# Amazon Fire TV — experimental release

[Français](FIRE_TV.md) · [General installation](INSTALLATION.md)

**The project has not tested a physical Fire TV.** This adaptation is experimental, not Amazon certification or availability in the Amazon Appstore. Android emulator tests do not validate Fire OS.

Android TV and Fire TV running Fire OS use the same code and APK. No Amazon account or Google service dependency is added. Home Assistant profiles and the editor remain shared.

## Scope

| Device system | Status |
| --- | --- |
| Fire OS with Android API 30–36 | Community test target; not hardware-validated |
| Fire OS below API 30 | Unsupported; Android 11 remains the minimum |
| Vega OS | Not compatible with this APK; requires a separate port |

Do not rely on the Fire TV product name or Fire OS number alone. Check the exact generation and API level in [Amazon's device specifications](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html). Some Fire TV models run Vega OS instead of Android. See [Fire OS](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html) and [porting to Vega](https://developer.amazon.com/docs/adbt/port-fire-os-app-to-vega).

## Included adaptations

- Standard Android apps-list entry alongside the Android TV entry.
- Detection using the official `amazon.hardware.fire_tv` feature, without collecting device names, models or serial numbers.
- Fire TV setup guidance: app mode, Home limitations and no Google launcher disabling instructions on Fire TV.
- A system-search label without promising Google services or Alexa integration. Keyboard input remains available when Android transcription is missing.
- No request for continue-watching access when the Android TV provider is absent.
- Local diagnostics expose only `FIRE_TV` or `ANDROID`, API level and provider availability, not device identifiers.

**APK 2.13.9, beta 1:** [prerelease notes](https://github.com/Philiphall6/tv-assistant/releases/tag/firetv-2.13.9-beta.1) · [download the signed APK](https://github.com/Philiphall6/tv-assistant/releases/download/firetv-2.13.9-beta.1/TVAssistant-2.13.9.apk) · [SHA256SUMS](https://github.com/Philiphall6/tv-assistant/releases/download/firetv-2.13.9-beta.1/SHA256SUMS).

The prerelease retains the stable package ID and signing certificate. Its Android version code is `64`. APK 2.14.0 (code `65`) retains these adaptations and can replace this beta without uninstalling, but Fire TV support remains experimental. The built-in updater excludes prereleases. Notifications require HA integration 0.8.0 and overlay permission supported by the firmware; they have not been validated on physical Fire TV hardware.

Contributors can also follow the [build guide](BUILD.md); never distribute an unsigned APK or private signing key.

## Test installation

1. Confirm **Fire OS with API 30 or newer**, not Vega OS.
2. Download APK 2.13.9 and `SHA256SUMS` from this prerelease into the same folder. Check the file with `sha256sum -c SHA256SUMS` (Linux) or compare `Get-FileHash .\TVAssistant-2.13.9.apk -Algorithm SHA256` with `SHA256SUMS` (PowerShell). Keep the same signing certificate when updating, without uninstalling.
3. Follow [Amazon's ADB connection guide](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html) on a trusted local network and approve the computer on the TV. Never expose ADB to the Internet.
4. Install and open TV Assistant from the apps list. The following commands explicitly select the device; replace `FIRE_TV_ADDRESS` locally.

```sh
adb connect FIRE_TV_ADDRESS:5555
adb -s FIRE_TV_ADDRESS:5555 shell getprop ro.build.version.sdk
adb -s FIRE_TV_ADDRESS:5555 install -r ./TVAssistant-2.13.9.apk
adb -s FIRE_TV_ADDRESS:5555 shell am start -n fr.maison.tvlauncher/.MainActivity
```

5. Enter your HTTPS HA address and a dedicated non-admin user's token on the device. Approve its profile assignment in the HA editor using your administrator account, as on Android TV.
6. Disconnect ADB and disable debugging afterwards. Never include the device address in public feedback.

**Returning to stable:** the built-in updater keeps following stable releases. A newer future stable release can replace this beta while preserving settings. Android normally blocks a direct downgrade from 2.13.9 to 2.13.8; do not uninstall to force a downgrade unless you accept losing local settings.

## Limitations

- **Home:** this experiment targets app/dashboard mode first. Home may return to Amazon's home screen. Home replacement and automatic launch are not guaranteed. No Amazon component is disabled and no accessibility service intercepts Home.
- **Remote:** arrows, OK, Back and long press require hardware testing. Alexa, power, volume and system-reserved shortcuts are not intercepted. Amazon documents [Home and microphone as system events that cannot be intercepted](https://developer.amazon.com/docs/fire-tv/remote-input.html).
- **Voice:** Assist can accept typed text. Transcription requires a service handling Android speech intents; the Alexa button is not an Assist integration.
- **HA cards:** native cards and real Lovelace cards use different rendering paths. Lovelace requires a WebView supporting the secure message bridge. Security is not weakened if the engine lacks this feature.
- **Continue watching:** Amazon home-screen recommendations are not retrieved. Only content shared by an accessible Android TV provider can appear; empty rows are not populated with fabricated content.
- **Photos, APK installation and screensaver:** system pickers, permissions and firmware behavior also require testing; Google TV behavior cannot be assumed.

## Community validation

Use the [Fire TV test report form](https://github.com/Philiphall6/tv-assistant/issues/new?template=fire_tv_test.yml). Failed tests are useful too.

Check installation/opening, remote navigation and long press, app launching, HTTPS/token connection, HA profile linking, native cards, Lovelace cards, resume after sleep and preservation of settings after an update. Only test a home-control action when you intend to execute it and have selected a safe device.

Report only the **commercial model and generation**, Fire OS/API/APK/HA/WebView versions, reproduction steps and results. Never include tokens, addresses, serial numbers, TV identifiers, complete profiles, personal album links or raw logs. Review the built-in diagnostic before sharing and inspect/redact screenshots.

Until hardware results are documented, the status remains **experimental, not validated on Fire TV**.
