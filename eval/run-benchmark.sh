#!/usr/bin/env bash
set -eEuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$REPO_ROOT/eval"
APP_ID="com.yomu.app"
# The run id is the directory name on both sides. It is unique per run, and every record carries it,
# so a prior run's outputs can never be scored as this one's (#58).
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$SCRIPT_DIR/benchmark-results/$RUN_ID"
LOG_FILE="$RUN_DIR/benchmark.log"
# Diagnostic only. The scorer never reads it (#142).
LOGCAT_FILE="$RUN_DIR/logcat.log"

SKIP_BUILD=0
SKIP_INSTALL=0
SKIP_EVAL=0

usage() {
  printf 'Usage: %s [--skip-build] [--skip-install] [--skip-eval] [-P<gradle-arg>...]\n' "$0"
  printf '\n'
  printf 'Flags:\n'
  printf '  --skip-build    Skip build (use installed APKs as-is)\n'
  printf '  --skip-install  Skip install only, still build. Use if model already downloaded.\n'
  printf '  --skip-eval     Skip scoring step\n'
  printf '  -P<arg>         Passed straight to the connectedAndroidTest gradle call, e.g.\n'
  printf '                  -Pandroid.testInstrumentationRunnerArguments.challengers=hunyuan_mt_7b\n'
}

# Gradle -P args (e.g. instrumentation runner arguments) are collected here and forwarded to the
# connectedAndroidTest invocation; the runner reads them to filter engines / skip the baseline (#84).
GRADLE_EXTRA_ARGS=()
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
    -P*)
      GRADLE_EXTRA_ARGS+=("$arg")
      ;;
    *)
      printf 'Unknown flag: %s\n' "$arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "$RUN_DIR"
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
# Score through an interpreter that has eval/requirements.txt, and stop if it does not (#156).
# A bare python3 falls through run_eval_lib's `except ImportError` to CHRF = None and every run
# reports mean_chrf: null -- which reads identically to "no bubbles scored". chrF is a registered
# metric in eval-contract.json, so a silent null is the contract reading nothing at all.
# PYTHON_BIN overrides, matching run-prompt-benchmark.sh; otherwise the eval virtualenv, if present.
if [ -n "${PYTHON_BIN:-}" ]; then
  PYTHON="$PYTHON_BIN"
elif [ -x "$SCRIPT_DIR/.venv/bin/python" ]; then
  PYTHON="$SCRIPT_DIR/.venv/bin/python"
else
  PYTHON="python3"
fi
if ! "$PYTHON" -c 'import sacrebleu' >/dev/null 2>&1; then
  printf 'Scoring interpreter %s has no sacrebleu, so mean_chrf would be recorded as null.\n' "$PYTHON" >&2
  printf 'Create the eval virtualenv once:\n' >&2
  printf '  python3 -m venv eval/.venv && eval/.venv/bin/pip install -r eval/requirements.txt\n' >&2
  printf 'Or point PYTHON_BIN at an interpreter that already has it.\n' >&2
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

# #57: the yolo26s candidate ships as a second detector asset so one run scores both detectors on
# identical pages. BubbleDetectionBenchmarkTest skips it if absent, so removing this block reverts to
# incumbent-only scoring. Same author/loader as the incumbent, single Text class, 20.3MB vs 6.1MB.
BUBBLE_S_MODEL_FILE="$REPO_ROOT/app/src/androidTest/assets/models/bubble_detection_s.onnx"
BUBBLE_S_MODEL_URL="https://huggingface.co/Kiuyha/Manga-Bubble-YOLO/resolve/main/onnx/yolo26s.onnx"
if [ ! -f "$BUBBLE_S_MODEL_FILE" ]; then
  printf 'Fetching yolo26s candidate weights (~20MB, once)...\n'
  mkdir -p "$(dirname "$BUBBLE_S_MODEL_FILE")"
  curl -sL --fail -o "$BUBBLE_S_MODEL_FILE" "$BUBBLE_S_MODEL_URL"
fi

