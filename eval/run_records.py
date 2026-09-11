#!/usr/bin/env python3
"""Structured eval run records: the manifest the host writes, and the validator the scorer runs.

Graduates the #142 decision. The eval no longer reconstructs a run by grepping logcat and no longer
copies device output into shared `eval/**/actual*` files. Instead:

1. Before the device run, the host writes `manifest.json` describing the run it *expects*: run id,
   APK and corpus and contract hashes, device identity, every case and bubble id, and the metadata
   of every requested arm.
2. The device writes one terminal JSONL record per arm/case/stage invocation into
   `files/yomu-benchmark/<run-id>/records.jsonl`.
3. The host extracts that directory and writes `COMPLETE` (record count + records SHA-256).
4. This module validates (1) against (2) and (3) before any aggregate is computed.

This repo has shipped a green harness measuring nothing four times. Each check below names the
failure it exists to catch:

* #36, detection silently stubbed at a fake 100%: an arm must be host-declared, and a real detection
  record with observed model identity must exist for every declared case. An arm with no records is
  invalid, never an implicit pass.
* #41, every case discarded on a line-count mismatch: the manifest fixes the expected bubble ids, so
  a returned-id mismatch fails the output-shape gate with every id still in the denominator instead
  of removing the case.
* #58, a skipped engine scored against a prior run's stale `actual/`: records are keyed by a unique
  run id, scored in place, and cross-checked against the corpus hash. No shared output files exist
  to go stale.
* #44, scoring a heuristic `label` as if it were annotation: `eval-contract.json` enumerates every
  allowed metric and its inputs, the manifest pins its hash, and `label` is listed as forbidden.

Run this module directly to write a manifest; import `validate_run` to read one back.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parent
CONTRACT_PATH = ROOT / "eval-contract.json"
BUBBLE_CASES = ROOT / "bubble-detection" / "cases"
TRANS_CASES = ROOT / "translation-quality" / "cases"

SCHEMA_VERSION = 1

MANIFEST_NAME = "manifest.json"
RECORDS_NAME = "records.jsonl"
COMPLETE_NAME = "COMPLETE"

DETECTION = "detection"
CONTEXT_ASSEMBLY = "context_assembly"
TRANSLATION = "translation"
STAGES = (DETECTION, CONTEXT_ASSEMBLY, TRANSLATION)

# A measured outcome is the engine's answer, including its failures: the arm stays valid and every
# expected id stays in the denominator.
MEASURED_OUTCOMES = frozenset({"success", "blank", "timeout", "overflow", "error"})
# No model invocation happened, so there is nothing to score: the requested arm is invalidated.
INVALIDATING_OUTCOMES = frozenset({"not_loaded", "skipped_budget"})
KNOWN_OUTCOMES = MEASURED_OUTCOMES | INVALIDATING_OUTCOMES

# Observed-vs-expected arm metadata. `call_shape` is checked separately because the #146 fallback
# amendment makes a second shape legitimate after an `overflow`.
OBSERVED_FIELDS = ("provider", "model_id", "quantization", "target_language")

# The corpus hash covers scored inputs only. Generated outputs are deliberately excluded: hashing
# them would make the hash a function of the run it is supposed to police.
CORPUS_FILES = (
    (BUBBLE_CASES, "page.jpg"),
    (BUBBLE_CASES, "expected.json"),
    (TRANS_CASES, "source.txt"),
    (TRANS_CASES, "reference.txt"),
)

PROBE_CASE_ID = "repetition-probe"


class RunError(Exception):
    """The run directory is not scoreable at all: no aggregate may be printed for any arm."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def carries_text(line: str) -> bool:
    """Mirror of `TranslatableBubble.carriesText` in TranslationEngine.kt.

    A bubble whose source has no letter or digit -- a lone "?", "...", "!?" -- is kept verbatim and
    never reaches the model, so it is not a requested id. Deriving that here rather than trusting
    the device is the point: the manifest must be an independent statement of what should happen.
    """
    return any(ch.isalnum() for ch in line)


