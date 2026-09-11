"""Scoring and runner utilities for the Yomu Phase 1 eval harness."""

import itertools
import json
import re
from pathlib import Path
from typing import Any, NamedTuple

try:
    from sacrebleu.metrics import CHRF
except ImportError:
    CHRF = None

import run_records

ROOT = Path(__file__).resolve().parent
BUBBLE_CASES = ROOT / "bubble-detection" / "cases"
TRANS_CASES = ROOT / "translation-quality" / "cases"
PROBE_BUBBLES = ROOT / "repetition-probe" / "bubbles.json"

# There are deliberately no shared `actual*` output files any more. Every engine output is read from
# the run directory it was produced in, so a skipped engine cannot be scored against a prior run's
# leftovers (#58).


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


# ADR-0004: the gate is the LLM's page-level call. ML Kit / OPUS-MT run the same call but batch
# per-bubble internally, so they are a floor reported separately and never ranked against it.
# Every other engine name is an LLM on the page-level id-keyed batch path — the 0.8b incumbent
# ("llm") and the #84 challengers — and is scored against the gate. Naming a new engine after a
# floor tool is the only footgun; the challengers use their own model ids so this stays correct.
FLOOR_ENGINES = frozenset({"mlkit", "opusmt"})

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


# Twin of NON_TRANSLATION_PATTERNS in pipeline/.../TranslationEngine.kt: the runtime rejects what
# this scores, so a shape added on one side belongs on the other.
def is_non_translation(text: str) -> bool:
    lowered = (text or "").lower()
    introduction = re.match(
        r"^\s*(?:sure[!,.]?\s*)?(?:here(?:['’]s| is| are)\s+(?:the |an? )?(?:english )?translations?\b|(?:english )?translation\s*:)",
        lowered,
    )
    clarification = re.search(
        r"\b(?:please provide|provide me with)\b.{0,80}\b(?:japanese|manga|target|complete)\s+(?:manga\s+)?text\b",
        lowered,
    )
    explanation = re.match(
        r"^\s*the (?:(?:english )?translation of (?:the )?(?:given )?japanese text\b|japanese text\b.{0,150}\btranslates? to\b)",
        lowered,
    )
    return bool(introduction or clarification or explanation) or any(marker in lowered for marker in NON_TRANSLATION_MARKERS)


class Entry(NamedTuple):
    """One scored bubble id.

    The source is carried, not just the reference and output, because coverage cannot fail on this
    path: TranslationEngine substitutes bubble.sourceText when the slot returns nothing, so a page
    the model never answered still reports 100% covered (#137). An id whose output is its own source
    is that substitution, counted separately as a source echo.
    """

    source: str
    reference: str
    output: str


