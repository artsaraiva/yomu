#!/usr/bin/env python3
"""Self-check for the #57 detector comparison. Run: python3 eval/test_detector_comparison.py"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import importlib.util

spec = importlib.util.spec_from_file_location(
    "score_detector_comparison", Path(__file__).resolve().parent / "score-detector-comparison.py"
)
cmp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cmp)


def box(x, y, w, h, conf=0.9):
    return {"x": x, "y": y, "w": w, "h": h, "conf": conf}


def main():
    # The #33 rule: 8pp separation dominates; below it, strict merging reduction; else hold.
    assert cmp.decide(8.0, 10, 10) == "swap-8pp"
    assert cmp.decide(20.0, 5, 9) == "swap-8pp", "8pp wins even if merging is worse"
    assert cmp.decide(3.0, 10, 8) == "swap-merging"
    assert cmp.decide(3.0, 10, 10) == "hold", "equal merging is not a strict reduction"
    assert cmp.decide(-5.0, 10, 5) == "swap-merging", "candidate can win on merging while behind"
    assert cmp.decide(7.9, 10, 12) == "hold"

    # best_pad picks the pad maximising containment. Expected box needs padding to be contained;
    # a detector whose box is already tight to the glyphs peaks at pad 0.
    tight_case = [{
        "id": "t", "kind": cmp.STORY, "w": 1000, "h": 1000,
        "expected": [box(100, 100, 50, 50)],
        "actual.json": [box(100, 100, 50, 50)],       # exact -> contained at any pad, incl. 0
        "actual_s.json": [box(110, 110, 30, 30)],     # inset -> needs pad to contain the box
    }]
    assert cmp.best_pad(tight_case, "actual.json") == 0.0
    assert cmp.best_pad(tight_case, "actual_s.json") > 0.0

    # Confidence floor drops low-conf boxes before scoring.
    p_all = cmp.pool(tight_case, "actual.json", 0.0, min_conf=0.0)
    lowconf = [{**tight_case[0], "actual.json": [box(100, 100, 50, 50, conf=0.30)]}]
    p_hi = cmp.pool(lowconf, "actual.json", 0.0, min_conf=0.45)
    assert p_all["total_containment_recall"] == 1.0
    assert p_hi["total_containment_recall"] == 0.0, "conf<0.45 box must be filtered out"

    print("ok")


if __name__ == "__main__":
    main()
