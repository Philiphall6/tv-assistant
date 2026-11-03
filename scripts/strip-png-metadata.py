"""Remove ancillary metadata only; retain compressed pixels byte-for-byte.

No image decoding, visual editing, resizing or re-encoding is performed.
"""
from pathlib import Path
import struct
import sys

root = Path(__file__).resolve().parents[1]
for argument in sys.argv[1:]:
    path = Path(argument).resolve()
    if not path.is_relative_to(root) or path.suffix.lower() != '.png':
        raise SystemExit('Only PNG assets inside this checkout may be processed')
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit('Invalid PNG')
    kept = [data[:8]]
    offset = 8
    while offset < len(data):
        length = struct.unpack('>I', data[offset:offset+4])[0]
        end = offset + 12 + length
        if end > len(data):
            raise SystemExit('Truncated PNG')
        kind = data[offset+4:offset+8]
        # Preserve image structure/transparency/color information, remove provenance/text.
        if kind in {b'IHDR', b'IDAT', b'IEND', b'PLTE', b'tRNS', b'gAMA', b'cHRM', b'sRGB', b'iCCP'}:
            kept.append(data[offset:end])
        offset = end
    result = b''.join(kept)
    if result != data:
        path.write_bytes(result)
        print('Ancillary metadata removed:', path.relative_to(root))
