#!/usr/bin/env python3
"""Paired detector comparison for #57: incumbent yolo26n vs the yolo26s candidate.

Reads, per case, `expected.json` plus both detectors' detection records from a run directory
(`--run-dir`, the structured artifact run-benchmark.sh writes -- see eval/SCHEMA.md) and produces
every number the pre-registered #33 rule needs:

  - story containment at each detector's OWN best crop pad (the ranking number), at zero pad, and
    at the fixed 4% pad production ships today (ADR-0003);
  - merging detections (the sub-8pp tie-break) and false positives at each detector's best pad;
  - a confidence-threshold sweep (0.25/0.35/0.45/0.55) for the winner, from the per-box `conf`
    the benchmark emits -- no re-run needed;
  - whether NMS ever removed a box (thresholded vs kept, aggregated);
  - per-page detect wall-clock (mean/median/max).

Then it applies the rule fixed in the #33 grilling BEFORE any number existed:
  >= 8pp story-containment separation for yolo26s -> swap; below 8pp, yolo26s wins only if it
  strictly reduces merging; otherwise the incumbent holds. Wall-clock is a veto only.

This is a one-off decision aid feeding #33's ADR, not a permanent gate -- run-eval.py is untouched.
"""

import argparse
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

import run_records
from run_eval_lib import BUBBLE_CASES, STORY, load_json, pool_summary, score_bubbles

# (label, arm id in the run manifest)
INCUMBENT = ("yolo26n (incumbent)", "bubble")
CANDIDATE = ("yolo26s (candidate)", "bubble_s")

FIXED_PAD = 0.04  # ADR-0003, what production ships today
PAD_GRID = [i / 100 for i in range(0, 9)]  # 0.00 .. 0.08, "own best pad" is the argmax over this
CONF_SWEEP = [0.25, 0.35, 0.45, 0.55]
SEPARATION_PP = 8.0


def detection_records(run, arm_id):
    """That arm's detection record per case id, or {} when the arm was not in this run."""
    arm = run.arms.get(arm_id)
    if arm is None:
        return {}
    return {
        case_id: next(r for r in records if r.get("stage") == run_records.DETECTION)
        for case_id, records in arm.by_case.items()
        if any(r.get("stage") == run_records.DETECTION for r in records)
    }


def load_cases(run):
    """Cases that carry expected + both detectors' records, so the comparison is paired."""
    incumbent = detection_records(run, INCUMBENT[1])
    candidate = detection_records(run, CANDIDATE[1])
    cases = []
    for case_dir in sorted(BUBBLE_CASES.iterdir()):
        expected_path = case_dir / "expected.json"
        case_id = case_dir.name
        if not expected_path.exists() or case_id not in incumbent or case_id not in candidate:
            continue
        expected = load_json(expected_path)
        cases.append({
            "id": case_id,
            "kind": expected.get("kind", STORY),
            "w": expected["image_width"],
            "h": expected["image_height"],
            "expected": expected.get("boxes", []),
            INCUMBENT[1]: incumbent[case_id],
            CANDIDATE[1]: candidate[case_id],
        })
    return cases


def pool(cases, detector_key, pad, min_conf=0.0, kind=STORY):
    """Box-weighted pool for one detector at a given pad and confidence floor, one page-kind."""
    scored = []
    for c in cases:
        if c["kind"] != kind:
            continue
        actual = [b for b in c[detector_key].get("boxes", []) if b.get("conf", 1.0) >= min_conf]
        scored.append(score_bubbles(c["expected"], actual, c["w"], c["h"], pad_fraction=pad))
    return pool_summary(scored)


def best_pad(cases, detector_key):
    """The pad maximising story containment for this detector -- ADR-0003 fits 4% to the incumbent's
    insets, so a detector that frames tighter is measured at its own correction, not the incumbent's."""
    return max(PAD_GRID, key=lambda p: pool(cases, detector_key, p)["total_containment_recall"])


def decide(gap_pp, inc_merging, cand_merging):
    """The #33 rule, pure so it is self-checkable. Returns one of swap-8pp / swap-merging / hold."""
    if gap_pp >= SEPARATION_PP:
        return "swap-8pp"
    if cand_merging < inc_merging:
        return "swap-merging"
    return "hold"


def fmt(pool_result):
    return (f"containment {pool_result['total_containment_recall']:.3f} "
            f"({pool_result['total_containment_recall'] * pool_result['total_expected']:.0f}"
            f"/{pool_result['total_expected']}) | "
            f"localisation {pool_result['total_localisation_recall']:.3f} | "
            f"merging {pool_result['total_merging_detections']} | "
            f"fp {pool_result['total_false_positives']}")


