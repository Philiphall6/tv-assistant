# Contributing to TV Assistant

Contributions should preserve existing profiles, remote navigation, user privacy and compatibility with Android 11–16. Use a separate branch and explain the behavior being changed.

## Report a problem

Include APK, integration, Android, Home Assistant and WebView versions, steps to reproduce, and expected versus actual behavior. Do not include tokens, passwords, server addresses, device serial numbers, personal album links or unredacted logs.

Report vulnerabilities through [private security reporting](https://github.com/Philiphall6/tv-assistant/security/advisories/new), not a public issue.

## Make and verify a change

1. Read the [build guide](docs/BUILD.md) and [test guide](docs/TESTING.md).
2. Use synthetic data and isolated emulator packages. Do not run tests against a personal HA instance or execute real home-control actions.
3. Preserve custom text, entity IDs, YAML and existing profile keys. Keep the Android and HA schema files identical.
4. Run the affected tests, check documentation links and explain any untested cases in the pull request.

For translations, use the [localization guide](docs/LOCALIZATION.md). Do not put private signing keys or credentials in the checkout, CI variables printed in logs, screenshots or release assets. Release signing stays outside ordinary CI.

Contributions must be compatible with GPL-3.0-only. Preserve third-party notices and do not imply official affiliation with Home Assistant or Google.