# Case data ships to the device as test assets. It is gitignored (CC BY-NC), so copy it in from
# the eval cases before the build packages the test APK. Detection needs page.jpg and the engine
# benchmark needs source.txt; both are staged in one pass so a newly added case can never arrive
# half-staged. (It did once: growing the set to 17 cases left 9 of them with a page and no
# source.txt, and EngineBenchmarkTest died on the first one it reached.)
TEST_ASSETS_DIR="$REPO_ROOT/app/src/androidTest/assets/eval-cases"
rm -rf "$TEST_ASSETS_DIR"
for case_dir in "$SCRIPT_DIR"/bubble-detection/cases/*/; do
  [ -d "$case_dir" ] || continue
  case_id="$(basename "$case_dir")"
  page_jpg="$case_dir/page.jpg"
  expected_json="$case_dir/expected.json"
  source_txt="$SCRIPT_DIR/translation-quality/cases/$case_id/source.txt"
  [ -f "$page_jpg" ] || continue
  if [ ! -f "$source_txt" ]; then
    printf 'Case %s has page.jpg but no translation-quality source.txt; run eval/generate-cases.py\n' "$case_id" >&2
    exit 1
  fi
  mkdir -p "$TEST_ASSETS_DIR/$case_id"
  cp "$page_jpg" "$TEST_ASSETS_DIR/$case_id/page.jpg"
  cp "$source_txt" "$TEST_ASSETS_DIR/$case_id/source.txt"
  # The translation benchmark now makes an ADR-0004 page-level call, which needs the ground-truth
  # boxes to assemble panels + reading order, so expected.json is staged alongside source.txt.
  cp "$expected_json" "$TEST_ASSETS_DIR/$case_id/expected.json"
done

# The #152 repetition probe rides in as its own asset dir. Same reason as the cases above: it is
# derived CC BY-NC content, gitignored, so it is copied in before the build packages the test APK.
# Absent probe = the #153 penalty sweep cannot run; every other test is unaffected, so this is a
# warning rather than an abort.
PROBE_ASSET_DIR="$REPO_ROOT/app/src/androidTest/assets/eval-probe"
rm -rf "$PROBE_ASSET_DIR"
if [ -f "$SCRIPT_DIR/repetition-probe/bubbles.json" ]; then
  mkdir -p "$PROBE_ASSET_DIR"
  cp "$SCRIPT_DIR/repetition-probe/bubbles.json" "$PROBE_ASSET_DIR/bubbles.json"
else
  printf 'No eval/repetition-probe/bubbles.json; run eval/generate-repetition-probe.py to enable the probe\n' >&2
fi

# Translation models are staged as fixtures rather than downloaded through the app's Settings UI,
# which is what the old interactive prompt here was waiting for. An upgrade install keeps filesDir,
# but a signature change or uninstall/reinstall clears it, and that is exactly when a human used to
# have to re-fetch ~600MB by hand. /data/local/tmp survives all of it, so these are pushed once and
# reused; EngineBenchmarkTest copies them into filesDir on setup.
# Anything dropped into a subdirectory of eval/fixtures/models is staged, so adding OPUS-MT weights
# later needs no change here. ML Kit is not fixturable - Play services fetches it on demand.
FIXTURE_DIR="$SCRIPT_DIR/fixtures/models"
DEVICE_FIXTURE_DIR="/data/local/tmp/yomu-fixtures"
# The `llm` enum arm loads whatever LlmModelCatalog.DEFAULT names, which ADR-0010 made
# Qwen2.5-1.5B. The fixture staged here has to be that file or the arm is never ready and the run
# reports it invalid. The 0.8b it replaced is demoted to a floor and is no longer fetched.
LLM_FIXTURE="$FIXTURE_DIR/llm/qwen25_1.5b_instruct_q4_k_m.gguf"
LLM_FIXTURE_URL="https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"

if [ ! -f "$LLM_FIXTURE" ]; then
  printf 'Fetching Qwen2.5-1.5B weights (~1GB, once)...\n'
  mkdir -p "$(dirname "$LLM_FIXTURE")"
  curl -L --fail --progress-bar -o "$LLM_FIXTURE" "$LLM_FIXTURE_URL"
fi

# #84 bake-off challengers. Same llm/ subdir, so the existing push loop stages them and
# EngineBenchmarkTest picks them up by file name. These are large (~1-5GB) and, unlike the pinned
# 0.8b, their exact GGUF revision/checksum is resolved during wiring — so a fetch that 404s or a
# candidate left un-fetched must NOT abort the run. A missing fixture is skipped by
# stageModelFixtures (absent weights => engine not-ready => skipped), which is the correct outcome.
# Fetch below is best-effort; to include a challenger, let it pull the GGUF or drop the .gguf in by
# hand at the path on the left of the `|`.
# engine-name | fixture path | url. The engine name matches EngineBenchmarkTest's Candidate name,
# so a `challengers=` run fetches and pushes ONLY those (plus the 0.8b baseline) — otherwise four+
# multi-GB GGUFs pile onto the device and ENOSPC the partition mid-run.
CHALLENGER_FIXTURES=(
  "cat_translate_1.4b|$FIXTURE_DIR/llm/cat_translate_1.4b_q4_k_m.gguf|https://huggingface.co/mradermacher/CAT-Translate-1.4b-GGUF/resolve/main/CAT-Translate-1.4b.Q4_K_M.gguf"
  "cat_translate_1.4b_i1|$FIXTURE_DIR/llm/cat_translate_1.4b_i1_q4_k_m.gguf|https://huggingface.co/mradermacher/CAT-Translate-1.4b-i1-GGUF/resolve/main/CAT-Translate-1.4b.i1-Q4_K_M.gguf"
  "cat_translate_7b|$FIXTURE_DIR/llm/cat_translate_7b_q4_k_m.gguf|https://huggingface.co/mradermacher/CAT-Translate-7b-GGUF/resolve/main/CAT-Translate-7b.Q4_K_M.gguf"
  "translategemma_4b|$FIXTURE_DIR/llm/translategemma_4b_q4_k_m.gguf|https://huggingface.co/mradermacher/translategemma-4b-it-GGUF/resolve/main/translategemma-4b-it.Q4_K_M.gguf"
  "qwen25_1.5b|$FIXTURE_DIR/llm/qwen25_1.5b_instruct_q4_k_m.gguf|https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
  "gemma2_2b|$FIXTURE_DIR/llm/gemma2_2b_it_q4_k_m.gguf|https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
  "qwen3_4b|$FIXTURE_DIR/llm/qwen3_4b_q4_k_m.gguf|https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf"
  "hunyuan_mt_7b|$FIXTURE_DIR/llm/hunyuan_mt_7b_q3_k_m.gguf|https://huggingface.co/mradermacher/Hunyuan-MT-7B-GGUF/resolve/main/Hunyuan-MT-7B.Q3_K_M.gguf"
)

# Pull the challengers= filter out of the forwarded -P args, if present.
REQUESTED=""
for a in ${GRADLE_EXTRA_ARGS[@]+"${GRADLE_EXTRA_ARGS[@]}"}; do
  case "$a" in
    *testInstrumentationRunnerArguments.challengers=*) REQUESTED=",${a##*challengers=}," ;;
  esac
done
requested() { # engine name -> 0 if it should run this pass (no filter = all)
  [ -z "$REQUESTED" ] && return 0
  case "$REQUESTED" in *",$1,"*) return 0 ;; *) return 1 ;; esac
}

# LLM fixtures allowed on the device this pass: the 0.8b baseline plus the requested challengers.
# Everything else is pruned from the device below so a targeted run does not carry old multi-GB
# weights it will not use.
ALLOWED_LLM=$'\n'"$(basename "$LLM_FIXTURE")"$'\n'
for entry in "${CHALLENGER_FIXTURES[@]}"; do
  name="${entry%%|*}"; rest="${entry#*|}"; path="${rest%%|*}"; url="${rest##*|}"
  requested "$name" || continue
  ALLOWED_LLM="${ALLOWED_LLM}$(basename "$path")"$'\n'
  [ -f "$path" ] && continue
  printf 'Fetching challenger %s (best-effort, large)...\n' "$(basename "$path")"
  mkdir -p "$(dirname "$path")"
  if ! curl -L --fail --progress-bar -o "$path" "$url"; then
    rm -f "$path"
    printf 'Challenger %s unavailable; it will be skipped in this run.\n' "$(basename "$path")" >&2
  fi
done

# Prune device llm fixtures not needed this pass, so the push + per-model staging have room.
for dev_file in $(adb shell "ls '$DEVICE_FIXTURE_DIR/llm' 2>/dev/null" | tr -d '\r'); do
  case "$ALLOWED_LLM" in
    *$'\n'"$dev_file"$'\n'*) : ;;
    *) printf 'Pruning unused device fixture llm/%s\n' "$dev_file"
       adb shell "rm -f '$DEVICE_FIXTURE_DIR/llm/$dev_file'" ;;
  esac
done

for model_dir in "$FIXTURE_DIR"/*/; do
  [ -d "$model_dir" ] || continue
  subdir="$(basename "$model_dir")"
  adb shell mkdir -p "$DEVICE_FIXTURE_DIR/$subdir"
  for model_file in "$model_dir"*; do
    [ -f "$model_file" ] || continue
    file_name="$(basename "$model_file")"
    # Skip llm fixtures not requested this pass (matched against the allow-list built above).
    if [ "$subdir" = "llm" ]; then
      case "$ALLOWED_LLM" in
        *$'\n'"$file_name"$'\n'*) : ;;
        *) continue ;;
      esac
    fi
    local_size="$(wc -c < "$model_file" | tr -d ' ')"
    device_size="$(adb shell "stat -c %s '$DEVICE_FIXTURE_DIR/$subdir/$file_name' 2>/dev/null" | tr -d '\r' || true)"
    if [ "$local_size" = "$device_size" ]; then
      printf 'Fixture %s/%s already on device, skipping push\n' "$subdir" "$file_name"
      continue
    fi
    printf 'Pushing fixture %s/%s (%s bytes)...\n' "$subdir" "$file_name" "$local_size"
    adb push "$model_file" "$DEVICE_FIXTURE_DIR/$subdir/$file_name" >/dev/null
  done
  # The instrumentation runs as the app UID, not shell, so it must be able to read these.
  adb shell chmod -R 755 "$DEVICE_FIXTURE_DIR" || true
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
  printf '\nIf install cleared app data, the staged fixtures in %s are unaffected\n' "$DEVICE_FIXTURE_DIR"
  printf 'and EngineBenchmarkTest restages them, so no in-app download is needed.\n'
