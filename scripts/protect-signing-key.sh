#!/usr/bin/env bash
# Rewrap an existing signing key without changing its identity. Never edits the source.
set -euo pipefail
[[ $# == 5 ]] || { echo 'Usage: source-keystore alias store-password-file key-password-file NEW-private-directory' >&2; exit 1; }
TVA_SOURCE=$(realpath "$1")
TVA_ALIAS=$2
TVA_STORE_PASS=$(realpath "$3")
TVA_KEY_PASS=$(realpath "$4")
TVA_DEST=$(realpath -m "$5")
TVA_CHECKOUT=$(cd "$(dirname "$0")/.." && pwd)
for file in "$TVA_SOURCE" "$TVA_STORE_PASS" "$TVA_KEY_PASS"; do
  test -f "$file"
  case "$file" in "$TVA_CHECKOUT"/*) echo 'Private inputs must be outside the checkout' >&2; exit 1;; esac
  [[ "$(stat -c %a "$file")" =~ ^[46]00$ ]] || { echo 'Private inputs require mode 600 or 400' >&2; exit 1; }
done
case "$TVA_DEST" in "$TVA_CHECKOUT"|"$TVA_CHECKOUT"/*) echo 'Destination must be outside the checkout' >&2; exit 1;; esac
test ! -e "$TVA_DEST" || { echo 'Destination already exists' >&2; exit 1; }
umask 077
mkdir -m 700 "$TVA_DEST"
openssl rand -base64 -out "$TVA_DEST/password" 48
keytool -importkeystore -noprompt -srckeystore "$TVA_SOURCE" -srcalias "$TVA_ALIAS" \
  -srcstorepass:file "$TVA_STORE_PASS" -srckeypass:file "$TVA_KEY_PASS" \
  -destkeystore "$TVA_DEST/signing.p12" -deststoretype PKCS12 -destalias "$TVA_ALIAS" \
  -deststorepass:file "$TVA_DEST/password" -destkeypass:file "$TVA_DEST/password"
TVA_BEFORE=$(keytool -exportcert -keystore "$TVA_SOURCE" -alias "$TVA_ALIAS" -storepass:file "$TVA_STORE_PASS" | sha256sum | cut -d ' ' -f 1)
TVA_AFTER=$(keytool -exportcert -keystore "$TVA_DEST/signing.p12" -alias "$TVA_ALIAS" -storepass:file "$TVA_DEST/password" | sha256sum | cut -d ' ' -f 1)
[[ "$TVA_BEFORE" == "$TVA_AFTER" ]] || { echo 'STOP: certificate mismatch; do not distribute' >&2; exit 1; }
printf 'Certificate preserved: %s\nPrivate files created outside repository. Back them up securely.\n' "$TVA_AFTER"
