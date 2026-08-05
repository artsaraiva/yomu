#!/usr/bin/env python3
"""Offline eval harness for Yomu Phase 1.

Scores bubble-detection and translation-quality cases. Engine outputs can be
supplied as JSON; when they are absent, the harness runs in stub mode with
synthetic outputs to exercise the scoring logic.

On-device engines (ML Kit, OPUS-MT, LLM) are invoked via Android instrumentation,
not from this script. Collect their outputs into the expected JSON format and
point this harness at them.
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from run_eval_lib import run_bubble_detection, run_translation_quality

RESULTS_DIR = ROOT / "results"


def print_summary(bubble: dict, translation: dict) -> None:
    print("=" * 60)
    print("Yomu Phase 1 Eval Summary")
    print("=" * 60)

    print("\nBubble Detection")
    print(f"  Cases: {len(bubble['cases'])}")
    if bubble["cases"]:
        print(f"  Avg recall@0.5: {bubble['summary']['avg_recall_iou_0_5']:.3f}")
        print(f"  Total false positives: {bubble['summary']['total_false_positives']}")
        print(f"  Total missed: {bubble['summary']['total_missed']}")
        print("  Per-case:")
        for c in bubble["cases"]:
            print(
                f"    {c['case_id']}: recall={c['recall_iou_0_5']:.3f} "
                f"matched={c['matched']}/{c['expected_count']} "
                f"fp={c['false_positives']} mode={c['mode']}"
            )

    print("\nTranslation Quality")
    print(f"  Cases: {len(translation['cases'])}")
    if translation["summary"]["engines"]:
        for engine, s in translation["summary"]["engines"].items():
            print(f"  Engine: {engine}")
            print(f"    Avg untranslated rate: {s['avg_untranslated_rate']:.3f}")
            print(f"    Avg artifact rate:     {s['avg_artifact_rate']:.3f}")
            print(f"    Avg exact match rate:  {s['avg_exact_match_rate']:.3f}")
            print(f"    Avg readability ratio: {s['avg_readability_ratio']:.3f}")
    else:
        print("  No engine outputs scored.")

    for c in translation["cases"]:
        for e in c["engines"]:
            if "error" in e:
                print(f"  {c['case_id']}/{e['engine']}: {e['error']}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Yomu Phase 1 eval harness")
    parser.add_argument(
        "--stub",
        action="store_true",
        help="Use synthetic outputs to exercise scoring logic when real engine outputs are absent",
    )
    parser.add_argument(
        "--no-bubble",
        action="store_true",
        help="Skip bubble-detection scoring",
    )
    parser.add_argument(
        "--no-translation",
        action="store_true",
        help="Skip translation-quality scoring",
    )
    args = parser.parse_args()

    bubble: dict[str, Any] = {"cases": [], "summary": {}}
    translation: dict[str, Any] = {"cases": [], "summary": {"engines": {}}}

    if not args.no_bubble:
        bubble = run_bubble_detection(args.stub)
    if not args.no_translation:
        translation = run_translation_quality(args.stub)

    print_summary(bubble, translation)

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    result_path = RESULTS_DIR / f"{timestamp}.json"
    result_path.write_text(
        json.dumps(
            {
                "timestamp": timestamp,
                "stub": args.stub,
                "bubble_detection": bubble,
                "translation_quality": translation,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to {result_path}")


if __name__ == "__main__":
    main()