else
  printf '\n[%s] Step 3 skipped: install (--skip-install)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

printf '\n[%s] Live logcat (run in another terminal):\n' "$(date '+%Y-%m-%d %H:%M:%S')"
printf '  adb logcat -s "EngineBenchmarkTest:*" "BubbleDetectionBenchmarkTest:*" "LlamaBridge:*" "LlamaTranslationBridge:*" "LlamaJNI:*" "OpusMtTranslator:*" "OpusMtTranslationBridge:*"\n\n'

step_start 4 'plan the run'
# The manifest is the host's independent statement of what this run should produce: which arms, with
# which model artefacts and call shapes, over which cases and bubble ids. The device records what it
# actually did, and run_records.py refuses to score a run where the two disagree. Both halves are
# needed — manifest-only call-shape metadata is the provenance mistake ADR-0010 corrected (#142).
SKIP_BASELINE=0
for a in ${GRADLE_EXTRA_ARGS[@]+"${GRADLE_EXTRA_ARGS[@]}"}; do
  case "$a" in *testInstrumentationRunnerArguments.skipBaseline=true) SKIP_BASELINE=1 ;; esac
done

ARM_ARGS=()
add_arm() { ARM_ARGS+=(--arm "$1"); }

# Detection arms. Weights ride into the test APK as assets, so an arm exists exactly when its asset
# was staged above.
add_arm "arm_id=bubble,stage=detection,provider=onnxruntime,model_id=yolo26n,quantization=fp32,target_language=,call_shape=page_image,model_file=$BUBBLE_MODEL_FILE"
if [ -f "$BUBBLE_S_MODEL_FILE" ]; then
  add_arm "arm_id=bubble_s,stage=detection,provider=onnxruntime,model_id=yolo26s,quantization=fp32,target_language=,call_shape=page_image,model_file=$BUBBLE_S_MODEL_FILE"
