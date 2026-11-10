#!/usr/bin/env bash
# Packs every font from gen_fonts.py into a Samsung-style font APK (package com.monotype.android.font.*),
# which One UI lists under Lock screen editor > clock font > "More fonts".
# usage: build_apks.sh FONTS_DIR OUT_DIR KEYSTORE
set -euo pipefail
FONTS=$(realpath "$1"); OUT=$(realpath -m "$2"); KS=$(realpath "$3")
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
JAR=$(ls -d "$ANDROID_HOME"/platforms/android-* | sort -V | tail -1)/android.jar
mkdir -p "$OUT"
for d in "$FONTS"/*/; do
  id=$(basename "$d"); [ -f "$d/font.ttf" ] || continue
  pkg=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['pkg'])" "$d/meta.json")
  label=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['label'])" "$d/meta.json")
  w=$(mktemp -d)
  mkdir -p "$w/assets/fonts" "$w/assets/xml"
  cp "$d/font.ttf" "$w/assets/fonts/LOCKSCREENDepth$id.ttf"
  cp "$d/font.xml" "$w/assets/xml/LOCKSCREENDepth$id.xml"
  cat > "$w/AndroidManifest.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="1" android:versionName="1.0">
    <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="34"/>
    <application android:label="$label" android:hasCode="false"/>
</manifest>
XML
  "$BT/aapt2" link --manifest "$w/AndroidManifest.xml" -I "$JAR" -A "$w/assets" -o "$w/u.apk"
  "$BT/zipalign" -f -p 4 "$w/u.apk" "$w/a.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "$OUT/DepthClock-$id.apk" "$w/a.apk"
  rm -rf "$w"; echo "built DepthClock-$id.apk"
done
