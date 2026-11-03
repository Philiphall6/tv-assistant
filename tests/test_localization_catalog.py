"""Check catalog integrity, generated resources and the shared profile contract."""
import json
import re
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def unique_pairs(pairs):
    value = {}
    for key, text in pairs:
        if key in value:
            raise ValueError(f"Duplicate translation key: {key}")
        value[key] = text
    return value


class LocalizationTests(unittest.TestCase):
    def test_unique_keys_and_parameters(self):
        catalog = json.loads((ROOT / 'localization/en.json').read_text(), object_pairs_hook=unique_pairs)
        for source, translation in catalog.items():
            with self.subTest(source=source):
                self.assertTrue(translation)
                self.assertEqual(sorted(re.findall(r'\{[a-z_]+\}', source)), sorted(re.findall(r'\{[a-z_]+\}', translation)))

    def test_android_resource_parity(self):
        def names(directory):
            return [s.attrib['name'] for s in ET.parse(ROOT / f'app/src/main/res/{directory}/ui.xml').getroot()]
        self.assertEqual(names('values'), names('values-fr'))
        self.assertEqual(len(names('values')), len(set(names('values'))))

    def test_ha_flow_translation_parity(self):
        component = ROOT / 'custom_components/home_assistant_tv'
        en = json.loads((component / 'translations/en.json').read_text())
        self.assertEqual(en, json.loads((component / 'strings.json').read_text()))
        def keys(value):
            return {key:keys(item) if isinstance(item,dict) else None for key,item in value.items()}
        self.assertEqual(keys(en), keys(json.loads((component / 'translations/fr.json').read_text())))

    def test_shared_schema_and_auto_voice(self):
        component = ROOT / 'custom_components/home_assistant_tv/schema.json'
        self.assertEqual(component.read_bytes(), (ROOT / 'app/src/main/res/raw/launcher_profile_schema.json').read_bytes())
        self.assertEqual(json.loads(component.read_text())['voice_language']['default'], 'auto')


if __name__ == '__main__':
    unittest.main()