def nms_and_timing(name, cases, detector_key):
    """thresholded/kept and duration live on the record itself, not on the box list."""
    thr = kept = 0
    times = []
    for case in cases:
        record = case[detector_key]
        thr += record.get("nms_thresholded", 0)
        kept += record.get("nms_kept", 0)
        if "duration_ms" in record:
            times.append(record["duration_ms"])
    removed = thr - kept
    print(f"  NMS: thresholded {thr} -> kept {kept} (removed {removed}"
          f"{'; NMS never fired -- #33 says delete it, not tune it' if removed == 0 else ''})")
    if times:
        print(f"  detect wall-clock: mean {statistics.mean(times):.0f}ms | "
              f"median {statistics.median(times):.0f}ms | max {max(times):.0f}ms "
              f"({len(times)} pages)")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run-dir", required=True, help="a run directory written by run-benchmark.sh")
    args = parser.parse_args()

    try:
        run = run_records.validate_run(Path(args.run_dir))
    except run_records.RunError as exc:
        print(f"Run rejected: {exc}", file=sys.stderr)
        sys.exit(2)

    cases = load_cases(run)
    if not cases:
        print("No paired cases found (need both the bubble and bubble_s arms in this run). "
              "Run eval/run-benchmark.sh with the yolo26s asset staged.", file=sys.stderr)
        sys.exit(1)

    story_n = sum(1 for c in cases if c["kind"] == STORY)
    print("=" * 68)
    print(f"#57 detector comparison  (paired, {story_n} story pages / "
          f"{sum(len(c['expected']) for c in cases if c['kind'] == STORY)} boxes)")
    print("=" * 68)

    inc_bp = best_pad(cases, INCUMBENT[1])
    cand_bp = best_pad(cases, CANDIDATE[1])
    for name, key in (INCUMBENT, CANDIDATE):
        bp = best_pad(cases, key)
        print(f"\n{name}")
        print(f"  own best pad = {bp:.0%}")
        print(f"    best pad  : {fmt(pool(cases, key, bp))}")
        print(f"    zero pad  : {fmt(pool(cases, key, 0.0))}")
        print(f"    fixed 4%  : {fmt(pool(cases, key, FIXED_PAD))}  <- production today")
        cover = pool(cases, key, bp, kind="cover")
        if cover["total_expected"]:
            print(f"    cover (never gated): {fmt(cover)}")
        nms_and_timing(name, cases, key)

    inc = pool(cases, INCUMBENT[1], inc_bp)
    cand = pool(cases, CANDIDATE[1], cand_bp)
    gap_pp = (cand["total_containment_recall"] - inc["total_containment_recall"]) * 100
    call = decide(gap_pp, inc["total_merging_detections"], cand["total_merging_detections"])

    print("\n" + "-" * 68)
    print("Confidence sweep (candidate winner is provisional; sweep whichever leads):")
    lead_name, lead_key, lead_bp = (
        (CANDIDATE[0], CANDIDATE[1], cand_bp) if gap_pp >= 0 else (INCUMBENT[0], INCUMBENT[1], inc_bp)
    )
    print(f"  {lead_name} at {lead_bp:.0%} pad:")
    for t in CONF_SWEEP:
        p = pool(cases, lead_key, lead_bp, min_conf=t)
        print(f"    conf>={t:.2f}: containment {p['total_containment_recall']:.3f} | "
              f"fp {p['total_false_positives']}")

    print("\n" + "=" * 68)
    print("PRE-REGISTERED VERDICT (#33 rule, story pool, own-best-pad):")
    print(f"  incumbent containment {inc['total_containment_recall']:.3f} @ {inc_bp:.0%}  |  "
          f"candidate {cand['total_containment_recall']:.3f} @ {cand_bp:.0%}  |  gap {gap_pp:+.1f}pp")
    if call == "swap-8pp":
        print(f"  >= {SEPARATION_PP:.0f}pp for yolo26s -> SWAP to yolo26s.")
    elif call == "swap-merging":
        print(f"  below {SEPARATION_PP:.0f}pp, but yolo26s strictly reduces merging "
              f"({inc['total_merging_detections']} -> {cand['total_merging_detections']}) -> SWAP to yolo26s.")
    else:
        print(f"  below {SEPARATION_PP:.0f}pp and no strict merging reduction "
              f"({inc['total_merging_detections']} -> {cand['total_merging_detections']}) -> "
              f"INCUMBENT HOLDS (size + integration precedent).")
    print("  Wall-clock is a veto only: if a winning yolo26s visibly delays the trigger, it is not "
          "an improvement. Judge against the timings above.")
    print("=" * 68)


if __name__ == "__main__":
    main()
