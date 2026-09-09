#!/bin/sh
# build.sh — build S22-Updater APK without Android Studio/Gradle.
# Needs: JDK 17, Android build-tools (aapt/aapt2/d8/zipalign/apksigner),
#        platform android.jar. Override via env:
#   JAVA_HOME, BT (build-tools dir), PLATFORM (android.jar)
set -u
fail() { echo "build: ERROR: $*" >&2; exit 1; }

HERE="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/toolchains/host/usr/lib/jvm/java-17-openjdk-amd64}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
BT="${BT:-$HOME/android-sdk/build-tools}"
PLATFORM="${PLATFORM:-$HOME/android-sdk/android-34/android.jar}"
OUT="$HERE/out"

[ -x "$JAVA_HOME/bin/javac" ] || fail "javac not found (JAVA_HOME=$JAVA_HOME)"
[ -x "$BT/aapt2" ] || fail "aapt2 not found (BT=$BT)"
[ -f "$PLATFORM" ] || fail "android.jar not found (PLATFORM=$PLATFORM)"

rm -rf "$OUT"
mkdir -p "$OUT/compiled_res" "$OUT/classes" "$OUT/dex"

echo "build: [1/6] javac"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn \
  -bootclasspath "$PLATFORM" \
  -d "$OUT/classes" \
  $(find "$HERE/src" -name "*.java") 2>&1 | grep -v "bootstrap\|deprecat" || true
[ -n "$(find "$OUT/classes" -name '*.class' | head -n 1)" ] || fail "no classes compiled"

echo "build: [2/6] aapt2 compile"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/compiled_res.zip" || fail "aapt2 compile"

echo "build: [3/6] d8"
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" --min-api 28 \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name "*.class") || fail "d8 failed"
ls "$OUT/dex/classes.dex" >/dev/null || fail "classes.dex missing"

echo "build: [4/6] aapt2 link"
"$BT/aapt2" link -o "$OUT/unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$HERE/AndroidManifest.xml" \
  "$OUT/compiled_res.zip" \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 || fail "aapt2 link"

echo "build: [5/6] add dex + align"
cp "$OUT/unsigned.apk" "$OUT/app.apk"
(cd "$OUT/dex" && "$BT/aapt" add -k ../app.apk classes.dex >/dev/null) || fail "add dex"
"$BT/zipalign" -f 4 "$OUT/app.apk" "$OUT/aligned.apk" || fail "zipalign"

echo "build: [6/6] sign"
KS="$HERE/debug.keystore"
if [ ! -f "$KS" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10950 -dname "CN=Android Debug,O=Android,C=US" || fail "keytool"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/S22-Updater-v1.apk" "$OUT/aligned.apk" || fail "apksigner"
"$BT/apksigner" verify --print-certs "$OUT/S22-Updater-v1.apk" | head -n 4
ls -lh "$OUT/S22-Updater-v1.apk"
echo "build: DONE -> $OUT/S22-Updater-v1.apk"
