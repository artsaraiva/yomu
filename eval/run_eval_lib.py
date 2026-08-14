"""Scoring and runner utilities for the Yomu Phase 1 eval harness."""

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
BUBBLE_CASES = ROOT / "bubble-detection" / "cases"
TRANS_CASES = ROOT / "translation-quality" / "cases"

BUBBLE_ACTUAL = "actual.json"
TRANS_ACTUAL_DIR = "actual"


def iou(a: dict, b: dict) -> float:
    ax1, ay1 = a["x"], a["y"]
    ax2, ay2 = ax1 + a["w"], ay1 + a["h"]
    bx1, by1 = b["x"], b["y"]
    bx2, by2 = bx1 + b["w"], by1 + b["h"]

    inter_x1 = max(ax1, bx1)
    inter_y1 = max(ay1, by1)
    inter_x2 = min(ax2, bx2)
    inter_y2 = min(ay2, by2)

    inter_w = max(0, inter_x2 - inter_x1)
    inter_h = max(0, inter_y2 - inter_y1)
    inter_area = inter_w * inter_h

    area_a = a["w"] * a["h"]
    area_b = b["w"] * b["h"]
    union_area = area_a + area_b - inter_area

    return inter_area / union_area if union_area > 0 else 0.0


def score_bubbles(expected: list[dict], actual: list[dict], threshold: float = 0.5) -> dict:
    matched_expected: set[int] = set()
    matched_actual: set[int] = set()

    pairs: list[tuple[float, int, int]] = []
    for i, e in enumerate(expected):
        for j, a in enumerate(actual):
            pairs.append((iou(e, a), i, j))
    pairs.sort(reverse=True)

    for score, i, j in pairs:
        if score < threshold:
            break
        if i in matched_expected or j in matched_actual:
            continue
        matched_expected.add(i)
        matched_actual.add(j)

    tp = len(matched_expected)
    fp = len(actual) - len(matched_actual)
    fn = len(expected) - len(matched_expected)
    recall = tp / len(expected) if expected else 1.0

    per_label: dict[str, dict] = {}
    for label in sorted({e.get("label", "unknown") for e in expected}):
        idxs = [i for i, e in enumerate(expected) if e.get("label", "unknown") == label]
        label_matched = sum(1 for i in idxs if i in matched_expected)
        label_total = len(idxs)
        per_label[label] = {
            "expected_count": label_total,
            "matched": label_matched,
            "missed": label_total - label_matched,
            "recall_iou_0_5": label_matched / label_total if label_total else 1.0,
        }

    return {
        "expected_count": len(expected),
        "actual_count": len(actual),
        "matched": tp,
        "false_positives": fp,
        "missed": fn,
        "recall_iou_0_5": recall,
        "per_label": per_label,
    }


def has_artifact(text: str) -> bool:
    if not text:
        return False
    return (
        "SEP" in text
        or "<extra" in text
        or "[unused" in text
        or re.search(r"\bUNK\b", text) is not None
    )


def words(text: str) -> list[str]:
    return re.findall(r"[a-zA-Z0-9']+", text or "")


def score_translation(source: list[str], reference: list[str], output: list[str]) -> dict:
    n = len(source)
    if len(output) != n:
        return {
            "error": f"line count mismatch: source={n}, output={len(output)}",
            "untranslated_rate": 0.0,
            "artifact_rate": 0.0,
            "exact_match_rate": 0.0,
            "readability_ratio": 0.0,
        }

    untranslated = sum(1 for s, o in zip(source, output) if s and s == o)
    artifacts = sum(1 for o in output if has_artifact(o))
    exact_matches = sum(1 for r, o in zip(reference, output) if r and r.strip() == o.strip())

    ref_words = sum(len(words(r)) for r in reference)
    out_words = sum(len(words(o)) for o in output)
    readability_ratio = out_words / ref_words if ref_words > 0 else 0.0

    return {
        "untranslated_rate": untranslated / n,
        "artifact_rate": artifacts / n,
        "exact_match_rate": exact_matches / n,
        "readability_ratio": readability_ratio,
    }


def bubble_stub(expected: list[dict]) -> list[dict]:
    return [{"x": b["x"], "y": b["y"], "w": b["w"], "h": b["h"]} for b in expected]


