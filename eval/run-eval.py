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

from run_eval_lib import SEPARATION_THRESHOLD, run_bubble_detection, run_translation_quality

RESULTS_DIR = ROOT / "results"


def print_summary(bubble: dict, translation: dict) -> None:
    print("=" * 60)
    print("Yomu Phase 1 Eval Summary")
    print("=" * 60)

    print("\nBubble Detection")
    print(f"  Cases: {len(bubble['cases'])}")
    if bubble["cases"]:
        summary = bubble["summary"]
        cover = bubble["cover_text"]
        print(
            f"  GATE - story containment recall: {summary['total_containment_recall']:.3f} "
            f"({summary['total_expected']} boxes, {summary['case_count']} cases)"
        )
        print(f"  Localisation recall (reported, never gated): "
              f"{summary['total_localisation_recall']:.3f}")
        print(f"  Merging detections (diagnostic): {summary['total_merging_detections']}")
        print(f"  False positives: {summary['total_false_positives']}")
        print(f"  Missed: {summary['total_missed']}")
        if cover["case_count"]:
            print(
                f"  Cover text (reported, NOT gated): "
                f"{cover['total_containment_recall']:.3f} "
                f"({cover['total_expected']} boxes, {cover['case_count']} cases)"
            )
        print(
            f"  Separation rule: a rival detector counts as better only at "
            f">= {SEPARATION_THRESHOLD:.0%} gate difference; below that, tie-break on "
            f"licence/size/latency."
        )
        # A case with no actual.json scores zero and would silently drag the gate down, which is
        # the "green harness measuring nothing" failure in reverse. Say so loudly.
        missing = [c["case_id"] for c in bubble["cases"] if c["mode"] == "missing"]
        if missing:
            print(
                f"  WARNING: {len(missing)} case(s) have no actual.json and scored 0 - "
                f"the gate above is NOT comparable until run-benchmark.sh regenerates them: "
                f"{', '.join(missing)}"
            )
        print("  Per-case (diagnostic only, never averaged):")
        for c in bubble["cases"]:
            print(
                f"    {c['case_id']} [{c['kind']}]: containment={c['containment_recall']:.3f} "
                f"matched={c['matched']}/{c['expected_count']} "
                f"localised={c['localised']} merged={c['merging_detections']} "
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
