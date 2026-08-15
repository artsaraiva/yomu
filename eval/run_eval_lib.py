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


# ADR-0003: detections are padded before scoring, mirroring the crop the pipeline hands OCR.
PAD_FRACTION = 0.04
CONTAINMENT_THRESHOLD = 0.95

# Case kinds, mirroring generate-cases.py. Only STORY pages are gated (#44).
STORY = "story"
COVER = "cover"

# #44: two detectors are separated only if their story-pool containment differs by at least this
# much. Below it they are tied, and the choice is made on licence, model size, and latency —
# never on score. 78 boxes cannot resolve a 3pp gap, and neither can all 1592 in OpenMantra.
SEPARATION_THRESHOLD = 0.08


def pad_box(box: dict, pad: float, image_width: int, image_height: int) -> dict:
    x1 = max(0.0, box["x"] - pad)
    y1 = max(0.0, box["y"] - pad)
    x2 = min(float(image_width), box["x"] + box["w"] + pad)
    y2 = min(float(image_height), box["y"] + box["h"] + pad)
    return {"x": x1, "y": y1, "w": max(0.0, x2 - x1), "h": max(0.0, y2 - y1)}


def contained_fraction(expected: dict, detection: dict) -> float:
    """Fraction of the expected box's area covered by the detection."""
    inter_w = max(
        0.0,
        min(expected["x"] + expected["w"], detection["x"] + detection["w"])
        - max(expected["x"], detection["x"]),
    )
    inter_h = max(
        0.0,
        min(expected["y"] + expected["h"], detection["y"] + detection["h"])
        - max(expected["y"], detection["y"]),
    )
    area = expected["w"] * expected["h"]
    return (inter_w * inter_h) / area if area > 0 else 0.0


def covers_centre(detection: dict, box: dict) -> bool:
    cx = box["x"] + box["w"] / 2
    cy = box["y"] + box["h"] / 2
    return (
        detection["x"] <= cx <= detection["x"] + detection["w"]
        and detection["y"] <= cy <= detection["y"] + detection["h"]
    )


def score_bubbles(
    expected: list[dict],
    actual: list[dict],
    image_width: int,
    image_height: int,
    threshold: float = CONTAINMENT_THRESHOLD,
    pad_fraction: float = PAD_FRACTION,
) -> dict:
    """Score detections by containment (ADR-0003), not IoU.

    A ground-truth box is a hit when one padded detection covers at least
    `threshold` of its area, one-to-one. A detection covering two or more
    ground-truth centres is merging and is a hit for none of them.
    """
    pad = pad_fraction * image_width
    padded = [pad_box(a, pad, image_width, image_height) for a in actual]

    merging = {
        j for j, d in enumerate(padded) if sum(1 for e in expected if covers_centre(d, e)) >= 2
    }

    matched_expected: set[int] = set()
    matched_actual: set[int] = set()

    pairs: list[tuple[float, int, int]] = []
    for i, e in enumerate(expected):
        for j, d in enumerate(padded):
            if j in merging:
                continue
            pairs.append((contained_fraction(e, d), i, j))
    pairs.sort(reverse=True)

    for score, i, j in pairs:
        if score < threshold:
            break
        if i in matched_expected or j in matched_actual:
            continue
        matched_expected.add(i)
        matched_actual.add(j)

    # Localisation is one-to-one as well: a merging detection localises one of the boxes it
    # swallows, not both — otherwise a single fat box would localise the whole page.
    localised: set[int] = set()
    claimed: set[int] = set()
    for i, e in enumerate(expected):
        for j, d in enumerate(padded):
            if j in claimed or not covers_centre(d, e):
                continue
            claimed.add(j)
            localised.add(i)
            break

    tp = len(matched_expected)
    fp = len(actual) - len(matched_actual)
    fn = len(expected) - len(matched_expected)

    # No per-label breakdown: the speech/narration/sfx labels are heuristic guesses from
    # generate-cases.py, not annotation (#44). Reporting them invites reading meaning into noise.
    return {
        "expected_count": len(expected),
        "actual_count": len(actual),
        "matched": tp,
        "false_positives": fp,
        "missed": fn,
        "localised": len(localised),
        "merging_detections": len(merging),
        "containment_recall": tp / len(expected) if expected else 1.0,
        "localisation_recall": len(localised) / len(expected) if expected else 1.0,
    }


def pool_summary(results: list[dict]) -> dict:
    """Aggregate a set of cases box-weighted.

    Per-case recalls are never averaged (#44): under a per-case mean a 1-box case counts as much
    as a 17-box one. Every headline number here is total matched over total expected.
    """
    total_expected = sum(r["expected_count"] for r in results)
    total_matched = sum(r["matched"] for r in results)
    total_localised = sum(r["localised"] for r in results)
    return {
        "case_count": len(results),
        "total_expected": total_expected,
        "total_containment_recall": total_matched / total_expected if total_expected else 0.0,
        "total_localisation_recall": total_localised / total_expected if total_expected else 0.0,
        "total_merging_detections": sum(r["merging_detections"] for r in results),
        "total_false_positives": sum(r["false_positives"] for r in results),
        "total_missed": sum(r["missed"] for r in results),
    }


