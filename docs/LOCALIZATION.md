# Localization

TV Assistant follows Android's configured locale on the TV and the current HA user's locale in the editor. French and English are included; English is the fallback. There is no global language setting in a shared TV profile: two TVs and two HA users can use different languages with the same configuration.

## Translation files

| Purpose | Source |
| --- | --- |
| Launcher and editor labels | `localization/en.json`, with stable French source labels as keys |
| Android resources | Generated `app/src/main/res/values/ui.xml` and `values-fr/ui.xml` |
| HA editor catalog | Generated `custom_components/home_assistant_tv/ui-catalog.js` |
| HA integration setup | `strings.json` in English and `translations/en.json`, `translations/fr.json` |

Run the generator after editing the shared catalog:

```sh
python3 scripts/generate-localization.py
python3 scripts/generate-localization.py --check
python3 tests/test_localization_catalog.py
```

Android uses native resource selection. Built-in strings call `I18n.t`; never pass user-entered text to it. Configuration changes recreate activities through Android's normal lifecycle. HA uses `hass.locale.language`, then `hass.language`, then the document/browser language. Regional variants such as `fr-CA` select French.

The panel translates labels only at rendering boundaries. Mark user-provided display text with `userText` and never translate input values, YAML, entity IDs or profile keys. The `t` helper translates labels, while `f` substitutes named parameters without translating their values. Language switching must retain unsent drafts.

Voice language `auto` follows Android's actual language, even when the launcher UI falls back to English. An explicitly saved voice language is preserved. Lovelace cards remain responsible for their own HA translations. Some backend validation and third-party diagnostics retain their original language.

## Add another language

Add a reviewed catalog, update the generator and language resolver, add Android resources and HA setup translations, and extend the locale tests. Do not advertise a language solely because the platform supports its locale code. Preserve placeholders such as `{greeting}`, `{state}` and `{query}`.

Tests cover regional language selection, fallback, runtime switching, unchanged custom text and draft preservation. Browser tests run without a real HA account; native tests use the isolated audit APK.
