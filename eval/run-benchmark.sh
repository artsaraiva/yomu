#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

./gradlew :app:connectedAndroidTest

adb pull /sdcard/Download/yomu-benchmark/ ./eval/benchmark-output/

for case_dir in ./eval/benchmark-output/*/; do
    case_id="$(basename "$case_dir")"
    target_dir="eval/translation-quality/cases/$case_id/actual"
    mkdir -p "$target_dir"
    for engine_json in "$case_dir"/*.json; do
        [ -e "$engine_json" ] || continue
        cp "$engine_json" "$target_dir/$(basename "$engine_json")"
    done
done

python3 eval/run-eval.py