# --------------------------------------------------------------------------------------------
# Manifest
# --------------------------------------------------------------------------------------------


def corpus_sha256(case_ids: Iterable[str]) -> str:
    """Hash every scored input for the given cases, path-ordered so it is reproducible."""
    parts: list[str] = []
    for case_id in sorted(case_ids):
        for base, name in CORPUS_FILES:
            path = base / case_id / name
            if path.exists():
                parts.append(f"{base.name}/{case_id}/{name}:{sha256_file(path)}")
    return sha256_text("\n".join(parts))


def case_plan(case_ids: Iterable[str] | None = None) -> dict[str, dict]:
    """Expected cases and ids, read from the corpus rather than from anything the device says."""
    plan: dict[str, dict] = {}
    for case_dir in sorted(BUBBLE_CASES.iterdir()) if BUBBLE_CASES.exists() else []:
        if not case_dir.is_dir() or not (case_dir / "expected.json").exists():
            continue
        if case_ids is not None and case_dir.name not in case_ids:
            continue
        expected = json.loads((case_dir / "expected.json").read_text(encoding="utf-8"))
        boxes = expected.get("boxes", [])
        source_path = TRANS_CASES / case_dir.name / "source.txt"
        source = (
            source_path.read_text(encoding="utf-8").splitlines() if source_path.exists() else []
        )
        plan[case_dir.name] = {
            # Every ground-truth box is a bubble id. Detection and context assembly see all of them.
            "bubble_ids": list(range(len(boxes))),
            # Only ids whose source carries text reach the model (TranslationEngine keeps the rest
            # verbatim). Pinning this here is what stops #41 from silently shrinking a page.
            "requested_ids": [i for i, line in enumerate(source) if carries_text(line)],
            "image_width": expected.get("image_width"),
            "image_height": expected.get("image_height"),
        }
    return plan


# Twin of ArmMeta.quantizationOf in app/src/androidTest/.../RunRecords.kt. The GGUF file name is the
# only place a fixture states its quantization, and host and device must read it identically or
# every arm fails the observed-metadata check.
QUANTIZATION_RE = re.compile(r"(i1[_-])?q\d+(_[0k])?(_[a-z])?")


def quantization_of(file_name: str) -> str:
    match = QUANTIZATION_RE.search(file_name.lower())
    return match.group(0).upper() if match else "unknown"


def parse_arm(spec: str) -> dict[str, Any]:
    """Parse one `--arm key=value,key=value` spec.

    `gen.<name>` keys build the expected generation settings. Unknown keys are an error rather than
    a silently ignored typo -- a mistyped `call_shape` would otherwise disable the check it names.
    """
    allowed = {
        "arm_id",
        "stage",
        "provider",
        "provider_version",
        "model_id",
        "model_file",
        "quantization",
        "target_language",
        "call_shape",
        "permits_fallback",
    }
    arm: dict[str, Any] = {"target_language": "en", "permits_fallback": False, "generation": {}}
    for part in spec.split(","):
        if not part.strip():
            continue
        key, _, value = part.partition("=")
        key, value = key.strip(), value.strip()
        if key.startswith("gen."):
            arm["generation"][key[4:]] = json.loads(value)
            continue
        if key not in allowed:
            raise RunError(f"unknown arm key {key!r} in {spec!r}")
        if key == "permits_fallback":
            arm[key] = value.lower() in ("1", "true", "yes")
        else:
            # `target_language=` with nothing after it means "not applicable" (a detector has no
            # target language), which the device records as null -- not as an empty string.
            arm[key] = value or None
    for required in ("arm_id", "stage", "provider", "model_id", "call_shape"):
        if not arm.get(required):
            raise RunError(f"arm spec missing {required}: {spec!r}")
    if arm["stage"] not in STAGES:
        raise RunError(f"arm stage must be one of {STAGES}: {spec!r}")

    model_file = arm.pop("model_file", "")
    if model_file:
        path = Path(model_file)
        if not path.is_file():
            raise RunError(f"arm {arm['arm_id']}: model_file {model_file} does not exist")
        arm["model_sha256"] = sha256_file(path)
        arm.setdefault("quantization", quantization_of(path.name))
    else:
        # A managed provider (ML Kit fetches its own model through Play services) has no readable
        # artifact. That is allowed only when the provider package/model version is recorded, so the
        # run still says which implementation produced the numbers.
        if not arm.get("provider_version"):
            raise RunError(
                f"arm {arm['arm_id']}: a null model hash needs provider_version recorded"
            )
        arm["model_sha256"] = None
    return arm


