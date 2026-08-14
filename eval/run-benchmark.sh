#!/usr/bin/env bash
set -eEuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$REPO_ROOT/eval"
DEVICE_BENCHMARK_DIR="/sdcard/Android/data/com.yomu.app/files/yomu-benchmark"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$SCRIPT_DIR/benchmark-results/$TIMESTAMP"
RAW_ARTIFACTS_DIR="$RUN_DIR/raw-artifacts"
LOG_FILE="$RUN_DIR/benchmark.log"
LOGCAT_FILE="$RUN_DIR/logcat.log"

SKIP_BUILD=0
SKIP_INSTALL=0
SKIP_EVAL=0

usage() {
  printf 'Usage: %s [--skip-build] [--skip-install] [--skip-eval]\n' "$0"
  printf '\n'
  printf 'Flags:\n'
  printf '  --skip-build    Skip build (use installed APKs as-is)\n'
  printf '  --skip-install  Skip install only, still build. Use if model already downloaded.\n'
  printf '  --skip-eval     Skip scoring step\n'
}

for arg in "$@"; do
  case "$arg" in
    --skip-build)
      SKIP_BUILD=1
      SKIP_INSTALL=1
      ;;
    --skip-install)
      SKIP_INSTALL=1
      ;;
    --skip-eval)
      SKIP_EVAL=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown flag: %s\n' "$arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "$RAW_ARTIFACTS_DIR"
exec > >(tee -a "$LOG_FILE") 2>&1

START_TS="$(date +%s)"

now_seconds() {
  date +%s
}

elapsed_since() {
  local from="$1"
  local to
  to="$(now_seconds)"
  printf '%ss' "$((to - from))"
}

step_start() {
  local step_no="$1"
  local label="$2"
  STEP_TS="$(now_seconds)"
  printf '\n[%s] Step %s: %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$step_no" "$label"
}

step_done() {
  local step_no="$1"
  local label="$2"
  local from="$3"
  printf '[%s] Step %s complete: %s (elapsed %s)\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" "$step_no" "$label" "$(elapsed_since "$from")"
}

on_error() {
  local line_no="$1"
  local total_elapsed
  total_elapsed="$(elapsed_since "$START_TS")"
  printf '\nBenchmark failed at line %s after %s.\n' "$line_no" "$total_elapsed" >&2
  printf 'Run directory: %s\n' "$RUN_DIR" >&2
  printf 'Log file: %s\n' "$LOG_FILE" >&2
}

trap 'on_error $LINENO' ERR

cd "$REPO_ROOT"

step_start 1 'prerequisites'
if [ ! -x "$REPO_ROOT/gradlew" ]; then
  printf 'Missing Gradle wrapper at %s/gradlew.\n' "$REPO_ROOT" >&2
  exit 1
fi
if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available in PATH. Install Android platform-tools and retry.\n' >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  printf 'python3 is not available in PATH. Install Python 3 and retry.\n' >&2
  exit 1
fi
device_state="$(adb get-state 2>/dev/null || true)"
if [ "$device_state" != "device" ]; then
  printf 'No ready Android device detected by adb. Connect/unlock a device or start an emulator, then retry.\n' >&2
  exit 1
fi
step_done 1 'prerequisites' "$STEP_TS"

# The bubble detector runs from weights the test APK carries, so the benchmark does not depend on
# whatever the device happens to have downloaded. Same file and checksum ModelManager fetches.
BUBBLE_MODEL_FILE="$REPO_ROOT/app/src/androidTest/assets/models/bubble_detection.onnx"
BUBBLE_MODEL_URL="https://huggingface.co/Kiuyha/Manga-Bubble-YOLO/resolve/main/onnx/yolo26n.onnx"
if [ ! -f "$BUBBLE_MODEL_FILE" ]; then
  printf 'Fetching bubble detection weights...\n'
  mkdir -p "$(dirname "$BUBBLE_MODEL_FILE")"
  curl -sL --fail -o "$BUBBLE_MODEL_FILE" "$BUBBLE_MODEL_URL"
fi

