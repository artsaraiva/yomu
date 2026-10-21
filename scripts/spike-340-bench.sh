#!/usr/bin/env bash
# Spike #340: one run-speed-benchmark.sh pass plus llama's per-generation prompt/decode split.
# Usage: ANDROID_SERIAL=<serial> scripts/spike-340-bench.sh <out-name> <run-speed-benchmark.sh flags...>
# Writes <out-name>.table and <out-name>.logcat under $OUT_DIR (default /tmp/yomu-340).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${OUT_DIR:-/tmp/yomu-340}"
name="$1"; shift
mkdir -p "$OUT_DIR"
# Throttling halves decode speed, so every run starts cool: thermal status 0 and battery at most 35 C.
until [ "$(adb shell dumpsys thermalservice | sed -n 's/^Thermal Status: //p' | tr -d '\r')" = 0 ] &&
      [ "$(adb shell dumpsys battery | sed -n 's/^ *temperature: //p' | tr -d '\r')" -le 350 ]; do sleep 30; done
adb logcat -c
adb logcat -s 'LlamaJNI:*' 'LlamaCpp:*' > "$OUT_DIR/$name.logcat" &
pid=$!
"$REPO_ROOT/scripts/run-speed-benchmark.sh" --skip-build "$@" | grep '^|' > "$OUT_DIR/$name.table" || true
sleep 1; kill "$pid" 2>/dev/null || true
cat "$OUT_DIR/$name.table"
# The first Perf line is the warm-up page; the rest follow the table's pages in order.
grep -o 'Perf .*' "$OUT_DIR/$name.logcat" | awk '{
  for (i = 2; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
  printf "%s prefill %d tok %.0f ms (%.1f tok/s) | decode %d tok %.0f ms (%.1f tok/s)\n", (NR == 1 ? "warm-up" : "page " NR - 1),
    v["promptTokens"], v["promptMs"], v["promptTokens"] * 1000 / v["promptMs"], v["decodeTokens"], v["decodeMs"], v["decodeTokens"] * 1000 / v["decodeMs"]
}'
