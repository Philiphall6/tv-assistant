#!/usr/bin/env bash
# Only the .audit package is installed. No production settings or HA credentials are used.
set -euo pipefail
source "$(dirname "$0")/../scripts/build-env.sh"
: "${ANDROID_SERIAL:?Choose an isolated emulator using ANDROID_SERIAL}"
[[ "$ANDROID_SERIAL" == emulator-* ]] || { echo 'Only an explicitly selected emulator is allowed' >&2; exit 1; }
export PATH="$TVA_SDK/platform-tools:$PATH"
cd "$TVA_ROOT"
mkdir -p build
./build-apk.sh audit | tee build/audit-build.log
TVA_AUDIT_DIR=$(sed -n 's/^Build directory: //p' build/audit-build.log)
tests/compile-panel-tests.sh "$TVA_AUDIT_DIR" | tee build/audit-tests.log
TVA_TEST_DIR=$(tail -1 build/audit-tests.log)
umask 077
TVA_KEYS=$(mktemp -d)
openssl rand -base64 -out "$TVA_KEYS/password" 48
keytool -genkeypair -keystore "$TVA_KEYS/audit.p12" -storetype PKCS12 -storepass:file "$TVA_KEYS/password" \
  -keypass:file "$TVA_KEYS/password" -alias audit -dname 'CN=TV Assistant isolated test' -keyalg RSA -keysize 3072 -validity 2
export TVA_SIGNING_KEYSTORE="$TVA_KEYS/audit.p12" TVA_SIGNING_ALIAS=audit TVA_STORE_PASSWORD_FILE="$TVA_KEYS/password" TVA_KEY_PASSWORD_FILE="$TVA_KEYS/password"
TVA_UNSIGNED=$(sed -n 's/^Unsigned APK: //p' build/audit-build.log)
scripts/sign-apk.sh "$TVA_UNSIGNED" "$TVA_KEYS/audit.apk"
scripts/sign-apk.sh "$TVA_TEST_DIR/instrumentation-unsigned.apk" "$TVA_KEYS/tests.apk"
adb -s "$ANDROID_SERIAL" install -r "$TVA_KEYS/audit.apk"
adb -s "$ANDROID_SERIAL" install -r "$TVA_KEYS/tests.apk"
adb -s "$ANDROID_SERIAL" shell appops set fr.maison.tvlauncher.audit SYSTEM_ALERT_WINDOW allow
adb -s "$ANDROID_SERIAL" shell am instrument -w fr.maison.tvlauncher.tests/fr.maison.tvlauncher.PanelInstrumentation | tee build/instrumentation.log
grep -q '^PASS: ' build/instrumentation.log
! grep -Eq 'FAIL:|INSTRUMENTATION_FAILED|Process crashed' build/instrumentation.log
adb -s "$ANDROID_SERIAL" uninstall fr.maison.tvlauncher.tests
adb -s "$ANDROID_SERIAL" uninstall fr.maison.tvlauncher.audit
printf 'PASS: isolated instrumentation completed; temporary signing material: %s\n' "$TVA_KEYS"
