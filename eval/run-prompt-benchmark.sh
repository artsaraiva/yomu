#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PYTHON_BIN="${PYTHON_BIN:-python3}"
"$PYTHON_BIN" -c 'import sacrebleu' || { echo 'Install eval/requirements.txt first'; exit 1; }
RUN="$ROOT/eval/results/prompt-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN/raw" app/src/androidTest/assets/eval-cases
export RUN
"$PYTHON_BIN" - <<'PY'
from pathlib import Path
import shutil
assets = Path('app/src/androidTest/assets/eval-cases')
shutil.rmtree(assets)
for source in sorted(Path('eval/translation-quality/cases').glob('*/source.txt')):
    target = assets / source.parent.name
    target.mkdir(parents=True)
    shutil.copy(source, target / 'source.txt')
    shutil.copy(Path('eval/bubble-detection/cases') / source.parent.name / 'expected.json', target / 'expected.json')
PY
if [[ "${1:-}" != --skip-build ]]; then
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pandroid.injected.build.abi=arm64-v8a --console=plain > "$RUN/build.log" 2>&1
fi
APK_ROOT=app/build/outputs/apk
if [[ ! -f "$APK_ROOT/debug/app-debug.apk" ]]; then APK_ROOT=app/build/intermediates/apk; fi
adb install -r -t "$APK_ROOT/debug/app-debug.apk"
adb install -r -t "$APK_ROOT/androidTest/debug/app-debug-androidTest.apk"
{
  git rev-parse HEAD
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell run-as com.yomu.app sha256sum files/models/llm/qwen25_1.5b_instruct_q4_k_m.gguf
  "$PYTHON_BIN" -c 'import sacrebleu; print("sacrebleu", sacrebleu.__version__)'
} > "$RUN/manifest.txt"
git diff HEAD > "$RUN/working.diff"
adb logcat -T 1 -v threadtime LlamaTranslationBridge:I LlamaJNI:I EngineBenchmark:I AndroidRuntime:E '*:S' > "$RUN/logcat.log" &
LOG_PID=$!
trap 'kill "$LOG_PID" 2>/dev/null || true' EXIT
adb shell am instrument -w -r -e class com.yomu.app.EngineBenchmarkTest#comparePromptModes com.yomu.app.test/com.yomu.app.CustomTestRunner | tee "$RUN/instrumentation.log"
adb exec-out run-as com.yomu.app tar -cf - files/yomu-prompt-benchmark | tar -xf - -C "$RUN/raw"
"$PYTHON_BIN" - <<'PY'
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
sys.path.insert(0, 'eval')
import run_eval_lib as lib
run = Path(os.environ['RUN'])
lib.TRANS_CASES = run / 'cases'
manifest = {}
for source in sorted(Path('eval/translation-quality/cases').glob('*/source.txt')):
    case = lib.TRANS_CASES / source.parent.name
    case.mkdir(parents=True)
    for name in ('source.txt', 'reference.txt'):
        shutil.copy(source.parent / name, case / name)
        manifest[f'{source.parent.name}/{name}'] = hashlib.sha256((case / name).read_bytes()).hexdigest()
    actual = run / 'raw/files/yomu-prompt-benchmark' / source.parent.name
    if actual.exists():
        shutil.copytree(actual, case / 'actual')
result = lib.run_translation_quality(False)
(run / 'corpus-sha256.json').write_text(json.dumps(manifest, indent=2))
(run / 'scores.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result['summary'], indent=2))
expected = {'qwen_model_card', 'qwen_translation_only', 'qwen_capture_context'}
engines = result['summary']['engines']
if set(engines) != expected or any(s['completed_cases'] != s['expected_cases'] for s in engines.values()):
    raise SystemExit('Incomplete benchmark: inspect raw artifacts')
PY
grep -q 'OK (1 test)' "$RUN/instrumentation.log"
printf '\nBenchmark artifacts: %s\n' "$RUN"
