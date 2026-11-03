#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../scripts/build-env.sh"
[[ $# == 1 && -d "$1/classes" ]] || { echo "Pass the audit build directory" >&2; exit 1; }
TVA_AUDIT_BUILD="$(realpath "$1")"
TVA_TEST_BUILD="$(mktemp -d "$TVA_ROOT/build/tests.XXXXXX")"
mkdir -p "$TVA_TEST_BUILD/classes" "$TVA_TEST_BUILD/dex"
javac -encoding UTF-8 -source 17 -target 17 -classpath "$TVA_PLATFORM:$TVA_AUDIT_BUILD/classes:$TVA_ROOT/libs/*" -d "$TVA_TEST_BUILD/classes" "$TVA_ROOT"/tests/*.java
"$TVA_TOOLS/d8" --min-api 30 --lib "$TVA_PLATFORM" --classpath "$TVA_AUDIT_BUILD/classes" --output "$TVA_TEST_BUILD/dex" "$TVA_TEST_BUILD"/classes/fr/maison/tvlauncher/*.class
python3 "$TVA_ROOT/scripts/prepare-manifest.py" "$TVA_ROOT/tests/AndroidManifest.xml" "$TVA_TEST_BUILD/AndroidManifest.xml" tests
"$TVA_TOOLS/aapt2" link -o "$TVA_TEST_BUILD/resources.apk" -I "$TVA_PLATFORM" --manifest "$TVA_TEST_BUILD/AndroidManifest.xml"
(cd "$TVA_TEST_BUILD/dex" && jar uf "$TVA_TEST_BUILD/resources.apk" classes.dex)
"$TVA_TOOLS/zipalign" -f 4 "$TVA_TEST_BUILD/resources.apk" "$TVA_TEST_BUILD/instrumentation-unsigned.apk"
printf '%s\n' "$TVA_TEST_BUILD"