fi

if [ "$SKIP_BASELINE" -eq 0 ]; then
  # ADR-0004 floors. ML Kit is the managed provider the contract allows a null artefact hash for,
  # since Play services fetches its model on demand; its package version stands in for the hash.
  MLKIT_VERSION="$(sed -n 's/.*com\.google\.mlkit:translate:\([^"]*\)".*/\1/p' "$REPO_ROOT/app/build.gradle.kts" | head -1)"
  add_arm "arm_id=mlkit,stage=translation,provider=mlkit,provider_version=${MLKIT_VERSION:-unknown},model_id=mlkit-nl-translate-ja-en,quantization=n/a,call_shape=per_bubble"
  # OPUS-MT is deliberately NOT declared. It is the preferred self-contained floor (ADR-0008, which
  # rejected deleting it) and its weights are staged, but DJL ships no arm64-v8a
  # libdjl_tokenizer.so, so it cannot load on any target device until #14 lands. Declaring it would
  # mark every run invalid for a reason no run can fix, and a permanent red is a signal everyone
  # learns to ignore — which is how #36 and #58 survived. When #14 lands, restore:
  #   add_arm "arm_id=opusmt,stage=translation,provider=opusmt,provider_version=onnx-local,model_id=opus-mt-ja-en,quantization=n/a,call_shape=per_bubble"
  # The `llm` arm is whatever LlmModelCatalog.DEFAULT names, on its per-line call shape.
  add_arm "arm_id=llm,stage=translation,provider=llama.cpp,model_id=$(basename "$LLM_FIXTURE"),call_shape=per_line,model_file=$LLM_FIXTURE"
