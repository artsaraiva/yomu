#!/usr/bin/env bash
set -eEuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$REPO_ROOT/eval"
DEVICE_BENCHMARK_DIR="/sdcard/Android/data/com.yomu.app/files/yomu-benchmark"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$SCRIPT_DIR/benchmark-results/$TIMESTAMP"
RAW_ARTIFACTS_DIR="$RUN_DIR/raw-artifacts"
LOG_FILE="$RUN_DIR/benchmark.log"

SKIP_BUILD=0
SKIP_EVAL=0

usage() {
  printf 'Usage: %s [--skip-build] [--skip-eval]\n' "$0"
}

for arg in "$@"; do
  case "$arg" in
    --skip-build)
      SKIP_BUILD=1
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

if [ "$SKIP_BUILD" -eq 0 ]; then
  step_start 2 'build/install'
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:installDebug :app:installDebugAndroidTest
  step_done 2 'build/install' "$STEP_TS"
else
  printf '\n[%s] Step 2 skipped: build/install (--skip-build)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

step_start 3 'device test'
./gradlew :app:connectedAndroidTest
step_done 3 'device test' "$STEP_TS"

step_start 4 'pull artifacts'
adb pull "$DEVICE_BENCHMARK_DIR" "$RAW_ARTIFACTS_DIR"

for case_dir in "$RAW_ARTIFACTS_DIR"/yomu-benchmark/*/; do
  [ -d "$case_dir" ] || continue
  case_id="$(basename "$case_dir")"
  target_dir="$SCRIPT_DIR/translation-quality/cases/$case_id/actual"
  mkdir -p "$target_dir"
  for engine_json in "$case_dir"/*.json; do
    [ -f "$engine_json" ] || continue
    cp "$engine_json" "$target_dir/$(basename "$engine_json")"
  done
done
step_done 4 'pull artifacts' "$STEP_TS"

EVAL_RESULT_PATH=''
if [ "$SKIP_EVAL" -eq 0 ]; then
  step_start 5 'score results'
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
  step_done 5 'score results' "$STEP_TS"
else
  printf '\n[%s] Step 5 skipped: score results (--skip-eval)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

step_start 6 'summary'
printf 'Run directory: %s\n' "$RUN_DIR"
printf 'Benchmark log: %s\n' "$LOG_FILE"
printf 'Raw artifacts: %s\n' "$RAW_ARTIFACTS_DIR"
if [ "$SKIP_EVAL" -eq 0 ]; then
  printf 'Scored results: %s\n' "$RUN_DIR/scored-results.json"
fi
printf 'Total elapsed: %s\n' "$(elapsed_since "$START_TS")"
step_done 6 'summary' "$STEP_TS"
