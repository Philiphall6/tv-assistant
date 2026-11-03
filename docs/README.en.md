# TV Assistant

[Français](../README.md) · [Releases and signed APK](https://github.com/Philiphall6/tv-assistant/releases) · [Security](../SECURITY.md)

TV Assistant combines a customizable Android TV launcher with a multi-TV editor inside Home Assistant. It is an independent community project, not an official Home Assistant or Google product.

## Preview

![TV home screen with wallpaper, heat pump card, app favorites and home controls](images/tv-home.jpg)

![Home Assistant editor with a real TV preview and movable elements](images/ha-editor.jpg)

These owner-approved screenshots show an earlier version called Home Assistant TV. File metadata has been removed. Your apps, cards and labels depend on your configuration.

## Requirements

| Component | Requirement |
| --- | --- |
| TV | Android 11–16, API 30–36 |
| Home Assistant | 2026.10.4 or newer |
| Connection | HTTPS with a valid certificate |
| Lovelace cards | Current Android System WebView and required HA card extensions |

Android 9 and 10 are not supported. Default-home behavior varies by manufacturer. The launcher never disables the system launcher automatically.

## Installation

1. In HACS, add `https://github.com/Philiphall6/tv-assistant` as a custom **Integration** repository. Default-catalog inclusion is not yet announced.
2. Download the integration, restart HA at a suitable time, then add **TV Assistant** in **Settings → Devices & services**.
3. Install the signed APK from [Releases](https://github.com/Philiphall6/tv-assistant/releases) on the TV. HACS installs the integration, **not the APK**.
4. Create a dedicated, non-administrator HA user for the TV and a long-lived access token. Enter the HTTPS address and token only in the TV's local settings.
5. Enable editing from HA on the TV. In your administrator account, open **TV Assistant**, create a profile and explicitly assign it to the TV after checking its identity.

New TVs cannot read a profile before an administrator assigns one. One profile can serve multiple TVs, or each TV can have a separate profile. See the [installation and rollback guide](INSTALLATION.md) for default-home setup and optional permissions.

## Features and limits

- Favorite apps, configurable icon shapes, long-press menus and resume content when apps provide it.
- Adjustable navigation position, size, tabs, colors, clock, status and voice search.
- Native cards, visual and YAML editing, free placement and shared or individual TV profiles.
- Real Lovelace cards in a persistent WebView, with remote-pointer interaction and no periodic page reload.
- Solid colors, a local image or a cached slideshow from a shared Google Photos album.

The real preview requires the launcher to be visible on the TV. Otherwise, a placement plan remains available. Not every custom HA card supports TV remotes. Google Photos synchronization uses the public album page format, which Google can change. Treat shared album URLs as private data.

## Automatic language

The TV follows Android's language; the HA panel follows the current user's Home Assistant language. French and English are included, with English as fallback. Switching languages never translates or rewrites custom names, profiles, entity IDs or YAML.

Dates, numbers and the 12/24-hour clock follow local settings. Voice recognition uses Android's language when set to `auto`; an explicit language remains unchanged. Lovelace cards use HA and their extensions' translations. Some technical diagnostics may retain their original language.

## Updates, security and contributions

Install updates over the existing APK when the signing certificate matches. Uninstalling erases local settings and credentials. Tokens are encrypted with Android Keystore and excluded from Android backups. The stable package ID is `fr.maison.tvlauncher`; the integration domain is `home_assistant_tv`.

See [build and signing](BUILD.md), [tests](TESTING.md), [contributions](../CONTRIBUTING.md), [translations](LOCALIZATION.md) and [HACS catalog preparation](HACS_DEFAULT.md). Report vulnerabilities using [private reporting](https://github.com/Philiphall6/tv-assistant/security/advisories/new), never a public issue containing credentials.

Original code is licensed under [GPL-3.0-only](../LICENSE). [Third-party licenses and trademarks](../THIRD_PARTY.md) remain separate.