def translation_stub(reference: list[str]) -> list[str]:
    return list(reference)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def run_bubble_detection(stub: bool) -> dict[str, Any]:
    results: list[dict] = []
    if not BUBBLE_CASES.exists():
        return {"cases": results}

    for case_dir in sorted(BUBBLE_CASES.iterdir()):
        if not case_dir.is_dir():
            continue
        expected_path = case_dir / "expected.json"
        if not expected_path.exists():
            continue

        expected = load_json(expected_path)
        boxes = expected.get("boxes", [])

        actual_path = case_dir / BUBBLE_ACTUAL
        if actual_path.exists():
            actual = load_json(actual_path).get("boxes", [])
            mode = "actual"
        elif stub:
            actual = bubble_stub(boxes)
            mode = "stub"
        else:
            actual = []
            mode = "missing"

        score = score_bubbles(boxes, actual)
        score["case_id"] = case_dir.name
        score["mode"] = mode
        results.append(score)

    if results:
        avg_recall = sum(r["recall_iou_0_5"] for r in results) / len(results)
        total_fp = sum(r["false_positives"] for r in results)
        total_fn = sum(r["missed"] for r in results)
        # Box-weighted, so a 1-box case cannot swing the number as hard as it does in avg_recall.
        total_expected = sum(r["expected_count"] for r in results)
        total_matched = sum(r["matched"] for r in results)
        total_recall = total_matched / total_expected if total_expected else 0.0
    else:
        avg_recall = 0.0
        total_fp = 0
        total_fn = 0
        total_recall = 0.0

    labels = sorted({label for r in results for label in r["per_label"]})
    per_label_summary: dict[str, dict] = {}
    for label in labels:
        expected_count = sum(r["per_label"].get(label, {}).get("expected_count", 0) for r in results)
        matched = sum(r["per_label"].get(label, {}).get("matched", 0) for r in results)
        missed = sum(r["per_label"].get(label, {}).get("missed", 0) for r in results)
        per_label_summary[label] = {
            "expected_count": expected_count,
            "matched": matched,
            "missed": missed,
            "recall_iou_0_5": matched / expected_count if expected_count else 1.0,
        }

    return {
        "cases": results,
        "summary": {
            "avg_recall_iou_0_5": avg_recall,
            "total_recall_iou_0_5": total_recall,
            "total_false_positives": total_fp,
            "total_missed": total_fn,
            "per_label": per_label_summary,
        },
    }


def run_translation_quality(stub: bool) -> dict[str, Any]:
    results: list[dict] = []
    engines: set[str] = set()
    if not TRANS_CASES.exists():
        return {"cases": results}

    for case_dir in sorted(TRANS_CASES.iterdir()):
        if not case_dir.is_dir():
            continue
        source_path = case_dir / "source.txt"
        reference_path = case_dir / "reference.txt"
        if not source_path.exists() or not reference_path.exists():
            continue

        source = load_lines(source_path)
        reference = load_lines(reference_path)

        actual_dir = case_dir / TRANS_ACTUAL_DIR
        if actual_dir.exists():
            outputs = sorted(actual_dir.glob("*.json"))
            mode = "actual"
        elif stub:
            outputs = []
            mode = "stub"
        else:
            outputs = []
            mode = "missing"

        case_result: dict[str, Any] = {"case_id": case_dir.name, "mode": mode, "engines": []}

        if mode == "stub":
            score = score_translation(source, reference, translation_stub(reference))
            score["engine"] = "stub"
            case_result["engines"].append(score)
            engines.add("stub")
        elif not outputs:
            case_result["engines"].append({"engine": "none", "error": "no engine output found"})
        else:
            for out_path in outputs:
                engine_name = out_path.stem
                engines.add(engine_name)
                out_data = load_json(out_path)
                output = out_data.get("translations", [])
                score = score_translation(source, reference, output)
                score["engine"] = engine_name
                case_result["engines"].append(score)

        results.append(case_result)

    summary: dict[str, Any] = {"engines": {}}
    for engine in engines:
        scores = [
            next((e for e in r["engines"] if e.get("engine") == engine), None)
            for r in results
        ]
        valid = [s for s in scores if s is not None and "error" not in s]
        if valid:
            summary["engines"][engine] = {
                "avg_untranslated_rate": sum(s["untranslated_rate"] for s in valid) / len(valid),
                "avg_artifact_rate": sum(s["artifact_rate"] for s in valid) / len(valid),
                "avg_exact_match_rate": sum(s["exact_match_rate"] for s in valid) / len(valid),
                "avg_readability_ratio": sum(s["readability_ratio"] for s in valid) / len(valid),
            }

    return {"cases": results, "summary": summary}
