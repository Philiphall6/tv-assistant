"""Import an explicitly approved screenshot, stripping metadata without re-encoding.

JPEG scan bytes and color profiles are preserved. EXIF, XMP, IPTC, comments and
other application metadata are not copied. The source is never modified.
"""
from pathlib import Path
import sys

source, destination = map(Path, sys.argv[1:])
root = Path(__file__).resolve().parents[1] / "docs/images"
if not destination.resolve().is_relative_to(root) or destination.suffix != ".jpg":
    raise SystemExit("Destination must be a JPEG under docs/images")
data = source.read_bytes()
if not data.startswith(b"\xff\xd8"):
    raise SystemExit("Not a JPEG")
kept, offset = [data[:2]], 2
while offset < len(data):
    start = offset
    if data[offset] != 255:
        raise SystemExit("Invalid JPEG marker")
    while data[offset] == 255:
        offset += 1
    marker = data[offset]
    offset += 1
    if marker == 0xda:  # Start of scan: do not decode or change the compressed pixels.
        kept.append(data[start:])
        break
    length = int.from_bytes(data[offset:offset+2], "big")
    end = offset + length
    if length < 2 or end > len(data):
        raise SystemExit("Invalid JPEG segment")
    payload = data[offset+2:end]
    keep = not (0xe0 <= marker <= 0xef or marker == 0xfe)
    keep |= marker == 0xe0 and payload.startswith(b"JFIF\x00")
    keep |= marker == 0xe2 and payload.startswith(b"ICC_PROFILE\x00")
    keep |= marker == 0xee and payload.startswith(b"Adobe")
    if keep:
        kept.append(data[start:end])
    offset = end
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(b"".join(kept))
print("Imported approved screenshot:", destination.name)