fi

# #84 challengers: declared only when their fixture is actually on disk, matching the device's
# skip-a-missing-fixture behaviour. All run the id-keyed batch path.
for entry in "${CHALLENGER_FIXTURES[@]}"; do
  name="${entry%%|*}"; rest="${entry#*|}"; path="${rest%%|*}"
  requested "$name" || continue
  [ -f "$path" ] || continue
  add_arm "arm_id=$name,stage=translation,provider=llama.cpp,model_id=$(basename "$path"),call_shape=id_keyed_batch,model_file=$path"
done

DEVICE_MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
ANDROID_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
APP_APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$REPO_ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
APK_ARGS=()
[ -f "$APP_APK" ] && APK_ARGS+=(--app-apk "$APP_APK")
[ -f "$TEST_APK" ] && APK_ARGS+=(--test-apk "$TEST_APK")

"$PYTHON" "$SCRIPT_DIR/run_records.py" manifest \
  --run-id "$RUN_ID" \
  --started-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --out "$RUN_DIR/manifest.json" \
  --device-model "$DEVICE_MODEL" \
  --android-api "$ANDROID_API" \
  ${APK_ARGS[@]+"${APK_ARGS[@]}"} \
  "${ARM_ARGS[@]}"
step_done 4 'plan the run' "$STEP_TS"

step_start 5 'device test'
# Clear any previous run directory on device so an aborted earlier attempt cannot contribute records.
adb shell "run-as $APP_ID rm -rf files/yomu-benchmark" >/dev/null 2>&1 || true
adb logcat -c

adb logcat -s "EngineBenchmarkTest:*" "BubbleDetectionBenchmarkTest:*" "LlamaBridge:*" "LlamaTranslationBridge:*" "LlamaJNI:*" "OpusMtTranslator:*" "OpusMtTranslationBridge:*" > "$LOGCAT_FILE" 2>/dev/null &
LOGCAT_PID=$!

last_line=0
while kill -0 $LOGCAT_PID 2>/dev/null; do
  sleep 1
done &
TAIL_PID=$!

# A failing test must not skip artifact extraction: when one half of the benchmark breaks, the
# other half's results are exactly what is needed to debug it. Record the status and report it at
# the end instead of letting `set -e` abort here.
# PIPESTATUS[0] is gradle's own status, so a grep that matches nothing cannot be mistaken for a
# test failure, and pipefail cannot mask one.
set +e
# leaveApksInstalledAfterRun is load-bearing, not a convenience: AGP uninstalls both APKs when the
# task finishes, and uninstalling takes the app's files directory -- and the run records inside it --
# with it. Without this the extraction below always finds nothing, however well the run went.
./gradlew :app:connectedAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.runId=$RUN_ID" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  ${GRADLE_EXTRA_ARGS[@]+"${GRADLE_EXTRA_ARGS[@]}"} 2>&1 | grep -E "Starting|completed\.|BUILD"
TEST_STATUS=${PIPESTATUS[0]}
set -e

sleep 3
kill $LOGCAT_PID 2>/dev/null || true
kill $TAIL_PID 2>/dev/null || true
wait $LOGCAT_PID 2>/dev/null || true
step_done 5 'device test' "$STEP_TS"

