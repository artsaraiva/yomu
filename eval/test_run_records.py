"""Contract validation for the structured run records (#165, graduating #142).

Every check here is anchored to a failure this repo actually shipped. The four regression tests at
the bottom are the acceptance criteria: stubbed detection (#36), discarded translation cases (#41),
stale outputs (#58), and scoring heuristic labels (#44).
"""

import json
from pathlib import Path

import pytest

import run_records
from run_records import RunError, sha256_file, validate_run

CASE_ID = "bourei-p04"
BUBBLE_IDS = list(range(8))
# Every line of this case's source.txt carries text, so every id is requested.
REQUESTED_IDS = list(BUBBLE_IDS)
RUN_ID = "20260911-120000"

DETECTOR_ARM = {
    "arm_id": "bubble",
    "stage": "detection",
    "provider": "onnxruntime",
    "model_id": "yolo26n",
    "model_sha256": "0" * 64,
    "quantization": "fp32",
    "target_language": None,
    "call_shape": "page_image",
    "permits_fallback": False,
    "generation": {},
    "cases": [CASE_ID],
}

LLM_ARM = {
    "arm_id": "qwen25_1.5b",
    "stage": "translation",
    "provider": "llama.cpp",
    "model_id": "qwen25_1.5b",
    "model_sha256": "1" * 64,
    "quantization": "Q4_K_M",
    "target_language": "en",
    "call_shape": "id_keyed_batch",
    "permits_fallback": False,
    "generation": {"temperature": 0.2, "penalty_repeat": 1.0},
    "cases": [CASE_ID],
}

OBSERVED = {
    "provider": "llama.cpp",
    "model_id": "qwen25_1.5b",
    "quantization": "Q4_K_M",
    "target_language": "en",
    "call_shape": "id_keyed_batch",
    "generation": {"temperature": 0.2, "penalty_repeat": 1.0},
}


def detection_record(**overrides):
    record = {
        "run_id": RUN_ID,
        "arm_id": "bubble",
        "case_id": CASE_ID,
        "stage": "detection",
        "outcome": "success",
        "duration_ms": 42,
        "page_width": 828,
        "page_height": 1170,
        "nms_thresholded": 9,
        "nms_kept": 8,
        "boxes": [{"x": 10, "y": 10, "w": 20, "h": 20, "conf": 0.9}],
        "observed": {"provider": "onnxruntime", "model_id": "yolo26n"},
    }
    record.update(overrides)
    return record


def context_record(**overrides):
    record = {
        "run_id": RUN_ID,
        "arm_id": LLM_ARM["arm_id"],
        "case_id": CASE_ID,
        "stage": "context_assembly",
        "outcome": "success",
        "duration_ms": 3,
        "input_ids": list(BUBBLE_IDS),
        "output_block_ids": [BUBBLE_IDS[:4], BUBBLE_IDS[4:]],
    }
    record.update(overrides)
    return record


def translation_record(**overrides):
    record = {
        "run_id": RUN_ID,
        "arm_id": LLM_ARM["arm_id"],
        "case_id": CASE_ID,
        "stage": "translation",
        "outcome": "success",
        "duration_ms": 1200,
        "requested_ids": list(REQUESTED_IDS),
        "results": [{"bubble_id": i, "text": f"line {i}"} for i in REQUESTED_IDS],
        "observed": dict(OBSERVED),
    }
    record.update(overrides)
    return record


def write_run(tmp_path: Path, records: list[dict], arms: list[dict] | None = None, **manifest_overrides) -> Path:
    """Write a complete, internally consistent run directory, then apply any overrides."""
    run_dir = tmp_path / RUN_ID
    run_dir.mkdir(parents=True, exist_ok=True)

    manifest = run_records.build_manifest(
        run_id=RUN_ID,
        started_at="2026-09-11T12:00:00Z",
        arms=[dict(arm) for arm in (arms if arms is not None else [DETECTOR_ARM, LLM_ARM])],
        app_apk=None,
        test_apk=None,
        device_model="Pixel 7",
        android_api="34",
        case_ids=[CASE_ID],
    )
    manifest.update(manifest_overrides)
    (run_dir / run_records.MANIFEST_NAME).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (run_dir / run_records.RECORDS_NAME).write_text(
        "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in records), encoding="utf-8"
    )
    run_records.main(["complete", "--run-dir", str(run_dir)])
    return run_dir


