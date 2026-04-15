#!/bin/bash
set -e

export ANDROID_HOME=/home/thomas/android-sdk
export ANDROID_SDK_ROOT=/home/thomas/android-sdk

# Läs och räkna upp versionsnummer
VERSION=$(cat version.txt | tr -d '[:space:]')
echo "Bygger version $VERSION..."

# Bygg APK
./gradlew assembleDebug

# Kopiera med versionsnamn
OUT="drawing-v${VERSION}.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT"

# Spara också som drawing.apk (senaste)
cp "$OUT" drawing.apk

# Räkna upp för nästa bygge
echo $((VERSION + 1)) > version.txt

echo ""
echo "Klar: $OUT ($(du -h "$OUT" | cut -f1))"
echo "Nästa bygge blir version $((VERSION + 1))"
