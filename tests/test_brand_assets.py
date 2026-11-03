"""Packaging brand checks with no third-party dependencies or network access."""
from pathlib import Path
import struct
import unittest
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "custom_components/home_assistant_tv/brand"
DRAWABLE = ROOT / "app/src/main/res/drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"


class BrandAssetTests(unittest.TestCase):
    def test_android_and_ha_use_identical_vector_paths(self):
        for svg, vector in [("icon.svg", "installation_icon.xml"), ("banner.svg", "tv_banner.xml")]:
            with self.subTest(asset=svg):
                source = ET.parse(BRAND / svg).getroot()
                target = ET.parse(DRAWABLE / vector).getroot()
                self.assertEqual(source.get("width"), target.get(ANDROID + "viewportWidth"))
                self.assertEqual(source.get("height"), target.get(ANDROID + "viewportHeight"))
                a, b = source.findall(SVG + "path"), target.findall("path")
                self.assertEqual(len(a), len(b))
                self.assertGreaterEqual(len(a), 8)
                for first, second in zip(a, b):
                    self.assertEqual(first.get("d"), second.get(ANDROID + "pathData"))
                    fill = "#00000000" if first.get("fill") == "none" else first.get("fill")
                    self.assertEqual(fill, second.get(ANDROID + "fillColor"))
                    self.assertEqual(first.get("stroke"), second.get(ANDROID + "strokeColor"))
                    self.assertEqual(first.get("stroke-width"), second.get(ANDROID + "strokeWidth"))
                    self.assertEqual(first.get("stroke-linecap"), second.get(ANDROID + "strokeLineCap"))

    def test_vectors_are_self_contained(self):
        for name in ["icon.svg", "logo.svg", "banner.svg"]:
            with self.subTest(asset=name):
                root = ET.parse(BRAND / name).getroot()
                self.assertEqual(root.find(SVG + "title").text, "TV Assistant")
                for element in root.iter():
                    self.assertIn(element.tag, {SVG + "svg", SVG + "path", SVG + "title"})
                    self.assertFalse(any("href" in key or "font" in key for key in element.attrib))

    def test_png_sizes_integrity_and_no_private_metadata(self):
        for name, width, height in [("icon", 256, 256), ("logo", 768, 192)]:
            for scale, suffix in [(1, ""), (2, "@2x")]:
                with self.subTest(asset=name + suffix):
                    data = (BRAND / f"{name}{suffix}.png").read_bytes()
                    self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                    offset, chunks = 8, []
                    while offset < len(data):
                        size, = struct.unpack_from(">I", data, offset)
                        kind = data[offset + 4:offset + 8]
                        payload = data[offset + 8:offset + 8 + size]
                        crc, = struct.unpack_from(">I", data, offset + 8 + size)
                        self.assertEqual(zlib.crc32(kind + payload), crc)
                        self.assertIn(kind, {b"IHDR", b"bKGD", b"IDAT", b"IEND"})
                        if kind == b"IHDR":
                            self.assertEqual(struct.unpack_from(">II", payload), (width * scale, height * scale))
                        chunks.append(kind)
                        offset += size + 12
                    self.assertEqual(offset, len(data))
                    self.assertEqual(chunks[0], b"IHDR")
                    self.assertIn(b"IDAT", chunks)
                    self.assertEqual(chunks[-1], b"IEND")

    def test_manifest_uses_installation_assets_only(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = root.find("application")
        self.assertEqual(app.get(ANDROID + "icon"), "@drawable/installation_icon")
        self.assertEqual(app.get(ANDROID + "banner"), "@drawable/tv_banner")
        self.assertTrue((DRAWABLE / "app_icon.xml").is_file())
        self.assertTrue((DRAWABLE / "ha_logo_accent.xml").is_file())


if __name__ == "__main__":
    unittest.main()