def good_records():
    return [detection_record(), context_record(), translation_record()]


def errors_for(run, arm_id):
    return " | ".join(run.arms[arm_id].errors)


def test_clean_run_has_no_invalid_arms(tmp_path):
    run = validate_run(write_run(tmp_path, good_records()))
    assert run.invalid_arms == {}
    assert set(run.valid_arms) == {"bubble", LLM_ARM["arm_id"]}


def test_missing_manifest_is_fatal(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    (run_dir / run_records.MANIFEST_NAME).unlink()
    with pytest.raises(RunError, match="manifest missing"):
        validate_run(run_dir)


def test_malformed_records_are_fatal(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    (run_dir / run_records.RECORDS_NAME).write_text("{not json\n", encoding="utf-8")
    with pytest.raises(RunError, match="malformed JSON"):
        validate_run(run_dir)


def test_missing_completion_marker_is_fatal(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    (run_dir / run_records.COMPLETE_NAME).unlink()
    with pytest.raises(RunError, match="completion marker missing"):
        validate_run(run_dir)


def test_record_count_mismatch_is_fatal(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    marker = json.loads((run_dir / run_records.COMPLETE_NAME).read_text())
    marker["record_count"] = 99
    (run_dir / run_records.COMPLETE_NAME).write_text(json.dumps(marker), encoding="utf-8")
    with pytest.raises(RunError, match="record_count"):
        validate_run(run_dir)


def test_records_hash_mismatch_is_fatal(tmp_path):
    """Appending a record after COMPLETE was written must not be scoreable."""
    run_dir = write_run(tmp_path, good_records())
    with (run_dir / run_records.RECORDS_NAME).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(translation_record(case_id=CASE_ID)) + "\n")
    with pytest.raises(RunError, match="record_count|records_sha256"):
        validate_run(run_dir)


def test_run_id_mismatch_is_fatal(tmp_path):
    run_dir = write_run(tmp_path, [detection_record(run_id="some-other-run"), context_record(), translation_record()])
    with pytest.raises(RunError, match="run_id"):
        validate_run(run_dir)


def test_unknown_outcome_invalidates_the_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(outcome="probably_fine"), context_record(), translation_record()]))
    assert "unknown outcome" in errors_for(run, "bubble")
    # The other arm is untouched: a valid arm stays reportable when another is invalid.
    assert LLM_ARM["arm_id"] in run.valid_arms


def test_missing_stage_record_invalidates_the_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), translation_record()]))
    assert "no context_assembly record" in errors_for(run, LLM_ARM["arm_id"])


def test_duplicate_stage_record_invalidates_the_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), detection_record(), context_record(), translation_record()]))
    assert "2 detection records" in errors_for(run, "bubble")


def test_observed_metadata_mismatch_invalidates_the_arm(tmp_path):
    observed = dict(OBSERVED, quantization="Q8_0")
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(observed=observed)]))
    assert "observed quantization" in errors_for(run, LLM_ARM["arm_id"])


def test_generation_mismatch_invalidates_the_arm(tmp_path):
    observed = dict(OBSERVED, generation={"temperature": 0.2, "penalty_repeat": 1.1})
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(observed=observed)]))
    assert "generation mismatch" in errors_for(run, LLM_ARM["arm_id"])


def test_context_input_ids_must_equal_the_manifest(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(input_ids=[0, 1]), translation_record()]))
    assert "input_ids" in errors_for(run, LLM_ARM["arm_id"])


def test_context_blocks_must_cover_the_translation_request(tmp_path):
    blocks = [BUBBLE_IDS[:4], BUBBLE_IDS[4:6]]
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(output_block_ids=blocks), translation_record()]))
    assert "context block ids" in errors_for(run, LLM_ARM["arm_id"])