def build_manifest(
    run_id: str,
    started_at: str,
    arms: list[dict],
    app_apk: Path | None,
    test_apk: Path | None,
    device_model: str,
    android_api: str,
    case_ids: Iterable[str] | None = None,
) -> dict:
    cases = case_plan(case_ids)
    if not cases:
        raise RunError("no eval cases found; run eval/generate-cases.py before benchmarking")
    for arm in arms:
        arm.setdefault("cases", sorted(cases))
    return {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_id,
        "started_at": started_at,
        "app_apk_sha256": sha256_file(app_apk) if app_apk else None,
        "test_apk_sha256": sha256_file(test_apk) if test_apk else None,
        "device": {"model": device_model, "android_api": android_api},
        "corpus_sha256": corpus_sha256(cases),
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "stages": list(STAGES),
        "cases": cases,
        "arms": arms,
    }


# --------------------------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------------------------


@dataclass
class ArmResult:
    arm_id: str
    stage: str
    meta: dict
    errors: list[str] = field(default_factory=list)
    # case_id -> the stage records for this arm, in file order.
    by_case: dict[str, list[dict]] = field(default_factory=dict)

    @property
    def valid(self) -> bool:
        return not self.errors


@dataclass
class ValidatedRun:
    run_dir: Path
    manifest: dict
    arms: dict[str, ArmResult]

    @property
    def valid_arms(self) -> dict[str, ArmResult]:
        return {k: v for k, v in self.arms.items() if v.valid}

    @property
    def invalid_arms(self) -> dict[str, ArmResult]:
        return {k: v for k, v in self.arms.items() if not v.valid}


