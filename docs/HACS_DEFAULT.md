# HACS catalog and validation

TV Assistant was accepted into the **default HACS catalog** on November 3, 2026: [merged submission #13217](https://github.com/hacs/default/pull/13217). Search for **TV Assistant** directly in HACS. If it does not appear in your search yet, the [custom repository fallback](README.en.md#installation) remains available.

The repository follows the [official HACS inclusion requirements](https://www.hacs.xyz/docs/publish/include/). It provides the integration manifest, HACS manifest, config flow, translations, brand icon, GPL license, installation documentation and dedicated HACS/Hassfest validation jobs. Catalog inclusion does not make TV Assistant an official Home Assistant product.

## Before future releases

1. Open the [Validate workflow](https://github.com/Philiphall6/tv-assistant/actions/workflows/validate.yml). Confirm that both **HACS** and **Hassfest** passed on the candidate commit, without ignored checks.
2. Confirm the integration/browser tests and the [Android tests](https://github.com/Philiphall6/tv-assistant/actions/workflows/android.yml) passed. Review public files, release assets and signing-certificate continuity.
3. Create a normal, non-draft release **after** the successful validation runs. Keep installation instructions and release notes up to date.

The repository must remain public and continue to meet HACS requirements. Catalog membership is managed by HACS maintainers.

## Integration and APK

The stable integration domain is `home_assistant_tv`. The Android APK is distributed separately in GitHub Releases; HACS does not install it on TVs.
