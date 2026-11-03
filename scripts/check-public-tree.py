"""Conservative tracked-file gate. Reports paths/categories, never matched secret values.

Not a substitute for a secret scanner and manual review of the actual publication.
"""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
names = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
patterns = {
    "private key": rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----",
    "JWT credential": rb"eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}",
    "provider token": rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[A-Z0-9]{16})",
    "private machine path": rb"(?:/home/[A-Za-z0-9_.-]+/|/Users/[A-Za-z0-9_.-]+/|[A-Z]:\\Users\\[^\\\s]+\\)",
    "personal DDNS": rb"https?://[^/\s\"']+\.(?:duckdns\.org|no-ip\.org)",
}
bad = []
for name in filter(None, names):
    path = root / name
    if path.is_symlink():
        bad.append((name, "symlink")); continue
    if path.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx", ".pem", ".apk", ".log", ".pyc"} or any(p in {".storage", "__pycache__", "release", "build", ".env"} for p in path.parts):
        bad.append((name, "private/generated file"))
    data = path.read_bytes()
    for label, pattern in patterns.items():
        if re.search(pattern, data):
            bad.append((name, label))
if bad:
    for name, label in bad:
        print(f"BLOCKED: {name}: {label}")
    sys.exit(1)
print(f"PASS: public-tree gate ({len(list(filter(None, names)))} tracked files); manual privacy review still required")
