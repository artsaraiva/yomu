#!/usr/bin/env python3
"""Self-check for the containment scorer (ADR-0003). Run: python3 eval/test_score_bubbles.py"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_eval_lib import pool_summary, score_bubbles

W, H = 830, 1170


def box(x, y, w, h, label="speech"):
    return {"x": x, "y": y, "w": w, "h": h, "label": label}


def det(x, y, w, h):
    return {"x": x, "y": y, "w": w, "h": h}


def main() -> None:
    # An exact box is a hit.
    s = score_bubbles([box(100, 100, 80, 110)], [det(100, 100, 80, 110)], W, H)
    assert s["containment_recall"] == 1.0, s
    assert s["localisation_recall"] == 1.0, s

    # A detection inset ~15px per side misses on raw overlap but the 4% pad (33px) rescues it.
    s = score_bubbles([box(100, 100, 80, 110)], [det(115, 115, 50, 80)], W, H)
    assert s["containment_recall"] == 1.0, s
    s = score_bubbles([box(100, 100, 80, 110)], [det(115, 115, 50, 80)], W, H, pad_fraction=0.0)
    assert s["containment_recall"] == 0.0, s

    # Covering 80% of the box is not a hit at the 0.95 gate, but the centre is still localised.
    s = score_bubbles([box(100, 100, 100, 100)], [det(100, 100, 80, 100)], W, H, pad_fraction=0.0)
    assert s["containment_recall"] == 0.0, s
    assert s["localisation_recall"] == 1.0, s

    # A detection covering two ground-truth centres is a hit for neither, and localises only one.
    two = [box(100, 100, 80, 100), box(200, 100, 80, 100)]
    s = score_bubbles(two, [det(90, 90, 200, 120)], W, H, pad_fraction=0.0)
    assert s["merging_detections"] == 1, s
    assert s["containment_recall"] == 0.0, s
    assert s["localisation_recall"] == 0.5, s
    assert s["false_positives"] == 1, s

    # Matching is one-to-one: one good detection cannot cover for two boxes.
    s = score_bubbles(two, [det(100, 100, 80, 100)], W, H, pad_fraction=0.0)
    assert s["matched"] == 1 and s["missed"] == 1, s

    # #44: pools are box-weighted, never a mean of per-case recalls. A 1-box case that scores 1.0
    # alongside a 9-box case that scores 0.0 is 0.100, not the 0.500 a per-case average gives.
    p = pool_summary(
        [
            {"expected_count": 1, "matched": 1, "localised": 1,
             "merging_detections": 0, "false_positives": 0, "missed": 0},
            {"expected_count": 9, "matched": 0, "localised": 0,
             "merging_detections": 2, "false_positives": 3, "missed": 9},
        ]
    )
    assert p["total_containment_recall"] == 0.1, p
    assert p["total_expected"] == 10 and p["case_count"] == 2, p
    assert p["total_merging_detections"] == 2 and p["total_false_positives"] == 3, p

    # An empty pool (no cover cases in the set) is 0.0, not a division error.
    assert pool_summary([])["total_containment_recall"] == 0.0

    print("ok")


if __name__ == "__main__":
    main()
