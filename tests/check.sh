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
# Functional KSU verdict mapping is mandatory. If this script is run from a
# clean tree, bootstrap the vendor jars through build.sh instead of silently
# skipping the verdict test.
if [ ! -f "$HERE/out/vendor/shizuku/api/classes.jar" ] \
    || [ ! -f "$HERE/out/vendor/shizuku/provider/classes.jar" ]; then
  bash "$HERE/build.sh" >/dev/null
  mkdir -p "$OUT/host" "$OUT/classes" "$OUT/dex"
fi
VCP="$PLATFORM:$HERE/out/vendor/shizuku/api/classes.jar:$HERE/out/vendor/shizuku/provider/classes.jar"
javac -d "$OUT/host" "$HERE/src/com/bodo121/s22updater/CommandResult.java" \
    "$HERE/src/com/bodo121/s22updater/CommandRunner.java" \
    "$HERE/src/com/bodo121/s22updater/RootState.java" \
    "$HERE/src/com/bodo121/s22updater/KernelSuController.java" \
    "$HERE/tests/VerdictTest.java" "$HERE/tests/CommandTest.java"
java -cp "$OUT/host" com.bodo121.s22updater.VerdictTest
java -cp "$OUT/host" com.bodo121.s22updater.CommandTest
javac --release 8 -classpath "$PLATFORM" -d "$OUT/classes" "$HERE/tests/SmokeTest.java"
shopt -s globstar
classes=("$OUT"/classes/**/*.class)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$PLATFORM" --min-api 28 --output "$OUT/dex" "${classes[@]}"
"$BT/aapt2" link -o "$OUT/test.apk" -I "$PLATFORM" --manifest "$HERE/tests/AndroidManifest.xml"
(cd "$OUT/dex" && "$BT/aapt" add "$OUT/test.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/test.apk" "$OUT/aligned.apk"
# debug.keystore is git-ignored and may not exist (e.g. CI release builds sign
# with release.keystore instead, so build.sh never creates it). The smoke test
# only needs a throwaway valid key, so generate one if missing.
KS="$OUT/debug.keystore"
if [ ! -f "$KS" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10950 -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
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
