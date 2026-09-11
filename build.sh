#!/bin/bash
# build.sh — build S22-Updater APK without Android Studio/Gradle.
# Needs: JDK 17, Android build-tools (aapt/aapt2/d8/zipalign/apksigner),
#        platform android.jar. Override via env:
#   JAVA_HOME, BT (build-tools dir), PLATFORM (android.jar)
# Optional signing overrides (release builds should reuse one keystore so
# updates install cleanly over previous versions):
#   SIGN_KEYSTORE, SIGN_STORE_PASS, SIGN_KEY_PASS, SIGN_ALIAS
# Optional Android 9+ key-rotation lineage for one-time migration from an old
# signing key:
#   SIGN_LINEAGE, SIGN_ROTATION_MIN_SDK (default 28)
# Optional safety net that fails the build unless the APK signer matches:
#   SIGN_EXPECTED_CERT_SHA256 (permanent release cert, see README)
#
# The applicationId is frozen: com.bodo121.s22updater. Every release must use
# the same package name and the same release key (v2 since v4.8, see README),
# or Android blocks updates.
set -euo pipefail
shopt -s globstar nullglob
fail() { echo "build: ERROR: $*" >&2; exit 1; }

HERE="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/toolchains/host/usr/lib/jvm/java-17-openjdk-amd64}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
BT="${BT:-$HOME/android-sdk/build-tools}"
PLATFORM="${PLATFORM:-$HOME/android-sdk/android-34/android.jar}"
OUT="$HERE/out"
VENDOR="$OUT/vendor"

[ -x "$JAVA_HOME/bin/javac" ] || fail "javac not found (JAVA_HOME=$JAVA_HOME)"
[ -x "$BT/aapt2" ] || fail "aapt2 not found (BT=$BT)"
[ -f "$PLATFORM" ] || fail "android.jar not found (PLATFORM=$PLATFORM)"

