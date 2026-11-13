#!/bin/sh
# Generates the sample MP3 library and copies it to the connected emulator or phone, then asks Android to
# index it. Touches a device, so ask the owner before the first run. Pick /sdcard/Music/Sample in the app.
set -eu
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"
SAMPLES=core/tags/build/samples

./gradlew :core:tags:generateSamples --console=plain

# Each top-level folder (Music, Recordings) is merged into the device's shared storage.
for folder in "$SAMPLES"/*; do
  "$ADB" push "$folder" /sdcard/
done

"$ADB" shell content call --method scan_volume --uri content://media --arg external_primary
echo "Pushed $(find "$SAMPLES" -name '*.mp3' | wc -l | tr -d ' ') files. Music folder to pick: /sdcard/Music/Sample"