# ADR-0004: the gate is the LLM's page-level call; ML Kit / OPUS-MT run the same call but batch
# per-bubble internally, so they are a floor reported separately and never ranked against it.
GATE_ENGINE = "llm"

# Hiragana, katakana (incl. half-width), CJK ideographs and compatibility forms. A CJK codepoint in
# an English translation is Japanese residue (ADR-0004), reference-adjudicated below.
CJK = re.compile(r"[぀-ヿ㐀-䶿一-鿿豈-﫿ｦ-ﾟ]")

# Non-translation: the engine echoed its instruction or returned a refusal template instead of a
# translation. #47 recorded a CAT-Translate output that was the verbatim instruction and scored
# 0.000 on every old metric. Matched case-insensitively as a substring, kept deliberately small —
# a false positive here fails a hard gate.
NON_TRANSLATION_MARKERS = (
    "translate the following",
    "translate these japanese",
    "reply with the translation",
    "one per line, numbered",
    "i cannot translate",
    "i can't translate",
    "as an ai",
)


def words(text: str) -> list[str]:
    return re.findall(r"[a-zA-Z0-9']+", text or "")


def has_cjk(text: str) -> bool:
    return bool(text) and CJK.search(text) is not None


def is_non_translation(text: str) -> bool:
    lowered = (text or "").lower()
    return any(marker in lowered for marker in NON_TRANSLATION_MARKERS)


def score_translation(source: list[str], reference: list[str], output: list[str]) -> dict:
    """Score one page-level call, matched to the reference by bubble id (ADR-0004).

    `output[i]` is the translation of source line i, empty where the engine returned no entry for
    that id. A short or long list is padded/truncated to the source length rather than voiding the
    case — the discard rule that printed "No engine outputs scored." before #45 is gone. Blank
    source lines carry no text and leave every denominator.
    """
    n = len(source)
    entries: list[tuple[str, str]] = []  # (reference, output) for ids that carry source text
    for i in range(n):
        if not source[i].strip():
            continue
        ref = reference[i] if i < len(reference) else ""
        out = output[i] if i < len(output) else ""
        entries.append((ref, out))

    total = len(entries)
    covered = sum(1 for _, o in entries if o.strip())
    non_translation = sum(1 for _, o in entries if is_non_translation(o))
    # Reference-adjudicated: CJK in the output is residue only where the reference has none, so a
    # legitimately-kept onomatopoeia is not charged against the engine.
    residue = sum(1 for r, o in entries if has_cjk(o) and not has_cjk(r))

    ref_words = sum(len(words(r)) for r, _ in entries)
    out_words = sum(len(words(o)) for _, o in entries)

    return {
        "entries": total,
        "covered": covered,
        "non_translation": non_translation,
        "residue": residue,
        "ref_words": ref_words,
        "out_words": out_words,
        "bubble_coverage": covered / total if total else 1.0,
        "non_translation_rate": non_translation / total if total else 0.0,
        "japanese_residue_rate": residue / total if total else 0.0,
        # Diagnostic only, never gated (#52): flags a verbose translation or an echoed prompt without
        # telling them apart.
        "readability_ratio": out_words / ref_words if ref_words > 0 else 0.0,
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

        score = score_bubbles(
            boxes, actual, expected["image_width"], expected["image_height"]
        )
        score["case_id"] = case_dir.name
        score["mode"] = mode
        score["kind"] = expected.get("kind", STORY)
        results.append(score)

    story = [r for r in results if r["kind"] == STORY]
    cover = [r for r in results if r["kind"] == COVER]

    return {
        "cases": results,
        # The gate is story boxes only. Cover pages carry title typography no bubble detector
        # finds, so they tax every candidate by the same constant and discriminate nothing (#44).
        "summary": pool_summary(story),
        "cover_text": pool_summary(cover),
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
        valid = [
            e
            for r in results
            for e in r["engines"]
            if e.get("engine") == engine and "error" not in e
        ]
        if not valid:
            continue
        # Rates are aggregated over entries, never averaged over cases (#44): a 3-bubble page must
        # not weigh as much as a 17-bubble one, and a single residue anywhere fails the gate.
        entries = sum(s["entries"] for s in valid)
        covered = sum(s["covered"] for s in valid)
        non_translation = sum(s["non_translation"] for s in valid)
        residue = sum(s["residue"] for s in valid)
        ref_words = sum(s["ref_words"] for s in valid)
        out_words = sum(s["out_words"] for s in valid)
        summary["engines"][engine] = {
            "role": "gate" if engine == GATE_ENGINE else "floor",
            "entries": entries,
            "bubble_coverage": covered / entries if entries else 1.0,
            "non_translation_rate": non_translation / entries if entries else 0.0,
            "japanese_residue_rate": residue / entries if entries else 0.0,
            "readability_ratio": out_words / ref_words if ref_words > 0 else 0.0,
            # Pass bars (#52): non-translation 0, residue 0, coverage 100% of ids. All-or-nothing.
            "gate_pass": non_translation == 0 and residue == 0 and covered == entries,
        }

    return {"cases": results, "summary": summary}