# Bubble-detection pages ship to the device as test assets. They are gitignored (CC BY-NC),
# so copy them in from the eval cases before the build packages the test APK.
TEST_ASSETS_DIR="$REPO_ROOT/app/src/androidTest/assets/eval-cases"
for case_dir in "$SCRIPT_DIR"/bubble-detection/cases/*/; do
  [ -d "$case_dir" ] || continue
  case_id="$(basename "$case_dir")"
  page_jpg="$case_dir/page.jpg"
  [ -f "$page_jpg" ] || continue
  mkdir -p "$TEST_ASSETS_DIR/$case_id"
  cp "$page_jpg" "$TEST_ASSETS_DIR/$case_id/page.jpg"
done

if [ "$SKIP_BUILD" -eq 0 ]; then
  step_start 2 'build'
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
  step_done 2 'build' "$STEP_TS"
else
  printf '\n[%s] Step 2 skipped: build (--skip-build)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

if [ "$SKIP_INSTALL" -eq 0 ]; then
  step_start 3 'install'
  ./gradlew :app:installDebug :app:installDebugAndroidTest
  step_done 3 'install' "$STEP_TS"
  printf '\nInstall wipes app data. Download models in the app now:\n'
  printf '  Settings -> LLM engine -> Download (CAT-Translate, ~500MB)\n'
  printf '  Settings -> OPUS-MT engine -> Download (~106MB)\n'
  printf '  Re-run with: ./eval/run-benchmark.sh --skip-build --skip-install\n'
  printf '\nPress Enter when ready...\n'
  read -r
else
  printf '\n[%s] Step 3 skipped: install (--skip-install)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

printf '\n[%s] Live logcat (run in another terminal):\n' "$(date '+%Y-%m-%d %H:%M:%S')"
printf '  adb logcat -s "EngineBenchmarkTest:*" "BubbleDetectionBenchmarkTest:*" "LlamaBridge:*" "LlamaJNI:*"\n\n'

step_start 4 'device test'
adb logcat -c

adb logcat -s "EngineBenchmarkTest:*" "BubbleDetectionBenchmarkTest:*" "LlamaBridge:*" "LlamaJNI:*" > "$LOGCAT_FILE" 2>/dev/null &
LOGCAT_PID=$!

last_line=0
while kill -0 $LOGCAT_PID 2>/dev/null; do
  sleep 1
done &
TAIL_PID=$!

./gradlew :app:connectedAndroidTest 2>&1 | grep -E "Starting|completed\.|BUILD"

sleep 3
kill $LOGCAT_PID 2>/dev/null || true
kill $TAIL_PID 2>/dev/null || true
wait $LOGCAT_PID 2>/dev/null || true
step_done 4 'device test' "$STEP_TS"

step_start 5 'extract artifacts'
parse_logcat_results() {
  local logcat="$1"
  local out_dir="$2"

  while IFS= read -r line; do
    case "$line" in
      *"RESULT_JSON case="*)
        local case_id engine json_str
        case_id="$(printf '%s' "$line" | sed -n 's/.*case=\([^ ]*\).*/\1/p')"
        engine="$(printf '%s' "$line" | sed -n 's/.*engine=\([^ ]*\).*/\1/p')"
        json_str="$(printf '%s' "$line" | sed 's/.*json=//')"
        if [ -n "$case_id" ] && [ -n "$engine" ] && [ -n "$json_str" ]; then
          local case_dir="$out_dir/$case_id"
          mkdir -p "$case_dir"
          printf '%s' "$json_str" > "$case_dir/$engine.json"
        fi
        ;;
    esac
  done < "$logcat"
}

parse_logcat_results "$LOGCAT_FILE" "$RAW_ARTIFACTS_DIR/yomu-benchmark"

