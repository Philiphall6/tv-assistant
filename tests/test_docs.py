"""Repository documentation checks; no network or private files are read."""
from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
FILES = [ROOT / 'README.md', ROOT / 'CONTRIBUTING.md', ROOT / 'SECURITY.md', *sorted((ROOT / 'docs').glob('*.md'))]


class DocumentationTests(unittest.TestCase):
    def test_structure_and_relative_links(self):
        for path in FILES:
            with self.subTest(file=path.name):
                text = path.read_text()
                self.assertTrue(text.startswith('# '))
                self.assertTrue(text.endswith('\n'))
                self.assertEqual(text.count('```') % 2, 0, 'Unclosed code fence')
                lines = text.splitlines()
                inside = False
                for index, line in enumerate(lines):
                    if line.startswith('```'):
                        inside = not inside
                    if not inside and re.match(r'^#{1,6} ', line):
                        self.assertTrue(index == 0 or not lines[index-1].strip(), f'{path.name}:{index+1}: blank line before heading')
                        self.assertTrue(index+1 == len(lines) or not lines[index+1].strip(), f'{path.name}:{index+1}: blank line after heading')
                without_code = re.sub(r'```[\s\S]*?```', '', text)
                for target in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', without_code):
                    if urlsplit(target).scheme or target.startswith('#'):
                        continue
                    relative = unquote(target.split('#')[0])
                    if relative:
                        self.assertTrue((path.parent / relative).exists(), f'{path.name}: missing {relative}')


if __name__ == '__main__':
    unittest.main()