def test_context_failure_invalidates_the_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(outcome="error"), translation_record()]))
    assert "context assembly outcome" in errors_for(run, LLM_ARM["arm_id"])


def test_not_loaded_invalidates_the_requested_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(outcome="not_loaded", results=[])]))
    assert "no model invocation" in errors_for(run, LLM_ARM["arm_id"])


def test_skipped_budget_invalidates_the_requested_arm(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(outcome="skipped_budget", results=[])]))
    assert "no model invocation" in errors_for(run, LLM_ARM["arm_id"])


@pytest.mark.parametrize("outcome", ["blank", "timeout", "overflow", "error"])
def test_measured_engine_failures_keep_the_arm_valid(tmp_path, outcome):
    """A model that answered badly is a measurement, not a broken instrument."""
    run = validate_run(
        write_run(tmp_path, [detection_record(), context_record(), translation_record(outcome=outcome, results=[])])
    )
    assert LLM_ARM["arm_id"] in run.valid_arms


def test_detection_with_zero_boxes_is_a_valid_measured_result(tmp_path):
    run = validate_run(write_run(tmp_path, [detection_record(boxes=[]), context_record(), translation_record()]))
    assert "bubble" in run.valid_arms


def test_unrequested_arm_is_reported_not_scored(tmp_path):
    """The same device run carries arms the host did not plan -- they must not break the planned ones.

    `connectedAndroidTest` runs every @Test in EngineBenchmarkTest, so the prompt-mode comparison and
    the #153 penalty sweep write their own arms into the same records file. Rejecting the whole run
    for that would invert the per-arm isolation rule this contract exists to provide.
    """
    extra = translation_record(arm_id="repeat_1.2")
    run = validate_run(write_run(tmp_path, good_records() + [extra]))
    assert run.unrequested_arms == ["repeat_1.2"]
    assert run.invalid_arms == {}
    assert set(run.valid_arms) == {"bubble", LLM_ARM["arm_id"]}


def test_a_fallback_record_may_not_repeat_the_declared_call_shape(tmp_path):
    # A second batch call after an overflow is a retry, not the declared per-line fallback.
    arm = dict(LLM_ARM, permits_fallback=True)
    records = [
        detection_record(),
        context_record(),
        translation_record(outcome="overflow", results=[]),
        translation_record(),
    ]
    run = validate_run(write_run(tmp_path, records, arms=[DETECTOR_ARM, arm]))
    assert "repeats the declared call_shape" in errors_for(run, LLM_ARM["arm_id"])


# --- #146 amendment: a declared per-line fallback after an overflow ---------------------------


def test_declared_fallback_after_overflow_stays_valid(tmp_path):
    arm = dict(LLM_ARM, permits_fallback=True)
    per_line = dict(OBSERVED, call_shape="per_line")
    records = [
        detection_record(),
        context_record(),
        translation_record(outcome="overflow", results=[]),
        translation_record(observed=per_line),
    ]
    run = validate_run(write_run(tmp_path, records, arms=[DETECTOR_ARM, arm]))
    assert run.invalid_arms == {}


def test_undeclared_fallback_invalidates_the_arm(tmp_path):
    per_line = dict(OBSERVED, call_shape="per_line")
    records = [
        detection_record(),
        context_record(),
        translation_record(outcome="overflow", results=[]),
        translation_record(observed=per_line),
    ]
    run = validate_run(write_run(tmp_path, records))
    assert "declares none" in errors_for(run, LLM_ARM["arm_id"])


def test_second_call_shape_without_an_overflow_invalidates_the_arm(tmp_path):
    arm = dict(LLM_ARM, permits_fallback=True)
    per_line = dict(OBSERVED, call_shape="per_line")
    records = [
        detection_record(),
        context_record(),
        translation_record(),
        translation_record(observed=per_line),
    ]
    run = validate_run(write_run(tmp_path, records, arms=[DETECTOR_ARM, arm]))
    assert "no preceding overflow" in errors_for(run, LLM_ARM["arm_id"])


def test_undeclared_call_shape_on_the_first_record_invalidates_the_arm(tmp_path):
    per_line = dict(OBSERVED, call_shape="per_line")
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(observed=per_line)]))
    assert "observed call_shape" in errors_for(run, LLM_ARM["arm_id"])


