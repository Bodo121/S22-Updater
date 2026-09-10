#!/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/toolchains/host/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
BT="${BT:-$HOME/android-sdk/build-tools}"
PLATFORM="${PLATFORM:-$HOME/android-sdk/android-34/android.jar}"
OUT="$HERE/out/tests"
mkdir -p "$OUT/host" "$OUT/classes" "$OUT/dex"
javac -d "$OUT/host" "$HERE/src/com/bodo121/s22updater/Network.java" \
    "$HERE/src/com/bodo121/s22updater/PayloadStore.java" "$HERE/tests/StoreTest.java"
java -cp "$OUT/host" com.bodo121.s22updater.StoreTest
javac --release 8 -classpath "$PLATFORM" -d "$OUT/classes" "$HERE/tests/SmokeTest.java"
shopt -s globstar
classes=("$OUT"/classes/**/*.class)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$PLATFORM" --min-api 28 --output "$OUT/dex" "${classes[@]}"
"$BT/aapt2" link -o "$OUT/test.apk" -I "$PLATFORM" --manifest "$HERE/tests/AndroidManifest.xml"
(cd "$OUT/dex" && "$BT/aapt" add "$OUT/test.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/test.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$HERE/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/S22-SmokeTest.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/S22-SmokeTest.apk"
if [[ "${1:-}" == "--device" ]]; then
    adb get-state
    adb install -r "$HERE"/out/S22-Updater-v*.apk
    adb install -r "$OUT/S22-SmokeTest.apk"
    result="$(adb shell am instrument -w com.bodo121.s22updater.tests/.SmokeTest)"
    printf '%s\n' "$result"
    [[ "$result" == *"PASS: launch, status views, listeners, tabs, activity recreation"* ]]
else
    printf '%s\n' 'Device tests built, not run. Use: bash tests/check.sh --device'
fi
