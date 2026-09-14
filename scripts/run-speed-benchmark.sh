#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURES="$REPO_ROOT/scripts/speed-fixtures"
DEVICE_DIR="/data/local/tmp/yomu-speed"
APP_ID="com.yomu.app"
TAG="SpeedBenchmark"

usage() {
  cat <<EOF
Usage: $0 [--skip-build]

Report-only speed benchmark (#230). Builds and installs the app, pushes the fixture pages and the
shipped models, times every LlmModelCatalog entry through the real pipeline, and prints a table of
per-stage ms and peak PSS. Asserts nothing about the numbers; fails only if the pipeline errors.

Works on a physical phone or an Android Studio emulator. With more than one device attached, pick
one with ANDROID_SERIAL=<serial> (see 'adb devices').

Fixtures (gitignored, never committed):
  $FIXTURES/pages/*.jpg          Three manga pages. OpenMantra is CC BY-NC 4.0, so they stay local.
                                 Seeded from eval/bubble-detection/cases when that directory exists.
  $FIXTURES/models/vision/       Bubble detector + MangaOCR, downloaded on first run.
  $FIXTURES/models/llm/          Catalog GGUFs, downloaded on first run.

  --skip-build   Reuse the installed APKs.
EOF
}

SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown flag: %s\n' "$arg" >&2; usage >&2; exit 2 ;;
  esac
done

cd "$REPO_ROOT"
command -v adb >/dev/null || { echo 'adb is not in PATH' >&2; exit 1; }

# adb honours ANDROID_SERIAL itself; without it, more than one device (a phone plus an emulator) makes
# every call below fail with "more than one device/emulator".
if [ -z "${ANDROID_SERIAL:-}" ]; then
  devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  case "$(printf '%s' "$devices" | grep -c .)" in
    0) echo 'No ready device. Connect a phone or start an emulator, then retry.' >&2; exit 1 ;;
    1) export ANDROID_SERIAL="$devices" ;;
    *) printf 'More than one device; set ANDROID_SERIAL to one of:\n%s\n' "$devices" >&2; exit 1 ;;
  esac
fi
echo "Device: $ANDROID_SERIAL ($(adb shell getprop ro.product.model | tr -d '\r'), $(adb shell getprop ro.product.cpu.abi | tr -d '\r'))"

mkdir -p "$FIXTURES/pages"
if ! ls "$FIXTURES"/pages/*.jpg >/dev/null 2>&1; then
  for case_id in balloon-dense-dialogue bourei-p04 tojime-dense-action; do
    src="$REPO_ROOT/eval/bubble-detection/cases/$case_id/page.jpg"
    [ -f "$src" ] && cp "$src" "$FIXTURES/pages/$case_id.jpg"
  done
fi
ls "$FIXTURES"/pages/*.jpg >/dev/null 2>&1 || { echo "No pages in $FIXTURES/pages; add three .jpg manga pages." >&2; exit 1; }

# Same pinned revisions ModelManager downloads; keep the two in step when a model changes.
MODELS=(
  "vision/bubble_detection.onnx|https://huggingface.co/Kiuyha/Manga-Bubble-YOLO/resolve/fb646500455e8a8a3a807fd27b855c8e4fc63766/onnx/yolo26n.onnx"
  "vision/manga_ocr_encoder.onnx|https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/encoder_model.onnx"
  "vision/manga_ocr_decoder.onnx|https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/decoder_model.onnx"
  "vision/vocab.txt|https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/vocab.txt"
  "llm/qwen25_1.5b_instruct_q4_k_m.gguf|https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/9eadc66189c7641e1ddd226b8267a9119b2ce2d4/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
  "llm/cat_translate_0.8b_q4_k_m.gguf|https://huggingface.co/mradermacher/CAT-Translate-0.8b-GGUF/resolve/834d0624185e964856a1b3c43eb5e114c9c41df5/CAT-Translate-0.8b.Q4_K_M.gguf"
  "llm/cat_translate_1.4b_q4_k_m.gguf|https://huggingface.co/mradermacher/CAT-Translate-1.4b-GGUF/resolve/2eb35647e57b5981c14611e67b9ad205329b498d/CAT-Translate-1.4b.Q4_K_M.gguf"
)
for entry in "${MODELS[@]}"; do
  rel="${entry%%|*}"; url="${entry#*|}"; file="$FIXTURES/models/$rel"
  [ -f "$file" ] && continue
  echo "Fetching $rel..."
  mkdir -p "$(dirname "$file")"
  curl -L --fail --progress-bar -o "$file.part" "$url"
  mv "$file.part" "$file"
done

# Size-compare before pushing, so a re-run does not re-send gigabytes.
push() {
  local src="$1" dest="$2" local_size device_size
  local_size="$(wc -c < "$src" | tr -d ' ')"
  device_size="$(adb shell "stat -c %s '$dest' 2>/dev/null" | tr -d '\r' || true)"
  [ "$local_size" = "$device_size" ] && return
  echo "Pushing $(basename "$src") ($local_size bytes)"
  adb push "$src" "$dest" >/dev/null
}
adb shell "rm -rf '$DEVICE_DIR/pages' && mkdir -p '$DEVICE_DIR/pages' '$DEVICE_DIR/models/vision' '$DEVICE_DIR/models/llm'"
for page in "$FIXTURES"/pages/*.jpg; do push "$page" "$DEVICE_DIR/pages/$(basename "$page")"; done
for entry in "${MODELS[@]}"; do rel="${entry%%|*}"; push "$FIXTURES/models/$rel" "$DEVICE_DIR/models/$rel"; done
adb shell chmod -R 755 "$DEVICE_DIR"

if [ "$SKIP_BUILD" -eq 0 ]; then
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
  # adb install, not gradle installDebug: gradle installs on every attached device, ignoring ANDROID_SERIAL.
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
fi

# am instrument directly rather than connectedAndroidTest: no gradle test timeout on a slow emulator.
adb logcat -c
set +e
instrument_out="$(adb shell am instrument -w -e class "$APP_ID.SpeedBenchmarkTest" "$APP_ID.test/$APP_ID.CustomTestRunner" | tr -d '\r')"
set -e
timing="$(adb logcat -d -s "$TAG:I" | grep -o 'TIMING .*' || true)"

if [ -n "$timing" ]; then
  printf '\n| model | page | stage | ms | peak PSS (MB) |\n|---|---|---|---|---|\n'
  printf '%s\n' "$timing" | awk '{
    for (i = 2; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    printf "| %s | %s | %s | %s | %d |\n", v["model"], v["page"], v["stage"], v["ms"], v["peakPssKb"] / 1024
  }'
fi

# The instrumentation exit status is 0 even when a test fails; the result line is the truth.
if ! printf '%s' "$instrument_out" | grep -q '^OK ('; then
  printf '\n%s\n' "$instrument_out" >&2
  echo 'Speed benchmark failed.' >&2
  exit 1
fi