for case_dir in "$RAW_ARTIFACTS_DIR"/yomu-benchmark/*/; do
  [ -d "$case_dir" ] || continue
  case_id="$(basename "$case_dir")"

  bubble_json="$case_dir/bubble.json"
  if [ -f "$bubble_json" ]; then
    cp "$bubble_json" "$SCRIPT_DIR/bubble-detection/cases/$case_id/actual.json"
  fi

  target_dir="$SCRIPT_DIR/translation-quality/cases/$case_id/actual"
  mkdir -p "$target_dir"
  for engine_json in "$case_dir"/*.json; do
    [ -f "$engine_json" ] || continue
    [ "$(basename "$engine_json")" = "bubble.json" ] && continue
    cp "$engine_json" "$target_dir/$(basename "$engine_json")"
  done
done

local_timing_csv="$RAW_ARTIFACTS_DIR/yomu-benchmark/benchmark_timing.csv"
printf 'case_id,engine,line_index,duration_ms,success\n' > "$local_timing_csv"
grep 'TIMING ' "$LOGCAT_FILE" | while IFS= read -r line; do
  case_id="$(printf '%s' "$line" | sed -n 's/.*case=\([^ ]*\).*/\1/p')"
  engine="$(printf '%s' "$line" | sed -n 's/.*engine=\([^ ]*\).*/\1/p')"
  line_idx="$(printf '%s' "$line" | sed -n 's/.*line=\([^ ]*\).*/\1/p')"
  duration="$(printf '%s' "$line" | sed -n 's/.*durationMs=\([^ ]*\).*/\1/p')"
  success="$(printf '%s' "$line" | sed -n 's/.*success=\([^ ]*\).*/\1/p')"
  printf '%s,%s,%s,%s,%s\n' "$case_id" "$engine" "$line_idx" "$duration" "$success" >> "$local_timing_csv"
done

printf 'Artifacts extracted from logcat to %s\n' "$RAW_ARTIFACTS_DIR"
printf 'Logcat log: %s\n' "$LOGCAT_FILE"

print_stats_table() {
  printf '\n========== Benchmark Results ==========\n'
  printf '%-10s %8s %10s %10s %10s\n' "Engine" "Lines" "Avg" "Min" "Max"
  printf '%-10s %8s %10s %10s %10s\n' "------" "------" "-------" "-------" "-------"
  for engine in mlkit opusmt llm; do
    grep "Result engine=$engine " "$LOGCAT_FILE" | sed -n 's/.*durationMs=\([0-9]*\).*/\1/p' > "/tmp/yomu-stats-$engine.$$"
    local line_count=0 sum=0 min=99999999 max=0
    while IFS= read -r d; do
      [ -z "$d" ] && continue
      line_count=$((line_count + 1))
      sum=$((sum + d))
      [ "$d" -lt "$min" ] && min=$d
      [ "$d" -gt "$max" ] && max=$d
    done < "/tmp/yomu-stats-$engine.$$"
    rm -f "/tmp/yomu-stats-$engine.$$"
    if [ "$line_count" -gt 0 ]; then
      printf '%-10s %8d %5dms %5dms %5dms\n' "$engine" "$line_count" "$((sum / line_count))" "$min" "$max"
    else
      grep "Engine $engine not ready" "$LOGCAT_FILE" >/dev/null 2>&1 && \
        printf '%-10s %8s %10s %10s %10s\n' "$engine" "--" "skipped" "--" "--" || \
        printf '%-10s %8s %10s %10s %10s\n' "$engine" "--" "no data" "--" "--"
    fi
  done
}

print_stats_table
printf '\n'
step_done 5 'extract artifacts' "$STEP_TS"

EVAL_RESULT_PATH=''
if [ "$SKIP_EVAL" -eq 0 ]; then
  step_start 6 'score results'
  eval_stdout_file="$RUN_DIR/run-eval-output.log"
  python3 "$SCRIPT_DIR/run-eval.py" | tee "$eval_stdout_file"
  while IFS= read -r line; do
    case "$line" in
      "Results written to "*)
        EVAL_RESULT_PATH="${line#Results written to }"
        ;;
    esac
  done < "$eval_stdout_file"
  if [ -n "$EVAL_RESULT_PATH" ] && [ -f "$EVAL_RESULT_PATH" ]; then
    cp "$EVAL_RESULT_PATH" "$RUN_DIR/scored-results.json"
  else
    printf 'Could not determine scored results path from eval output.\n' >&2
    exit 1
  fi
  step_done 6 'score results' "$STEP_TS"
else
  printf '\n[%s] Step 6 skipped: score results (--skip-eval)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

step_start 7 'summary'
printf 'Run directory: %s\n' "$RUN_DIR"
printf 'Benchmark log: %s\n' "$LOG_FILE"
printf 'Logcat log: %s\n' "$LOGCAT_FILE"
printf 'Raw artifacts: %s\n' "$RAW_ARTIFACTS_DIR"
if [ "$SKIP_EVAL" -eq 0 ]; then
  printf 'Scored results: %s\n' "$RUN_DIR/scored-results.json"
fi
printf 'Total elapsed: %s\n' "$(elapsed_since "$START_TS")"
step_done 7 'summary' "$STEP_TS"