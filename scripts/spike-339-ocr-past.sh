#!/usr/bin/env bash
# Spike #339, never merged: OCR stage time, today's export vs the past-key-values export.
# Usage: ANDROID_SERIAL=<serial> scripts/spike-339-ocr-past.sh <export-dir> [--skip-build]
# <export-dir> holds encoder_model.onnx, decoder_model.onnx, decoder_with_past_model.onnx and
# decoder_model_merged.onnx. Run scripts/run-speed-benchmark.sh once first for the pages and vision models.
set -euo pipefail
cd "$(dirname "$0")/.."
EXPORT="$1"
DEVICE_DIR=/data/local/tmp/yomu-speed
FIXTURES=scripts/speed-fixtures
echo "Device: $(adb shell getprop ro.product.model | tr -d '\r')"

adb shell "mkdir -p $DEVICE_DIR/pages $DEVICE_DIR/models/vision $DEVICE_DIR/models/ocr-past"
push() { [ "$(wc -c < "$1" | tr -d ' ')" = "$(adb shell "stat -c %s '$2' 2>/dev/null" | tr -d '\r' || true)" ] || adb push "$1" "$2" >/dev/null; }
for f in "$FIXTURES"/pages/*.jpg; do push "$f" "$DEVICE_DIR/pages/$(basename "$f")"; done
for f in bubble_detection.onnx manga_ocr_encoder.onnx manga_ocr_decoder.onnx vocab.txt; do push "$FIXTURES/models/vision/$f" "$DEVICE_DIR/models/vision/$f"; done
for f in encoder_model.onnx decoder_model.onnx decoder_with_past_model.onnx decoder_model_merged.onnx; do push "$EXPORT/$f" "$DEVICE_DIR/models/ocr-past/$f"; done
adb shell chmod -R 755 "$DEVICE_DIR"

if [ "${2:-}" != "--skip-build" ]; then
  ./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
fi

adb logcat -c
adb shell am instrument -w -e class com.yomu.app.OcrDecoderStateSpikeTest com.yomu.app.test/com.yomu.app.CustomTestRunner | tr -d '\r' | tail -3
adb logcat -d -s OcrSpike:I | grep -o 'CROP.*same=false.*' || true
printf '\n| page | crops | reader | OCR ms |\n|---|---|---|---|\n'
adb logcat -d -s OcrSpike:I | grep -o 'OCR_TIMING.*' | awk '{ for (i = 2; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] } printf "| %s | %s | %s | %s |\n", v["page"], v["crops"], v["reader"], v["ms"] }'
# Staged copies are ~370 MB in the app's filesDir; the vision ones are shared with the app and stay.
adb shell run-as com.yomu.app rm -rf files/models/ocr-past