step_start 6 'extract run records'
# The device wrote records under its own files dir, which is not world-readable; run-as is the only
# way in without root. Nothing is scraped from logcat any more (#142) -- its chatty filter silently
# dropped 12 of 22 probe bubbles the one time this run relied on it (#152).
# A failed extraction is its own diagnosis and must say so. Scoring on regardless would report every
# arm as "no records" -- true, but it buries the one fact that explains all of them.
if ! adb exec-out "run-as $APP_ID tar c -C files yomu-benchmark/$RUN_ID" \
     | tar x -C "$RUN_DIR" --strip-components=2; then
  printf 'Could not extract files/yomu-benchmark/%s from the device.\n' "$RUN_ID" >&2
  if ! adb shell "pm list packages" | grep -q "^package:$APP_ID$"; then
    printf '%s is not installed. The run records live in its files directory, so an uninstall\n' "$APP_ID" >&2
    printf 'between the test and this step destroys them.\n' >&2
  fi
  exit 1
fi
# COMPLETE pins the record count and the records SHA-256, so a truncated extraction or a file
# appended to afterwards is rejected rather than scored.
"$PYTHON" "$SCRIPT_DIR/run_records.py" complete --run-dir "$RUN_DIR"
printf 'Run records: %s\n' "$RUN_DIR/records.jsonl"
printf 'Logcat (diagnostic only): %s\n' "$LOGCAT_FILE"
step_done 6 'extract run records' "$STEP_TS"

EVAL_STATUS=0
if [ "$SKIP_EVAL" -eq 0 ]; then
  step_start 7 'score results'
  eval_stdout_file="$RUN_DIR/run-eval-output.log"
  # Scores the run directory in place. No outputs are copied into eval/**/actual* -- that sharing is
  # what let a skipped engine be scored against a prior run (#58).
  set +e
  "$PYTHON" "$SCRIPT_DIR/run-eval.py" --run-dir "$RUN_DIR" | tee "$eval_stdout_file"
  EVAL_STATUS=${PIPESTATUS[0]}
  set -e
  EVAL_RESULT_PATH=''
  while IFS= read -r line; do
    case "$line" in
      "Results written to "*) EVAL_RESULT_PATH="${line#Results written to }" ;;
    esac
  done < "$eval_stdout_file"
  if [ -n "$EVAL_RESULT_PATH" ] && [ -f "$EVAL_RESULT_PATH" ]; then
    cp "$EVAL_RESULT_PATH" "$RUN_DIR/scored-results.json"
  fi

  # #57: if the yolo26s candidate ran, print the paired detector comparison and the pre-registered
  # verdict that feeds #33. No-op when only the incumbent was scored.
  if grep -q '"arm_id": "bubble_s"' "$RUN_DIR/manifest.json" 2>/dev/null; then
    detector_cmp_file="$RUN_DIR/detector-comparison.txt"
    "$PYTHON" "$SCRIPT_DIR/score-detector-comparison.py" --run-dir "$RUN_DIR" | tee "$detector_cmp_file" || true
  fi
  step_done 7 'score results' "$STEP_TS"
else
  printf '\n[%s] Step 7 skipped: score results (--skip-eval)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
fi

step_start 8 'summary'
printf 'Run id: %s\n' "$RUN_ID"
printf 'Run directory: %s\n' "$RUN_DIR"
printf 'Benchmark log: %s\n' "$LOG_FILE"
printf 'Logcat log (diagnostic only): %s\n' "$LOGCAT_FILE"
printf 'Manifest: %s\n' "$RUN_DIR/manifest.json"
printf 'Records: %s\n' "$RUN_DIR/records.jsonl"
if [ "$SKIP_EVAL" -eq 0 ]; then
  printf 'Scored results: %s\n' "$RUN_DIR/scored-results.json"
fi
printf 'Total elapsed: %s\n' "$(elapsed_since "$START_TS")"
step_done 8 'summary' "$STEP_TS"

if [ "$TEST_STATUS" -ne 0 ]; then
  printf '\nThe device test reported failures; records above were still extracted.\n' >&2
  printf 'Per-test detail: app/build/reports/androidTests/connected/\n' >&2
  exit "$TEST_STATUS"
fi

# A requested arm the scorer rejected must not exit green, even when every other arm scored (#142).
if [ "$EVAL_STATUS" -ne 0 ]; then
  printf '\nScoring reported invalid arms; see the summary above.\n' >&2
  exit "$EVAL_STATUS"
fi