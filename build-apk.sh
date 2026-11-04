#!/usr/bin/env bash
# Always unsigned by default. Signing is a separate explicit operation.
set -euo pipefail
source "$(dirname "$0")/scripts/build-env.sh"
TVA_MODE="${1:-release}"
[[ "$TVA_MODE" == release || "$TVA_MODE" == audit ]] || { echo "Usage: $0 [release|audit]" >&2; exit 1; }
mkdir -p "$TVA_ROOT/build" "$TVA_ROOT/release"
TVA_BUILD="$(mktemp -d "$TVA_ROOT/build/apk.XXXXXX")"
mkdir -p "$TVA_BUILD/classes" "$TVA_BUILD/res" "$TVA_BUILD/dex" "$TVA_BUILD/generated" "$TVA_BUILD/legal/licenses"
cp "$TVA_ROOT/libs/LICENSE-AndroidX.txt" "$TVA_ROOT/libs/ANDROIDX-NOTICE.txt" "$TVA_BUILD/legal/licenses/"
cmp "$TVA_ROOT/custom_components/home_assistant_tv/schema.json" "$TVA_ROOT/app/src/main/res/raw/launcher_profile_schema.json"
cmp "$TVA_ROOT/custom_components/home_assistant_tv/layout_presets.json" "$TVA_ROOT/app/src/main/res/raw/layout_presets.json"
python3 "$TVA_ROOT/scripts/generate-localization.py" --check
python3 "$TVA_ROOT/scripts/prepare-manifest.py" "$TVA_ROOT/app/src/main/AndroidManifest.xml" "$TVA_BUILD/AndroidManifest.xml" "$TVA_MODE"
"$TVA_TOOLS/aapt2" compile --dir "$TVA_ROOT/app/src/main/res" -o "$TVA_BUILD/res/resources.zip"
"$TVA_TOOLS/aapt2" link -o "$TVA_BUILD/resources.apk" -I "$TVA_PLATFORM" --manifest "$TVA_BUILD/AndroidManifest.xml" --custom-package fr.maison.tvlauncher --java "$TVA_BUILD/generated" -A "$TVA_BUILD/legal" --auto-add-overlay "$TVA_BUILD/res/resources.zip"
find "$TVA_ROOT/app/src/main/java" "$TVA_BUILD/generated" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -source 17 -target 17 -classpath "$TVA_PLATFORM:$TVA_ROOT/libs/*" -d "$TVA_BUILD/classes"
mapfile -d '' TVA_CLASSES < <(find "$TVA_BUILD/classes" -name '*.class' -print0)
"$TVA_TOOLS/d8" --min-api 30 --lib "$TVA_PLATFORM" --output "$TVA_BUILD/dex" "${TVA_CLASSES[@]}" "$TVA_ROOT"/libs/*.jar
(cd "$TVA_BUILD/dex" && jar uf "$TVA_BUILD/resources.apk" classes.dex)
TVA_VERSION="$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).getroot().get("{http://schemas.android.com/apk/res/android}versionName"))' "$TVA_BUILD/AndroidManifest.xml")"
TVA_OUTPUT="$TVA_ROOT/release/TVAssistant-$TVA_VERSION-$TVA_MODE-unsigned.apk"
"$TVA_TOOLS/zipalign" -f 4 "$TVA_BUILD/resources.apk" "$TVA_OUTPUT"
printf 'Unsigned APK: %s\nBuild directory: %s\n' "$TVA_OUTPUT" "$TVA_BUILD"
