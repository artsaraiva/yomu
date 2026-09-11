#!/usr/bin/env python3
"""Self-check for the #57 detector comparison. Run: pytest eval/test_detector_comparison.py"""

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


INCUMBENT = cmp.INCUMBENT[1]
CANDIDATE = cmp.CANDIDATE[1]


def record(*boxes):
    """A detection record as it appears in records.jsonl -- boxes plus the record's own fields."""
    return {"stage": "detection", "outcome": "success", "duration_ms": 10, "boxes": list(boxes),
            "nms_thresholded": len(boxes), "nms_kept": len(boxes)}


def test_the_33_decision_rule():
    # 8pp separation dominates; below it, strict merging reduction; else hold.
    assert cmp.decide(8.0, 10, 10) == "swap-8pp"
    assert cmp.decide(20.0, 5, 9) == "swap-8pp", "8pp wins even if merging is worse"
    assert cmp.decide(3.0, 10, 8) == "swap-merging"
    assert cmp.decide(3.0, 10, 10) == "hold", "equal merging is not a strict reduction"
    assert cmp.decide(-5.0, 10, 5) == "swap-merging", "candidate can win on merging while behind"
    assert cmp.decide(7.9, 10, 12) == "hold"


def test_best_pad_is_each_detectors_own():
    # Expected box needs padding to be contained; a detector whose box is already tight to the
    # glyphs peaks at pad 0.
    tight_case = [{
        "id": "t", "kind": cmp.STORY, "w": 1000, "h": 1000,
        "expected": [box(100, 100, 50, 50)],
        INCUMBENT: record(box(100, 100, 50, 50)),   # exact -> contained at any pad, incl. 0
        CANDIDATE: record(box(110, 110, 30, 30)),   # inset -> needs pad to contain the box
    }]
    assert cmp.best_pad(tight_case, INCUMBENT) == 0.0
    assert cmp.best_pad(tight_case, CANDIDATE) > 0.0


def test_confidence_floor_drops_low_conf_boxes():
    base = {
        "id": "t", "kind": cmp.STORY, "w": 1000, "h": 1000,
        "expected": [box(100, 100, 50, 50)],
        INCUMBENT: record(box(100, 100, 50, 50)),
        CANDIDATE: record(box(110, 110, 30, 30)),
    }
    p_all = cmp.pool([base], INCUMBENT, 0.0, min_conf=0.0)
    lowconf = [{**base, INCUMBENT: record(box(100, 100, 50, 50, conf=0.30))}]
    p_hi = cmp.pool(lowconf, INCUMBENT, 0.0, min_conf=0.45)
    assert p_all["total_containment_recall"] == 1.0
    assert p_hi["total_containment_recall"] == 0.0, "conf<0.45 box must be filtered out"
