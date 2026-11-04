# TV Assistant

![TV Assistant logo: a television with a connected branch](../custom_components/home_assistant_tv/brand/logo.png)

[Français](../README.md) · [Releases and signed APK](https://github.com/Philiphall6/tv-assistant/releases) · [Security](../SECURITY.md)

[![HACS Default](https://img.shields.io/badge/HACS-Default-blue)](https://my.home-assistant.io/redirect/hacs_repository/?owner=Philiphall6&repository=tv-assistant&category=integration)
[![Latest release](https://img.shields.io/github/v/release/Philiphall6/tv-assistant?color=blue)](https://github.com/Philiphall6/tv-assistant/releases/latest)

TV Assistant combines a customizable Android TV launcher with a multi-TV editor inside Home Assistant. It is an independent community project, not an official Home Assistant or Google product.

[![Install TV Assistant with HACS](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=Philiphall6&repository=tv-assistant&category=integration)

## Preview

### TV home screen

![TV Assistant home screen with living room wallpaper, weather, temperature, heat pump, favorite apps and lights](images/tv-home.jpg)

An example of a personalized layout: home information, favorite apps and controls share the same screen.

### Home Assistant editor

![Home Assistant editor with a real TV capture, placement outlines and favorite apps layout settings](images/ha-editor.jpg)

Move and resize elements from Home Assistant. The real preview shows the layout applied on the TV; changes remain a draft until sent.

<details>
<summary>More layouts and customization settings</summary>

### TV Assistant wallpaper and bottom navigation

![Home screen with TV Assistant wallpaper and a compact bottom navigation bar](images/tv-home-bottom-navigation.jpg)

### Navigation position and width

![Navigation settings in Home Assistant with a preview of the bar at the bottom of the screen](images/ha-navigation-editor.jpg)

### App icon shapes

![Appearance settings with icon shape choices and a preview of the apps](images/ha-appearance-editor.jpg)

</details>

These owner-approved screenshots show a personalized example, not the default configuration. Labels, apps, cards and displayed values depend on your setup. File metadata has been removed; no profile configuration or entity IDs are distributed with these images.

## Requirements

### Optional Home Assistant notifications

Integration **0.8.0** and APK **2.14.0** add text banners over other applications. Enable reception locally in **Settings → Home Assistant notifications** and grant Android overlay permission. The HA editor cannot grant this consent remotely.

With integration **0.8.1**, click **Test notification** on a TV card in **TV Assistant → TVs** to send an 8-second test to that TV only. The result distinguishes socket delivery from a skipped test; it never wakes the TV or retries automatically. APK **2.14.0** is unchanged.

Each approved TV gets a device and a **Notifications** entity, also listed on its TV card. In an HA automation, choose **Notifications: Send a message**, select that TV's entity and enter a message and optional title. This uses the standard `notify.send_message` action (not an iPhone-style `notify.mobile_app_…` action), with an 8-second banner in the top-right corner. The entity becomes unavailable while reception is paused or disconnected; nothing is queued. Its state is the last send time via this action, not a display acknowledgement; message text is never stored in its attributes. Renaming keeps the entity identity; forgetting a TV removes its entity, and reconnection requires approval again. TVs sharing a profile remain separate targets.

For advanced options, expand the notification instructions for an example using your TV Assistant identifier. Use `home_assistant_tv.notify` with `device_ids`, `message`, optional `title`, `duration` (3–60 seconds), `position` (one of the four corners) and `tag`. Use `home_assistant_tv.dismiss_notification` with the same `device_ids` and `tag` to remove a banner. Titles are limited to 80 characters and messages to 600; long text may be truncated on screen. One banner at a time, at most one send per second per TV, no queue.

No notification while asleep, locked or in the screensaver, no wake-up and no missed-message replay on wake or reconnect. Banners do not take remote focus. Reception is owner-bound; administrators and trusted HA automations can send to approved TVs. The `sent` response means socket delivery, not confirmed display. TV Assistant stores no message history; HA automation traces can still contain action text.

The optional receiver uses an Android foreground service with a discreet indicator. Open the launcher after an Android reboot or force-stop to start reception again. Vendors and protected apps may block overlays or stop the service, so this is not a guaranteed critical-alert channel. Text only in this release; no images, buttons or conditional native cards. Settings are reorganized by function without resetting existing values. See the [French setup guide and YAML examples](INSTALLATION.md#notifications-home-assistant).

### Platform compatibility

| Component | Requirement |
| --- | --- |
| TV | Android 11–16, API 30–36 |
| Home Assistant | 2026.10.4 or newer |
| Connection | HTTPS with a valid certificate |
| Lovelace cards | Current Android System WebView and required HA card extensions |

Android 9 and 10 are not supported. Default-home behavior varies by manufacturer. The launcher never disables the system launcher automatically.

**Amazon Fire TV:** the adaptations from [experimental APK 2.13.9 — beta 1](https://github.com/Philiphall6/tv-assistant/releases/tag/firetv-2.13.9-beta.1) are retained in APK 2.14.0, but Fire OS remains experimental (Android API 30 or newer). Read the [guide and limitations](FIRE_TV.en.md) before installing. No physical Fire TV has been validated. Vega OS is excluded; Amazon Home replacement and overlays are not guaranteed.

## Installation

TV Assistant is available in the **default HACS catalog**. [HACS must already be installed and configured](https://www.hacs.xyz/docs/use/).

1. Click [**Install with HACS**](https://my.home-assistant.io/redirect/hacs_repository/?owner=Philiphall6&repository=tv-assistant&category=integration), or search for **TV Assistant** directly in HACS. The button opens its repository page in your Home Assistant; if prompted, enter your own instance address in My Home Assistant.
2. Download the integration, restart HA at a suitable time, then add **TV Assistant** in **Settings → Devices & services**.
3. Install the signed APK from [Releases](https://github.com/Philiphall6/tv-assistant/releases) on the TV. HACS installs the integration, **not the APK**.
4. Create a dedicated, non-administrator HA user for the TV and a long-lived access token. Enter the HTTPS address and token only in the TV's local settings.
5. Enable editing from HA on the TV. In your administrator account, open **TV Assistant**, create a profile and explicitly assign it to the TV after checking its identity.

**Fallback:** if TV Assistant does not appear in your HACS search yet, open the **Custom repositories** menu, add `https://github.com/Philiphall6/tv-assistant` with type **Integration**, then continue from step 2.

New TVs cannot read a profile before an administrator assigns one. One profile can serve multiple TVs, or each TV can have a separate profile. See the [installation and rollback guide](INSTALLATION.md) for default-home setup and optional permissions.

The button does not install anything automatically: you still confirm the download and restart. It contains no personal address or token.

### Connection status and removal

HA data access and the TV Assistant editor are separate connections. In the TV's **Edit from Home Assistant** settings, both statuses, the last check time and the next step are displayed. Enable linking on the TV, return to its home screen, then use your administrator account in **TV Assistant → TVs** to compare the identifier and assign a saved profile. An offline preview can also mean the TV is asleep, displaying another app or showing its settings.

**Delete profile** requires confirmation and refuses profiles still assigned to a TV, including an offline TV. **Remove connection** forgets that pairing and stops its profile/card streams; it preserves the profile, other TVs and the TV's local display. It does not revoke the HA token. Disable linking on the TV first for permanent removal; otherwise it may reappear on reconnect and will require fresh administrator approval. Revoke the token separately in the HA account's security settings if its HA access must also end.

## Features and limits

- Favorite apps, configurable icon shapes, long-press menus and resume content with artwork and progress when apps provide it.
- Remote shortcuts and local button-to-app assignments, subject to manufacturer restrictions.
- Adjustable navigation position, size, tabs, colors, clock, status and voice search.
- Native cards, visual and YAML editing, free placement and shared or individual TV profiles.
- Real Lovelace cards in a persistent WebView, with remote-pointer interaction and no periodic page reload.
- Solid colors, a local image or a cached slideshow from a shared Google Photos album.
- The idle launcher and HA dashboard allow the system screensaver without changing Google albums or the TV's timeout settings.
- Manual APK update checks, optional daily checks (off by default), and privacy-safe technical diagnostics previewed before sharing.
- Built-in TV Assistant wallpaper on new installations, without replacing existing wallpaper choices.
- Separate Apps tab settings for icon and label size, apps per row and visible rows; home favourites remain unchanged.

The real preview requires the launcher to be visible on the TV. Otherwise, a placement plan remains available. Not every custom HA card supports TV remotes. Google Photos synchronization uses the public album page format, which Google can change. Treat shared album URLs as private data.

## Automatic language

The TV follows Android's language; the HA panel follows the current user's Home Assistant language. French and English are included, with English as fallback. Switching languages never translates or rewrites custom names, profiles, entity IDs or YAML.

Dates, numbers and the 12/24-hour clock follow local settings. Voice recognition uses Android's language when set to `auto`; an explicit language remains unchanged. Lovelace cards use HA and their extensions' translations. Some technical diagnostics may retain their original language.

## Updates, security and contributions

Open **Settings → Updates and diagnostics** to check the latest stable release and open its official page. The APK version is compared, not the HACS version. No APK is automatically downloaded or installed. Optional daily checks run when returning to the home screen and never wake the TV. GitHub receives your connection's IP address but no HA token or TV identifier.

Starting with APK **2.13.8**, the GitHub distribution also offers **Download the update from GitHub**. Confirm the download, wait for checksum/package/version/certificate checks, then choose **Install the downloaded update**. If prompted, allow TV Assistant as an installation source in Android settings, return to the launcher, and choose Install again. Android requires confirmation and performs its final signature check. You may revoke the installation-source permission afterwards. Settings are preserved without uninstalling. Installing this feature for the first time still requires an APK transfer or ADB; subsequent updates need no PC or TV browser. A future Play Store build must use Play updates instead.

The diagnostic uses a closed list of technical facts, generated locally. Its exact contents are displayed before you choose Share, Save or Copy. There are no raw logs, names, identifiers, addresses, credentials, profiles, photos or playback history, and nothing is uploaded automatically. HA status is the last observed on the home screen, not a new connection test.

Adjust the Apps tab in **Apps and favourites → Apps layout** on the TV, or **Rows** in the HA editor. Large icons are bounded to their cells: use fewer rows or columns to allow larger sizes. Existing installations can select **TV Assistant** in Wallpaper settings.

Install updates over the existing APK when the signing certificate matches. Uninstalling erases local settings and credentials. Tokens are encrypted with Android Keystore and excluded from Android backups. The stable package ID is `fr.maison.tvlauncher`; the integration domain is `home_assistant_tv`.

See [build and signing](BUILD.md), [tests](TESTING.md), [contributions](../CONTRIBUTING.md), [translations](LOCALIZATION.md) and [HACS catalog and validation](HACS_DEFAULT.md). Report vulnerabilities using [private reporting](https://github.com/Philiphall6/tv-assistant/security/advisories/new), never a public issue containing credentials.

Original code is licensed under [GPL-3.0-only](../LICENSE). [Third-party licenses and trademarks](../THIRD_PARTY.md) remain separate.
