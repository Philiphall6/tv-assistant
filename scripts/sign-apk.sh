#!/usr/bin/env bash
# Passwords are read from protected files outside the checkout, never arguments or source.
set -euo pipefail
source "$(dirname "$0")/build-env.sh"
[[ $# == 2 ]] || { echo "Usage: $0 unsigned.apk signed.apk" >&2; exit 1; }
: "${TVA_SIGNING_KEYSTORE:?Set the private keystore path outside the repository}"
: "${TVA_SIGNING_ALIAS:?Set the signing alias}"
: "${TVA_STORE_PASSWORD_FILE:?Set a protected password file outside the repository}"
: "${TVA_KEY_PASSWORD_FILE:?Set a protected password file outside the repository}"
for file in "$TVA_SIGNING_KEYSTORE" "$TVA_STORE_PASSWORD_FILE" "$TVA_KEY_PASSWORD_FILE"; do
  test -f "$file" || { echo "Signing input missing" >&2; exit 1; }
  case "$(realpath "$file")" in "$TVA_ROOT"/*) echo "Signing material must stay outside the checkout" >&2; exit 1;; esac
  [[ "$(stat -c %a "$file")" =~ ^[46]00$ ]] || { echo "Signing input must have mode 600 or 400" >&2; exit 1; }
done
[[ "$(realpath -m "$1")" != "$(realpath -m "$2")" ]] || { echo "Keep the unsigned artifact separate" >&2; exit 1; }
test ! -e "$2" || { echo "Output already exists; choose a new output path" >&2; exit 1; }
TVA_KEY_ARGS=()
# apksigner consumes successive lines when both passwords use the same file.
# Omitting --key-pass makes it reuse the store password, as intended for PKCS12.
if [[ "$(realpath "$TVA_STORE_PASSWORD_FILE")" != "$(realpath "$TVA_KEY_PASSWORD_FILE")" ]]; then
  TVA_KEY_ARGS=(--key-pass "file:$TVA_KEY_PASSWORD_FILE")
fi
"$TVA_TOOLS/apksigner" sign --ks "$TVA_SIGNING_KEYSTORE" --ks-key-alias "$TVA_SIGNING_ALIAS" --ks-pass "file:$TVA_STORE_PASSWORD_FILE" "${TVA_KEY_ARGS[@]}" --out "$2" "$1"
"$TVA_TOOLS/apksigner" verify --verbose --print-certs "$2"