# --- the four historical failures -------------------------------------------------------------


def test_36_stubbed_detection_cannot_pass_as_a_run(tmp_path):
    """#36: detection was silently stubbed and reported a fake 100%.

    A stub produces no detection record for the declared arm. There is no implicit pass: the arm is
    invalid and gets no aggregate, instead of a green gate nobody measured.
    """
    run = validate_run(write_run(tmp_path, [context_record(), translation_record()]))
    assert "bubble" in run.invalid_arms
    assert "no detection record" in errors_for(run, "bubble")


def test_36_detection_from_a_different_model_invalidates_the_arm(tmp_path):
    """The other half of #36: a record that exists but did not come from the declared detector."""
    run = validate_run(
        write_run(
            tmp_path,
            [
                detection_record(observed={"provider": "onnxruntime", "model_id": "stub"}),
                context_record(),
                translation_record(),
            ],
        )
    )
    assert "does not name the declared model" in errors_for(run, "bubble")


def test_41_a_returned_id_mismatch_fails_the_gate_without_discarding_the_case(tmp_path):
    """#41: a line-count mismatch discarded every case and printed "No engine outputs scored."

    The ids the model returned are now scored against the manifest's: a short answer fails the
    output-shape gate with every expected id still in the denominator, and the arm stays valid so
    the failure is reported rather than vanishing.
    """
    short = [{"bubble_id": i, "text": f"line {i}"} for i in REQUESTED_IDS[:3]]
    run = validate_run(write_run(tmp_path, [detection_record(), context_record(), translation_record(results=short)]))
    assert LLM_ARM["arm_id"] in run.valid_arms

    from run_eval_lib import dense_output

    source = [f"src {i}" for i in BUBBLE_IDS]
    output, shape = dense_output(source, REQUESTED_IDS, short)
    assert shape["output_shape_pass"] is False
    assert shape["missing_ids"] == REQUESTED_IDS[3:]
    assert len(output) == len(BUBBLE_IDS)


def test_41_extra_and_duplicate_ids_fail_the_output_shape_gate():
    from run_eval_lib import dense_output

    source = ["a", "b"]
    results = [
        {"bubble_id": 0, "text": "A"},
        {"bubble_id": 0, "text": "A again"},
        {"bubble_id": 7, "text": "nobody asked"},
    ]
    _, shape = dense_output(source, [0, 1], results)
    assert shape["duplicate_ids"] == [0]
    assert shape["extra_ids"] == [7]
    assert shape["missing_ids"] == [1]
    assert shape["output_shape_pass"] is False


def test_58_a_records_file_from_another_run_is_fatal(tmp_path):
    """#58: a skipped engine was scored against a prior run's stale `actual/` files.

    Records carry the run id they were produced under, so another run's file cannot be scored as
    this one's. There are no shared output files left to go stale.
    """
    stale = [
        detection_record(run_id="20260101-000000"),
        context_record(run_id="20260101-000000"),
        translation_record(run_id="20260101-000000"),
    ]
    with pytest.raises(RunError, match="run_id"):
        validate_run(write_run(tmp_path, stale))


