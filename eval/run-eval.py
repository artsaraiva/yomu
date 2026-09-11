#!/usr/bin/env python3
"""Offline eval harness for Yomu Phase 1.

Scores bubble-detection and translation-quality cases from a **structured run directory** written by
`run-benchmark.sh` (`--run-dir`): a host-written `manifest.json`, the device's `records.jsonl`, and
a `COMPLETE` marker. `run_records.validate_run` checks all three against each other before a single
aggregate is computed; see `eval/SCHEMA.md`.

`--stub` remains, and is the only other mode: synthetic perfect outputs that exercise the scoring
logic without a device. There is no path that reads shared `eval/**/actual*` files — those are gone,
because a skipped engine scored against a prior run's leftovers is how #58 shipped.

Exit status is nonzero when any requested arm is invalid, even though the valid arms are still
reported.
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

import run_records
from run_eval_lib import (
    PROBE_MIN_RUN,
    SEPARATION_THRESHOLD,
    run_bubble_detection,
    run_repetition_probe,
    run_translation_quality,
)

RESULTS_DIR = ROOT / "results"


def print_summary(bubble: dict, translation: dict, probe: dict) -> None:
    print("=" * 60)
    print("Yomu Phase 1 Eval Summary")
    print("=" * 60)

    print("\nBubble Detection")
    for arm_id, arm in sorted(bubble.get("arms", {}).items()):
        print(f"  Arm: {arm_id}  ({len(arm['cases'])} cases)")
        if not arm["valid"]:
            # No aggregate for an invalid arm. Printing one would be the failure this whole
            # contract exists to prevent (#36, #58).
            print("    INVALID - no aggregate is reported for this arm:")
            for error in arm["errors"]:
                print(f"      - {error}")
            continue
        if not arm["cases"]:
            continue
        summary = arm["summary"]
        cover = arm["cover_text"]
        print(
            f"    GATE - story containment recall: {summary['total_containment_recall']:.3f} "
            f"({summary['total_expected']} boxes, {summary['case_count']} cases)"
        )
        print(f"    Localisation recall (reported, never gated): "
              f"{summary['total_localisation_recall']:.3f}")
        print(f"    Merging detections (diagnostic): {summary['total_merging_detections']}")
        print(f"    False positives: {summary['total_false_positives']}")
        print(f"    Missed: {summary['total_missed']}")
        if cover["case_count"]:
            print(
                f"    Cover text (reported, NOT gated): "
                f"{cover['total_containment_recall']:.3f} "
                f"({cover['total_expected']} boxes, {cover['case_count']} cases)"
            )
        print(
            f"    Separation rule: a rival detector counts as better only at "
            f">= {SEPARATION_THRESHOLD:.0%} gate difference; below that, tie-break on "
            f"licence/size/latency."
        )
        print("    Per-case (diagnostic only, never averaged):")
        for c in arm["cases"]:
            print(
                f"      {c['case_id']} [{c['kind']}]: containment={c['containment_recall']:.3f} "
                f"matched={c['matched']}/{c['expected_count']} "
                f"localised={c['localised']} merged={c['merging_detections']} "
                f"fp={c['false_positives']} mode={c['mode']}"
            )

    print("\nTranslation Quality")
    print(f"  Cases: {len(translation['cases'])}")
    engines = translation["summary"]["engines"]
    if engines:
        # The gate is the LLM's page-level call; ML Kit / OPUS-MT are a floor, never ranked against
        # it (ADR-0004). The first page-level numbers are a PRE-ADR-0002 BASELINE: sessionContext is
        # still unread and translateBatch still prompts a bare numbered list, so the ranking survives
        # but the absolute number is not ADR-0002's (#47).
        for engine, s in sorted(engines.items(), key=lambda kv: kv[1]["role"] != "gate"):
            if not s.get("valid", True):
                print(f"  Engine: {engine}  INVALID - no aggregate is reported for this arm:")
                for error in s["errors"]:
                    print(f"    - {error}")
                continue
            if s["role"] == "gate":
                verdict = "PASS" if s["gate_pass"] else "FAIL"
                print(f"  Engine: {engine}  [OUTPUT GATE]  {verdict}")
            else:
                print(f"  Engine: {engine}  [floor - not ranked against the gate]")
            print(f"    Non-translation rate (gate 0):  {s['non_translation_rate']:.3f}")
            print(f"    Japanese-residue rate (gate 0): {s['japanese_residue_rate']:.3f}")
            print(f"    Bubble coverage (gate 100%):    {s['bubble_coverage']:.1%} "
                  f"({s['entries']} ids)")
            print(f"    Output shape (gate: no missing/extra/duplicate ids): "
                  f"{'PASS' if s['output_shape_pass'] else 'FAIL'}")
            # Coverage cannot fail on this path, so it is never printed alone (#137, #153).
            print(f"    Source echoes (coverage cross-check, never gated): "
                  f"{s['source_echo']} ({s['source_echo_rate']:.3f})")
            print(f"    Pages completed: {s['completed_cases']}/{s['expected_cases']}")
            print(f"    Mean bubble chrF2 (lexical similarity, not meaning): {s['mean_chrf']}")
            print(f"    Readability ratio (diagnostic): {s['readability_ratio']:.3f}")
            print("    Semantic accuracy: not scored here - see eval/semantic-review-120.md")
    else:
        print("  No engine outputs scored.")

    for c in translation["cases"]:
        for e in c["engines"]:
            if "error" in e:
                print(f"  {c['case_id']}/{e['engine']}: {e['error']}")

    # Reported beside the gate, never gated (#152): the probe is targeted at a known failure mode
    # by construction, so a pass bar on it would reward avoiding these particular bubbles.
    print("\nRepetition Probe (reported, NEVER gated)")
    print(f"  Bubbles: {len(probe.get('bubbles', []))}")
    for engine, s in sorted(probe.get("engines", {}).items()):
        print(f"  Engine: {engine}")
        print(f"    Harm (output run shorter than a reference run of >= {PROBE_MIN_RUN}): "
              f"{s['harm']}/{s['scored_bubbles']} scored bubbles")
        for b in s["bubbles"]:
            if not b["scored"]:
                continue
            flag = "HARM" if b["harm"] else "ok"
            source = probe["bubbles"][b["id"]]["source"]
            print(f"    [{flag}] id={b['id']} ref_run={b['reference_run']} "
                  f"out_run={b['output_run']} source={source}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Yomu Phase 1 eval harness")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--run-dir",
        help="Score the structured run directory written by run-benchmark.sh (manifest.json + "
        "records.jsonl + COMPLETE). See eval/SCHEMA.md.",
    )
    mode.add_argument(
        "--stub",
        action="store_true",
        help="Use synthetic outputs to exercise scoring logic without a device",
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

    run = None
    if args.run_dir:
        try:
            run = run_records.validate_run(Path(args.run_dir))
        except run_records.RunError as exc:
            # Nothing in the directory is scoreable. Print no aggregate at all.
            print(f"Run rejected: {exc}", file=sys.stderr)
            return 2

    bubble: dict[str, Any] = {"arms": {}}
    translation: dict[str, Any] = {"cases": [], "summary": {"engines": {}}}

    if not args.no_bubble:
        bubble = run_bubble_detection(run, args.stub)
    if not args.no_translation:
        translation = run_translation_quality(run, args.stub)
    probe = run_repetition_probe(run, args.stub)

    print_summary(bubble, translation, probe)

    invalid = sorted(run.invalid_arms) if run else []
    if invalid:
        print(f"\n{len(invalid)} requested arm(s) invalid: {', '.join(invalid)}", file=sys.stderr)
    # Not an error: the same device run also carries measurements the host did not plan (the
    # prompt-mode comparison, the #153 penalty sweep). They are named rather than scored.
    if run and run.unrequested_arms:
        print(
            f"\nRecords present for {len(run.unrequested_arms)} arm(s) this run did not request, "
            f"not scored: {', '.join(run.unrequested_arms)}"
        )

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    result_path = RESULTS_DIR / f"{timestamp}.json"
    result_path.write_text(
        json.dumps(
            {
                "timestamp": timestamp,
                "stub": args.stub,
                "run_id": run.manifest["run_id"] if run else None,
                "invalid_arms": invalid,
                "bubble_detection": bubble,
                "translation_quality": translation,
                "repetition_probe": probe,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to {result_path}")
    # Valid arms are still reported above; a nonzero exit is how an invalid arm refuses to pass as a
    # green run (#142).
    return 1 if invalid else 0


if __name__ == "__main__":
    sys.exit(main())