VERSION_NAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$HERE/AndroidManifest.xml" | head -n 1)"
[ -n "$VERSION_NAME" ] || fail "cannot read versionName from manifest"
PKG_NAME="$(sed -n 's/.* package="\([^"]*\)".*/\1/p' "$HERE/AndroidManifest.xml" | head -n 1)"
[ "$PKG_NAME" = "com.bodo121.s22updater" ] \
  || fail "package name is frozen (found '$PKG_NAME'); changing it breaks updates"
APK_BASENAME="S22-Updater-v${VERSION_NAME%.0}"

rm -rf "$OUT"
mkdir -p "$OUT/compiled_res" "$OUT/classes" "$OUT/dex" "$VENDOR" "$VENDOR/shizuku"

echo "build: [0/6] vendor Shizuku client libraries"
SHIZUKU_VERSION="13.1.5"
SHIZUKU_API_SHA="4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b"
SHIZUKU_PROVIDER_SHA="b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3"
fetch_lib() {
  local name="$1"
  local sha="$2"
  local dest="$VENDOR/$name-$SHIZUKU_VERSION.aar"
  if [ ! -f "$dest" ]; then
    curl -sSL --max-time 120 -o "$dest" \
      "https://repo1.maven.org/maven2/dev/rikka/shizuku/$name/$SHIZUKU_VERSION/$name-$SHIZUKU_VERSION.aar" \
      || fail "download $name failed"
  fi
  echo "$sha  $dest" | sha256sum -c - || fail "$name checksum mismatch"
}
fetch_lib api "$SHIZUKU_API_SHA"
fetch_lib provider "$SHIZUKU_PROVIDER_SHA"
rm -rf "$VENDOR/shizuku"
mkdir -p "$VENDOR/shizuku/api" "$VENDOR/shizuku/provider"
(cd "$VENDOR/shizuku/api" && unzip -oq "$VENDOR/api-$SHIZUKU_VERSION.aar" classes.jar)
(cd "$VENDOR/shizuku/provider" && unzip -oq "$VENDOR/provider-$SHIZUKU_VERSION.aar" classes.jar)
SHIZUKU_CP="$VENDOR/shizuku/api/classes.jar:$VENDOR/shizuku/provider/classes.jar"

sources=("$HERE"/src/**/*.java)
[ "${#sources[@]}" -gt 0 ] || fail "no Java sources"
echo "build: [1/6] javac"
"$JAVA_HOME/bin/javac" --release 8 -nowarn \
  -classpath "$PLATFORM:$SHIZUKU_CP" \
  -d "$OUT/classes" \
  "${sources[@]}" || fail "javac failed"
classes=("$OUT"/classes/**/*.class)
[ "${#classes[@]}" -gt 0 ] || fail "no classes compiled"

echo "build: [2/6] aapt2 compile"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/compiled_res.zip" || fail "aapt2 compile"

echo "build: [3/6] d8"
mkdir -p "$VENDOR/shizuku-classes"
(cd "$VENDOR/shizuku/api" && "$JAVA_HOME/bin/jar" xf classes.jar)
(cd "$VENDOR/shizuku/provider" && "$JAVA_HOME/bin/jar" xf classes.jar)
shizuku_classes=("$VENDOR"/shizuku/api/**/*.class "$VENDOR"/shizuku/provider/**/*.class)
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" --min-api 28 \
  --output "$OUT/dex" \
  "${classes[@]}" "${shizuku_classes[@]}" || fail "d8 failed"
ls "$OUT/dex/classes.dex" >/dev/null || fail "classes.dex missing"

echo "build: [4/6] aapt2 link"
"$BT/aapt2" link -o "$OUT/unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$HERE/AndroidManifest.xml" \
  "$OUT/compiled_res.zip" \
  --min-sdk-version 28 --target-sdk-version 34 || fail "aapt2 link"

echo "build: [5/6] add dex + align"
cp "$OUT/unsigned.apk" "$OUT/app.apk"
(cd "$OUT/dex" && "$BT/aapt" add -k ../app.apk classes.dex >/dev/null) || fail "add dex"
"$BT/zipalign" -f 4 "$OUT/app.apk" "$OUT/aligned.apk" || fail "zipalign"

echo "build: [6/6] sign"
KS="${SIGN_KEYSTORE:-$HERE/debug.keystore}"
STORE_PASS="${SIGN_STORE_PASS:-android}"
KEY_PASS="${SIGN_KEY_PASS:-android}"
KEY_ALIAS="${SIGN_ALIAS:-androiddebugkey}"
LINEAGE="${SIGN_LINEAGE:-}"
if [ ! -f "$KS" ]; then
  [ "$KS" = "$HERE/debug.keystore" ] || fail "signing keystore not found: $KS"
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10950 -dname "CN=Android Debug,O=Android,C=US" || fail "keytool"
fi
sign_args=(--ks "$KS" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:$KEY_PASS")
if [ -n "$LINEAGE" ]; then
  [ -f "$LINEAGE" ] || fail "signing lineage not found: $LINEAGE"
  sign_args+=(--lineage "$LINEAGE" --min-sdk-version 28 \
    --rotation-min-sdk-version "${SIGN_ROTATION_MIN_SDK:-28}" \
    --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true)
fi
"$BT/apksigner" sign "${sign_args[@]}" \
  --out "$OUT/$APK_BASENAME.apk" "$OUT/aligned.apk" || fail "apksigner"
"$BT/apksigner" verify --verbose --print-certs "$OUT/$APK_BASENAME.apk"
if [ -n "${SIGN_EXPECTED_CERT_SHA256:-}" ]; then
  "$BT/apksigner" verify --print-certs "$OUT/$APK_BASENAME.apk" 2>/dev/null \
    | grep -qi "$SIGN_EXPECTED_CERT_SHA256" \
    || fail "signer does not match SIGN_EXPECTED_CERT_SHA256 (wrong keystore?)"
  echo "build: signer matches expected release certificate"
fi
"$BT/zipalign" -c 4 "$OUT/$APK_BASENAME.apk"
ls -lh "$OUT/$APK_BASENAME.apk"
echo "build: DONE -> $OUT/$APK_BASENAME.apk"