def test_58_a_corpus_changed_after_planning_is_fatal(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    manifest = json.loads((run_dir / run_records.MANIFEST_NAME).read_text())
    manifest["corpus_sha256"] = "f" * 64
    (run_dir / run_records.MANIFEST_NAME).write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(RunError, match="corpus_sha256"):
        validate_run(run_dir)


def test_44_a_different_metric_contract_is_fatal(tmp_path):
    """#44: heuristic `label` values were scored as if they were annotation.

    The metric contract is machine-readable and hashed into the manifest, so a run planned under a
    different contract is rejected rather than quietly compared against one.
    """
    run_dir = write_run(tmp_path, good_records())
    manifest = json.loads((run_dir / run_records.MANIFEST_NAME).read_text())
    manifest["contract_sha256"] = "e" * 64
    (run_dir / run_records.MANIFEST_NAME).write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(RunError, match="contract_sha256"):
        validate_run(run_dir)


def test_44_the_contract_forbids_scoring_detection_labels():
    contract = json.loads(run_records.CONTRACT_PATH.read_text(encoding="utf-8"))
    assert "expected.json:boxes[].label" in contract["forbidden_inputs"]
    for name, metric in contract["metrics"].items():
        for source in metric["inputs"]:
            assert source not in contract["forbidden_inputs"], f"{name} reads a forbidden input"


def test_44_an_unregistered_scored_dimension_is_rejected():
    """The registry is enforced, not merely checked in: an unregistered metric fails the run."""
    run_records.assert_registered("translation", ["bubble_coverage", "mean_chrf"])
    with pytest.raises(RunError, match="not registered"):
        run_records.assert_registered("translation", ["label_accuracy"])
    # Registered, but for the wrong stage: a detection metric may not be reported as a translation
    # one, which is how a heuristic dimension would sneak across (#44).
    with pytest.raises(RunError, match="not registered"):
        run_records.assert_registered("translation", ["containment_recall"])


def test_44_every_reported_metric_is_registered():
    import run_eval_lib

    run_records.assert_registered(run_records.DETECTION, run_eval_lib.DETECTION_METRICS)
    run_records.assert_registered(run_records.TRANSLATION, run_eval_lib.TRANSLATION_METRICS)
    run_records.assert_registered(run_records.TRANSLATION, run_eval_lib.PROBE_METRICS)


def test_44_the_manifest_pins_the_live_contract_hash(tmp_path):
    run_dir = write_run(tmp_path, good_records())
    manifest = json.loads((run_dir / run_records.MANIFEST_NAME).read_text())
    assert manifest["contract_sha256"] == sha256_file(run_records.CONTRACT_PATH)


def test_cases_none_declares_a_probe_only_arm():
    arm = run_records.parse_arm(
        "arm_id=repeat_1.2,stage=translation,provider=llama.cpp,"
        "model_id=qwen.gguf,call_shape=id_keyed_batch,cases=none,provider_version=x"
    )
    assert arm["cases"] == []


def test_cases_none_survives_manifest_defaulting(tmp_path):
    probe_only = run_records.parse_arm(
        "arm_id=repeat_1.2,stage=translation,provider=llama.cpp,"
        "model_id=qwen.gguf,call_shape=id_keyed_batch,cases=none,provider_version=x"
    )
    corpus = run_records.parse_arm(
        "arm_id=repeat_1.0,stage=translation,provider=llama.cpp,"
        "model_id=qwen.gguf,call_shape=id_keyed_batch,provider_version=x"
    )
    manifest = run_records.build_manifest(
        run_id="r", started_at="t", arms=[probe_only, corpus],
        app_apk=None, test_apk=None, device_model="d", android_api="1",
    )
    by_id = {a["arm_id"]: a for a in manifest["arms"]}
    assert by_id["repeat_1.2"]["cases"] == []
    assert by_id["repeat_1.0"]["cases"] == sorted(manifest["cases"])


def test_probe_only_arm_is_not_scored_as_missing_the_corpus(tmp_path):
    """A `cases=none` arm runs no corpus case, so the scorer must not report one error per case.

    validate_run already honours the narrowed list; run_eval_lib iterated every arm over every
    case independently, which invalidated the whole run over an arm that was never asked (#198).
    """
    import run_eval_lib

    probe_arm = dict(LLM_ARM, arm_id="repeat_1.2", cases=[])
    records = [
        detection_record(),
        context_record(),
        translation_record(),
        translation_record(arm_id="repeat_1.2", case_id=run_records.PROBE_CASE_ID),
    ]
    run_dir = write_run(tmp_path, records, arms=[DETECTOR_ARM, LLM_ARM, probe_arm])
    run = validate_run(run_dir)

    assert not run.arms["repeat_1.2"].errors
    scored = run_eval_lib.run_translation_quality(run)
    reported = [
        e for case in scored["cases"]
        for e in case["engines"] if e.get("engine") == "repeat_1.2"
    ]
    assert reported == []