def score_translation(source: list[str], reference: list[str], output: list[str]) -> dict:
    """Score one page-level call, matched to the reference by bubble id (ADR-0004).

    `output[i]` is the translation of source line i, empty where the engine returned no entry for
    that id. A short or long list is padded/truncated to the source length rather than voiding the
    case — the discard rule that printed "No engine outputs scored." before #45 is gone. Blank
    source lines carry no text and leave every denominator.
    """
    n = len(source)
    entries: list[Entry] = []
    for i in range(n):
        if not source[i].strip():
            continue
        ref = reference[i] if i < len(reference) else ""
        out = output[i] if i < len(output) else ""
        entries.append(Entry(source[i], ref, out))

    total = len(entries)
    covered = sum(1 for e in entries if e.output.strip())
    # Only a source line carrying Japanese counts: the corpus has punctuation-only bubbles ("...",
    # "?") whose correct output is the same string, and charging those would put a permanent floor
    # under a cross-check whose whole job is to read 0 when nothing was substituted.
    source_echo = sum(1 for e in entries if has_cjk(e.source) and e.output.strip() == e.source.strip())
    non_translation = sum(1 for e in entries if is_non_translation(e.output))
    # Reference-adjudicated: CJK in the output is residue only where the reference has none, so a
    # legitimately-kept onomatopoeia is not charged against the engine.
    residue = sum(1 for e in entries if has_cjk(e.output) and not has_cjk(e.reference))

    chrf_sum = (
        sum(CHRF().sentence_score(e.output, [e.reference]).score for e in entries) if CHRF else None
    )
    ref_words = sum(len(words(e.reference)) for e in entries)
    out_words = sum(len(words(e.output)) for e in entries)

    return {
        "entries": total,
        "chrf_sum": chrf_sum,
        "mean_chrf": chrf_sum / total if chrf_sum is not None and total else None,
        "covered": covered,
        "source_echo": source_echo,
        "non_translation": non_translation,
        "residue": residue,
        "ref_words": ref_words,
        "out_words": out_words,
        "bubble_coverage": covered / total if total else 1.0,
        # Reported beside coverage, never gated on its own: an echo is the engine's fallback, not
        # proof of failure (a bubble whose reference is the same string scores here too).
        "source_echo_rate": source_echo / total if total else 0.0,
        "non_translation_rate": non_translation / total if total else 0.0,
        "japanese_residue_rate": residue / total if total else 0.0,
        # Diagnostic only, never gated (#52): flags a verbose translation or an echoed prompt without
        # telling them apart.
        "readability_ratio": out_words / ref_words if ref_words > 0 else 0.0,
    }


# Repetition probe (#152). Nothing else in the harness can see a repetition penalty eating
# legitimate repetition: residue is CJK-based and these references are English, non-translation
# will not fire, and the readability ratio over "ha ha ha ha ha" against "ha ha" is 0.4 with no bar
# attached. The probe reports harm; it never carries a pass bar of its own.
PROBE_MIN_RUN = 3

# Repeated punctuation is not repetition: a run of "." is an ellipsis, not a verbal tic. Kana stay
# in, the sokuon っ included — it trails はははっ without breaking the run, and skipping it would
# split もったいない mid-word. generate-repetition-probe.py selects on the same primitives it imports
# from here, so a bubble can never be selected on a run the scorer cannot see.
REPEAT_SKIP = "…。、.,!?！？ー－〜～・_-*'\"ｰ \u3000\n\r"