def _read_json(path: Path, label: str) -> dict:
    if not path.exists():
        raise RunError(f"{label} missing: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise RunError(f"{label} is malformed: {exc}") from exc
    if not isinstance(data, dict):
        raise RunError(f"{label} is not a JSON object: {path}")
    return data


def _read_records(path: Path) -> list[dict]:
    if not path.exists():
        raise RunError(f"records file missing: {path}")
    records = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as exc:
            raise RunError(f"{path.name}:{lineno} is malformed JSON: {exc}") from exc
        if not isinstance(record, dict):
            raise RunError(f"{path.name}:{lineno} is not a JSON object")
        records.append(record)
    return records


def _check_whole_run(run_dir: Path) -> tuple[dict, list[dict]]:
    manifest = _read_json(run_dir / MANIFEST_NAME, "manifest")
    if manifest.get("schema_version") != SCHEMA_VERSION:
        raise RunError(
            f"manifest schema_version {manifest.get('schema_version')!r} != {SCHEMA_VERSION}"
        )
    run_id = manifest.get("run_id")
    if not run_id:
        raise RunError("manifest has no run_id")

    # #44: an aggregate computed under a different metric contract is not comparable to one computed
    # under this one, so a contract change invalidates the run rather than being absorbed silently.
    if manifest.get("contract_sha256") != sha256_file(CONTRACT_PATH):
        raise RunError(
            "manifest contract_sha256 does not match eval-contract.json; "
            "the run was planned against a different metric contract"
        )
    # #58: the corpus the run was planned against must be the corpus being scored.
    if manifest.get("corpus_sha256") != corpus_sha256(manifest.get("cases", {})):
        raise RunError(
            "manifest corpus_sha256 does not match the cases on disk; "
            "the corpus changed between planning and scoring"
        )

    records_path = run_dir / RECORDS_NAME
    records = _read_records(records_path)

    complete = _read_json(run_dir / COMPLETE_NAME, "completion marker")
    if complete.get("run_id") != run_id:
        raise RunError(
            f"COMPLETE run_id {complete.get('run_id')!r} != manifest run_id {run_id!r}"
        )
    if complete.get("record_count") != len(records):
        raise RunError(
            f"COMPLETE record_count {complete.get('record_count')!r} != {len(records)} records read"
        )
    if complete.get("records_sha256") != sha256_file(records_path):
        raise RunError("COMPLETE records_sha256 does not match records.jsonl")

    for index, record in enumerate(records):
        if record.get("run_id") != run_id:
            raise RunError(
                f"{RECORDS_NAME} line {index + 1} carries run_id {record.get('run_id')!r}, "
                f"not {run_id!r}"
            )
    return manifest, records


def _generation_mismatch(expected: dict, observed: Any) -> str | None:
    if not expected:
        return None
    if not isinstance(observed, dict):
        return f"generation settings not recorded (expected {expected})"
    differing = {
        key: (value, observed.get(key))
        for key, value in expected.items()
        if key not in observed or observed[key] != value
    }
    return f"generation mismatch {differing}" if differing else None


def _validate_translation_case(arm: ArmResult, case_id: str, records: list[dict], expected_ids: list[int]) -> None:
    """Per-case translation checks, including the #146 declared-fallback amendment."""
    first = records[0]
    if len(records) > 1:
        # A page-level batch that exceeds the decode budget falls back to per-line for that page
        # (#146). The extra records are expected only when the arm declared the fallback AND the
        # first record actually recorded `overflow`; an undeclared second call shape is the
        # ADR-0010 provenance failure and invalidates the arm.
        if first.get("outcome") != "overflow":
            arm.errors.append(
                f"{case_id}: {len(records)} translation records with no preceding overflow"
            )
            return
        if not arm.meta.get("permits_fallback"):
            arm.errors.append(f"{case_id}: fallback observed but the arm declares none")
            return

    for index, record in enumerate(records):
        outcome = record.get("outcome")
        if outcome in INVALIDATING_OUTCOMES:
            arm.errors.append(f"{case_id}: outcome {outcome} -- no model invocation to score")
            continue
        if outcome not in MEASURED_OUTCOMES:
            arm.errors.append(f"{case_id}: unknown outcome {outcome!r}")
            continue

        observed = record.get("observed")
        if not isinstance(observed, dict):
            arm.errors.append(f"{case_id}: translation record has no observed arm metadata")
            continue
        for key in OBSERVED_FIELDS:
            if observed.get(key) != arm.meta.get(key):
                arm.errors.append(
                    f"{case_id}: observed {key}={observed.get(key)!r} != "
                    f"declared {arm.meta.get(key)!r}"
                )
        # Index 0 must be the declared shape. A later record is the declared fallback, guarded above.
        if index == 0 and observed.get("call_shape") != arm.meta.get("call_shape"):
            arm.errors.append(
                f"{case_id}: observed call_shape={observed.get('call_shape')!r} != "
                f"declared {arm.meta.get('call_shape')!r}"
            )
        mismatch = _generation_mismatch(arm.meta.get("generation", {}), observed.get("generation"))
        if mismatch:
            arm.errors.append(f"{case_id}: {mismatch}")

    # Reading order, not source-line order: compared as a set for the same reason as the context
    # block ids above.
    requested = first.get("requested_ids")
    if not isinstance(requested, list) or sorted(requested) != sorted(expected_ids):
        # Not an invalidation: a wrong request list is the instrument's own statement of what it
        # asked for, and it must be visible rather than absorbed.
        arm.errors.append(
            f"{case_id}: requested_ids {requested} != manifest {expected_ids}"
        )


def validate_run(run_dir: Path) -> ValidatedRun:
    """Validate a run directory. Raises RunError when nothing in it is scoreable."""
    run_dir = Path(run_dir)
    manifest, records = _check_whole_run(run_dir)
    cases = manifest["cases"]

    arms: dict[str, ArmResult] = {
        arm["arm_id"]: ArmResult(arm_id=arm["arm_id"], stage=arm["stage"], meta=arm)
        for arm in manifest.get("arms", [])
    }
    if not arms:
        raise RunError("manifest declares no arms")

    unknown: list[str] = []
    for index, record in enumerate(records):
        arm_id = record.get("arm_id")
        arm = arms.get(arm_id)
        if arm is None:
            unknown.append(f"line {index + 1}: arm_id {arm_id!r} is not in the manifest")
            continue
        stage = record.get("stage")
        if stage not in STAGES:
            arm.errors.append(f"line {index + 1}: unknown stage {stage!r}")
            continue
        if stage not in (arm.stage, CONTEXT_ASSEMBLY):
            arm.errors.append(f"line {index + 1}: stage {stage} is not this arm's stage")
            continue
        case_id = record.get("case_id")
        if case_id != PROBE_CASE_ID and case_id not in cases:
            arm.errors.append(f"line {index + 1}: case_id {case_id!r} is not in the manifest")
            continue
        if not isinstance(record.get("duration_ms"), (int, float)):
            arm.errors.append(f"line {index + 1}: duration_ms is not a number")
        arm.by_case.setdefault(case_id, []).append(record)

    if unknown:
        raise RunError("records reference arms the manifest never declared: " + "; ".join(unknown))

    for arm in arms.values():
        expected_cases = arm.meta.get("cases", sorted(cases))
        for case_id in expected_cases:
            stage_records = [r for r in arm.by_case.get(case_id, []) if r.get("stage") == arm.stage]
            if not stage_records:
                arm.errors.append(f"{case_id}: no {arm.stage} record")
                continue
            if arm.stage == TRANSLATION:
                _validate_context(arm, case_id, cases[case_id])
                _validate_translation_case(
                    arm, case_id, stage_records, cases[case_id]["requested_ids"]
                )
            else:
                _validate_single(arm, case_id, stage_records)

    return ValidatedRun(run_dir=run_dir, manifest=manifest, arms=arms)


def _validate_single(arm: ArmResult, case_id: str, records: list[dict]) -> None:
    if len(records) > 1:
        arm.errors.append(f"{case_id}: {len(records)} {arm.stage} records, expected 1")
        return
    outcome = records[0].get("outcome")
    if outcome in INVALIDATING_OUTCOMES:
        arm.errors.append(f"{case_id}: outcome {outcome} -- no model invocation to score")
    elif outcome not in MEASURED_OUTCOMES:
        arm.errors.append(f"{case_id}: unknown outcome {outcome!r}")
    # #36: a detection arm that never ran the model cannot reach here, and one that ran and found
    # nothing records zero boxes -- a valid measured result whose recall fails.
    if arm.stage == DETECTION and outcome in MEASURED_OUTCOMES:
        observed = records[0].get("observed")
        if not isinstance(observed, dict) or observed.get("model_id") != arm.meta.get("model_id"):
            arm.errors.append(f"{case_id}: detection record does not name the declared model")


def _validate_context(arm: ArmResult, case_id: str, case: dict) -> None:
    context = [r for r in arm.by_case.get(case_id, []) if r.get("stage") == CONTEXT_ASSEMBLY]
    if not context:
        arm.errors.append(f"{case_id}: no {CONTEXT_ASSEMBLY} record")
        return
    if len(context) > 1:
        arm.errors.append(f"{case_id}: {len(context)} {CONTEXT_ASSEMBLY} records, expected 1")
        return
    record = context[0]
    if record.get("outcome") != "success":
        # Context assembly is host-side plumbing, not a measured engine behaviour: a failure here
        # means the page never reached the model in the shape the eval claims to measure.
        arm.errors.append(f"{case_id}: context assembly outcome {record.get('outcome')!r}")
        return
    if record.get("input_ids") != case["bubble_ids"]:
        arm.errors.append(f"{case_id}: context input_ids != manifest bubble_ids")
    blocks = record.get("output_block_ids") or []
    flattened = [bubble_id for block in blocks for bubble_id in block]
    if sorted(flattened) != sorted(case["bubble_ids"]):
        arm.errors.append(f"{case_id}: context block ids do not cover the manifest bubble ids")
    # Every id the model is asked for must have come out of a context block. The manifest's
    # requested_ids are in source-line order and the blocks are in manga reading order, so this is a
    # set comparison by construction -- the ids missing from requested_ids are exactly the
    # punctuation-only bubbles TranslationEngine keeps verbatim.
    in_blocks = sorted(i for i in flattened if i in set(case["requested_ids"]))
    if in_blocks != sorted(case["requested_ids"]):
        arm.errors.append(f"{case_id}: requested ids are not a subset of the context blocks")


# --------------------------------------------------------------------------------------------
# CLI: write a manifest
# --------------------------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)

    manifest_cmd = sub.add_parser("manifest", help="write manifest.json for a planned run")
    manifest_cmd.add_argument("--run-id", required=True)
    manifest_cmd.add_argument("--started-at", required=True)
    manifest_cmd.add_argument("--out", required=True)
    manifest_cmd.add_argument("--app-apk")
    manifest_cmd.add_argument("--test-apk")
    manifest_cmd.add_argument("--device-model", default="unknown")
    manifest_cmd.add_argument("--android-api", default="unknown")
    manifest_cmd.add_argument(
        "--arm",
        action="append",
        default=[],
        metavar="k=v,k=v",
        help="one arm spec; repeat per arm. Keys: arm_id, stage, provider, provider_version, "
        "model_id, model_file, quantization, target_language, call_shape, permits_fallback, "
        "and gen.<name> for expected generation settings.",
    )

    complete_cmd = sub.add_parser("complete", help="write the COMPLETE marker for an extracted run")
    complete_cmd.add_argument("--run-dir", required=True)

    args = parser.parse_args(argv)

    if args.command == "manifest":
        manifest = build_manifest(
            run_id=args.run_id,
            started_at=args.started_at,
            arms=[parse_arm(spec) for spec in args.arm],
            app_apk=Path(args.app_apk) if args.app_apk else None,
            test_apk=Path(args.test_apk) if args.test_apk else None,
            device_model=args.device_model,
            android_api=args.android_api,
        )
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {out}")
        return 0

    run_dir = Path(args.run_dir)
    manifest = _read_json(run_dir / MANIFEST_NAME, "manifest")
    records_path = run_dir / RECORDS_NAME
    if not records_path.exists():
        # An empty records file is still a completed extraction: the scorer must be able to say
        # "this run produced nothing" rather than fail on a missing file.
        records_path.write_text("", encoding="utf-8")
    records = _read_records(records_path)
    statuses: dict[str, str] = {}
    for arm in manifest.get("arms", []):
        arm_id = arm["arm_id"]
        seen = {r.get("case_id") for r in records if r.get("arm_id") == arm_id}
        expected = set(arm.get("cases", []))
        statuses[arm_id] = "complete" if expected and expected <= seen else "incomplete"
    marker = {
        "run_id": manifest.get("run_id"),
        "record_count": len(records),
        "records_sha256": sha256_file(records_path),
        "arm_status": statuses,
    }
    (run_dir / COMPLETE_NAME).write_text(
        json.dumps(marker, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {run_dir / COMPLETE_NAME} ({len(records)} records)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
