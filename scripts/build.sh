#!/usr/bin/env bash
# Equivalente de build.ps1 para macOS e Linux.
set -euo pipefail
BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION:-35.0.0}
PLATFORM=${PLATFORM:-android-35}
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[ -n "${JAVA_HOME:-}" ] || { echo 'Defina JAVA_HOME para o JDK 17 ou 21.' >&2; exit 1; }
[ -n "$SDK_ROOT" ] || { echo 'Defina ANDROID_SDK_ROOT.' >&2; exit 1; }
BUILD_TOOLS=${BUILD_TOOLS_PATH:-$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION}
PLATFORM_JAR=${PLATFORM_JAR:-$SDK_ROOT/platforms/$PLATFORM/android.jar}
BUILD=$PROJECT_ROOT/.build
LOCAL=$PROJECT_ROOT/.local
step() { echo "Falha em: $1" >&2; exit 1; }

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$LOCAL"
find "$PROJECT_ROOT/app/src/main/java" -name '*.java' > "$BUILD/sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$PLATFORM_JAR:$BUILD_TOOLS/core-lambda-stubs.jar" \
  -d "$BUILD/classes" @"$BUILD/sources.txt" || step javac
"$BUILD_TOOLS/aapt" package -f -M "$PROJECT_ROOT/app/src/main/AndroidManifest.xml" \
  -I "$PLATFORM_JAR" -F "$BUILD/base.apk" || step manifesto
"$JAVA_HOME/bin/jar" cf "$BUILD/classes.jar" -C "$BUILD/classes" . || step classes.jar
"$JAVA_HOME/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM_JAR" --min-api 26 --output "$BUILD" "$BUILD/classes.jar" || step D8
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
"$JAVA_HOME/bin/jar" uf "$BUILD/unsigned.apk" -C "$BUILD" classes.dex || step 'empacotamento DEX'
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk" || step zipalign
KEY=$LOCAL/debug.jks
if [ ! -f "$KEY" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEY" -storepass android -keypass android \
    -alias debug -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 \
    -validity 10000 || step 'chave de desenvolvimento'
fi
OUTPUT=$BUILD/tv-launcher-debug.apk
"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$KEY" \
  --ks-pass pass:android --out "$OUTPUT" "$BUILD/aligned.apk" || step assinatura
"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" verify "$OUTPUT" \
  || step 'verificacao da assinatura'
echo 'APK criado em .build/tv-launcher-debug.apk'