def longest_unit_run(units: list[str]) -> tuple[int, int]:
    """Longest run of one immediately-repeated unit in `units`: (repeat count, unit length).

    A unit is any contiguous slice, so this counts a repeated single element (ああ, ha ha) and a
    repeated phrase (もったいない×3, "what a waste"×3) with one pass.
    """
    # ponytail: O(n^3) over a bubble's worth of text (tens of units). Index the slices if this ever
    # runs over anything longer than a speech bubble.
    best, best_length = (1, 1) if units else (0, 0)
    for length in range(1, len(units) // 2 + 1):
        for start in range(len(units) - 2 * length + 1):
            unit = units[start : start + length]
            count = 1
            while units[start + count * length : start + (count + 1) * length] == unit:
                count += 1
            if count > best:
                best, best_length = count, length
    return best, best_length


def char_segments(text: str) -> list[list[str]]:
    """A bubble's characters, split at punctuation rather than with it removed.

    Splitting matters: deleting the comma from え、ええと would splice えええ into a run of three
    that the bubble does not contain.
    """
    return [list(segment) for segment in re.split(f"[{re.escape(REPEAT_SKIP)}]+", text or "") if segment]


def longest_repeat_run(text: str) -> int:
    """Longest repeated-unit run over either sequence: `ha ha ha` = 3, `aaaaaa` = 6, `ははは` = 3."""
    runs = [longest_unit_run(segment)[0] for segment in char_segments(text)]
    runs.append(longest_unit_run(words((text or "").lower()))[0])
    return max(runs, default=0)


def repeat_dominance(text: str) -> float:
    """Fraction of a bubble's non-punctuation characters spanned by its longest repeated run.

    This is what separates a bubble whose content *is* the repetition from a sentence that happens
    to contain あああっ before real dialogue; only the former is evidence about a penalty.
    """
    segments = char_segments(text)
    length = sum(len(segment) for segment in segments)
    if not length:
        return 0.0
    spans = (count * unit for count, unit in (longest_unit_run(s) for s in segments))
    return max(spans, default=0) / length


def score_repetition_probe(reference: list[str], output: list[str]) -> dict:
    """Harm = the engine shortened a run the human reference kept (#152).

    Scored only where the reference run is PROBE_MIN_RUN or more: below that there is nothing for a
    penalty to eat, so a short output run is not evidence. Per-bubble runs are returned so a hit is
    inspectable rather than just a number.
    """
    bubbles = []
    for i, ref in enumerate(reference):
        ref_run = longest_repeat_run(ref)
        out_run = longest_repeat_run(output[i] if i < len(output) else "")
        bubbles.append(
            {
                "id": i,
                "reference_run": ref_run,
                "output_run": out_run,
                "scored": ref_run >= PROBE_MIN_RUN,
                "harm": ref_run >= PROBE_MIN_RUN and out_run < ref_run,
            }
        )
    return {
        "bubbles": bubbles,
        "entries": len(bubbles),
        "scored_bubbles": sum(1 for b in bubbles if b["scored"]),
        "harm": sum(1 for b in bubbles if b["harm"]),
    }


def bubble_stub(expected: list[dict]) -> list[dict]:
    return [{"x": b["x"], "y": b["y"], "w": b["w"], "h": b["h"]} for b in expected]


def translation_stub(reference: list[str]) -> list[str]:
    return list(reference)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def _detection_arm(case_ids: list[str], boxes_for: Any, mode: str) -> dict[str, Any]:
    """Score one detector arm over `case_ids`. `boxes_for(case_id, expected)` returns its boxes."""
    results: list[dict] = []
    for case_id in case_ids:
        expected_path = BUBBLE_CASES / case_id / "expected.json"
        if not expected_path.exists():
            continue
        expected = load_json(expected_path)
        # #44: only x/y/w/h are read. `label` is a substring guess from generate-cases.py and is
        # listed as a forbidden input in eval-contract.json.
        gt = expected.get("boxes", [])
        score = score_bubbles(
            gt, boxes_for(case_id, expected), expected["image_width"], expected["image_height"]
        )
        score["case_id"] = case_id
        score["mode"] = mode
        score["kind"] = expected.get("kind", STORY)
        results.append(score)

    return {
        "cases": results,
        # The gate is story boxes only. Cover pages carry title typography no bubble detector
        # finds, so they tax every candidate by the same constant and discriminate nothing (#44).
        "summary": pool_summary([r for r in results if r["kind"] == STORY]),
        "cover_text": pool_summary([r for r in results if r["kind"] == COVER]),
    }


def run_bubble_detection(run: Any = None, stub: bool = False) -> dict[str, Any]:
    """Detection arms, scored from the run's detection records (or synthesised in stub mode)."""
    arms: dict[str, Any] = {}
    if stub or run is None:
        if not BUBBLE_CASES.exists():
            return {"arms": arms}
        case_ids = [d.name for d in sorted(BUBBLE_CASES.iterdir()) if d.is_dir()]
        arm = _detection_arm(
            case_ids,
            lambda case_id, expected: bubble_stub(expected.get("boxes", [])),
            "stub",
        )
        arm.update(valid=True, errors=[])
        arms["stub"] = arm
        return {"arms": arms}

    for arm_id, validated in run.arms.items():
        if validated.stage != run_records.DETECTION:
            continue
        boxes_by_case = {
            case_id: next(
                (r for r in records if r.get("stage") == run_records.DETECTION), {}
            ).get("boxes", [])
            for case_id, records in validated.by_case.items()
        }
        arm = _detection_arm(
            validated.meta.get("cases", []),
            lambda case_id, expected, table=boxes_by_case: table.get(case_id, []),
            "records",
        )
        arm.update(valid=validated.valid, errors=list(validated.errors))
        arms[arm_id] = arm
    return {"arms": arms}


def dense_output(
    source: list[str], requested_ids: list[int], results: list[dict]
) -> tuple[list[str], dict[str, Any]]:
    """Project a translation record's raw `{bubble_id, text}` results onto the source lines.

    Records carry the provider's raw results **before** `TranslationEngine` substitutes source text,
    so an id the model never answered arrives here as "" and fails coverage instead of hiding behind
    the substitution (#137). Ids outside `requested_ids` are the punctuation-only bubbles the engine
    keeps verbatim without calling the model at all; they are filled with their source, which is
    what production renders.

    The second return value is the output-shape report: a missing, extra or duplicate returned id is
    a measured engine failure, so it fails its own gate while every expected id stays in the
    denominator (#41).
    """
    by_id: dict[int, str] = {}
    duplicates: list[int] = []
    for entry in results:
        bubble_id = entry.get("bubble_id")
        if bubble_id in by_id:
            duplicates.append(bubble_id)
        by_id[bubble_id] = entry.get("text", "")

    requested = set(requested_ids)
    output = [
        by_id.get(i, "" if i in requested else source[i]) for i in range(len(source))
    ]
    shape = {
        "missing_ids": sorted(requested - set(by_id)),
        "extra_ids": sorted(set(by_id) - requested),
        "duplicate_ids": sorted(set(duplicates)),
    }
    shape["output_shape_pass"] = not any(
        shape[k] for k in ("missing_ids", "extra_ids", "duplicate_ids")
    )
    return output, shape


def _translation_arms(run: Any) -> dict[str, Any]:
    return {
        arm_id: validated
        for arm_id, validated in run.arms.items()
        if validated.stage == run_records.TRANSLATION
    }


def run_translation_quality(run: Any = None, stub: bool = False) -> dict[str, Any]:
    results: list[dict] = []
    engines: dict[str, Any] = {}
    if not TRANS_CASES.exists():
        return {"cases": results, "summary": {"engines": {}}}

    arms = {} if (stub or run is None) else _translation_arms(run)
    case_ids = sorted(d.name for d in TRANS_CASES.iterdir() if d.is_dir())

    for case_id in case_ids:
        source_path = TRANS_CASES / case_id / "source.txt"
        reference_path = TRANS_CASES / case_id / "reference.txt"
        if not source_path.exists() or not reference_path.exists():
            continue

        source = load_lines(source_path)
        reference = load_lines(reference_path)
        case_result: dict[str, Any] = {
            "case_id": case_id,
            "mode": "stub" if not arms else "records",
            "source": source,
            "reference": reference,
            "engines": [],
        }

        if not arms:
            if stub:
                score = score_translation(source, reference, translation_stub(reference))
                score["engine"] = "stub"
                score["output_shape_pass"] = True
                case_result["engines"].append(score)
                engines["stub"] = None
        else:
            requested_ids = run.manifest["cases"].get(case_id, {}).get("requested_ids", [])
            for arm_id, validated in arms.items():
                engines[arm_id] = validated
                records = [
                    r
                    for r in validated.by_case.get(case_id, [])
                    if r.get("stage") == run_records.TRANSLATION
                ]
                if not records:
                    case_result["engines"].append(
                        {"engine": arm_id, "error": f"no translation record for {case_id}"}
                    )
                    continue
                # A declared per-line fallback after an `overflow` writes further records for the
                # same page (#146). Later results win: they are the pass that actually produced the
                # page's translations.
                merged: list[dict] = []
                for record in records:
                    merged.extend(record.get("results") or [])
                output, shape = dense_output(source, requested_ids, merged)
                score = score_translation(source, reference, output)
                score["engine"] = arm_id
                score["translations"] = output
                score["outcome"] = records[-1].get("outcome")
                score.update(shape)
                case_result["engines"].append(score)

        results.append(case_result)

    summary: dict[str, Any] = {"engines": {}}
    for engine, validated in engines.items():
        valid = [
            e
            for r in results
            for e in r["engines"]
            if e.get("engine") == engine and "error" not in e
        ]
        # An arm the validator rejected gets no aggregate at all: reporting a mean for an instrument
        # that failed its own contract is the exact shape of the four historical failures (#142).
        # Checked before `valid` is consulted, so an arm that produced nothing at all is still named
        # rather than silently absent.
        if validated is not None and not validated.valid:
            summary["engines"][engine] = {
                "role": "floor" if engine in FLOOR_ENGINES else "gate",
                "valid": False,
                "errors": list(validated.errors),
            }
            continue
        if not valid:
            continue
        # Rates are aggregated over entries, never averaged over cases (#44): a 3-bubble page must
        # not weigh as much as a 17-bubble one, and a single residue anywhere fails the gate.
        entries = sum(s["entries"] for s in valid)
        covered = sum(s["covered"] for s in valid)
        source_echo = sum(s["source_echo"] for s in valid)
        non_translation = sum(s["non_translation"] for s in valid)
        residue = sum(s["residue"] for s in valid)
        ref_words = sum(s["ref_words"] for s in valid)
        out_words = sum(s["out_words"] for s in valid)
        summary["engines"][engine] = {
            "mean_chrf": sum(s["chrf_sum"] for s in valid) / entries if CHRF and entries else None,
            "role": "floor" if engine in FLOOR_ENGINES else "gate",
            "entries": entries,
            "bubble_coverage": covered / entries if entries else 1.0,
            # Coverage cannot fail here (#137), so it is never reported alone: an echoed source line
            # is a bubble the model did not answer, counted where coverage would have hidden it.
            "source_echo": source_echo,
            "source_echo_rate": source_echo / entries if entries else 0.0,
            "non_translation_rate": non_translation / entries if entries else 0.0,
            "japanese_residue_rate": residue / entries if entries else 0.0,
            "readability_ratio": out_words / ref_words if ref_words > 0 else 0.0,
            # Pass bars (#52): non-translation 0, residue 0, coverage 100% of ids. All-or-nothing.
            "valid": True,
            "completed_cases": len(valid),
            "expected_cases": len(results),
            # A missing/extra/duplicate returned id is a measured failure of the engine, not of the
            # instrument: it fails here and keeps every id in the denominator above (#41).
            "output_shape_pass": all(s.get("output_shape_pass", True) for s in valid),
            "gate_pass": (
                len(valid) == len(results)
                and entries > 0
                and non_translation == 0
                and residue == 0
                and covered == entries
                and all(s.get("output_shape_pass", True) for s in valid)
            ),
        }

    return {"cases": results, "summary": summary}


def run_repetition_probe(run: Any = None, stub: bool = False) -> dict[str, Any]:
    """Score the repetition probe. Reported alongside the gate, never gated (#152)."""
    if not PROBE_BUBBLES.exists():
        return {"bubbles": [], "engines": {}}

    bubbles = load_json(PROBE_BUBBLES)["bubbles"]
    reference = [b["reference"] for b in bubbles]
    sources = [b["source"] for b in bubbles]

    engines: dict[str, Any] = {}
    if run is not None:
        # The probe rides the same transport as the gate: its records are translation records whose
        # case id is the probe's own (#142).
        for arm_id, validated in _translation_arms(run).items():
            records = [
                r
                for r in validated.by_case.get(run_records.PROBE_CASE_ID, [])
                if r.get("stage") == run_records.TRANSLATION
            ]
            if not records:
                continue
            merged = [entry for record in records for entry in (record.get("results") or [])]
            output, _ = dense_output(sources, list(range(len(sources))), merged)
            engines[arm_id] = score_repetition_probe(reference, output)
    elif stub:
        engines["stub"] = score_repetition_probe(reference, translation_stub(reference))

    return {"bubbles": bubbles, "engines": engines}
