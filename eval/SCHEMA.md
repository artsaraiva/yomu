# Eval run record schema

The contract between the host, the device, and the scorer. Decided in
[#142](https://github.com/artsaraiva/yomu/issues/142), built in #165.

This is an **eval-only correctness instrument**. It is not production telemetry: nothing here is
persisted to Room, surfaced to users, or captured about prompt text. The one consumer is
`eval/run-eval.py`.

## Why it exists

This repo has shipped a green harness measuring nothing four times:

| | Failure | What was invisible |
| --- | --- | --- |
| #36 | detection silently stubbed | nothing recorded which detector produced the boxes |
| #41 | every case discarded on a line-count mismatch | nothing recorded which bubble ids were expected |
| #58 | a skipped engine scored against a prior run's `actual/` | nothing tied an output file to the run that made it |
| #44 | heuristic `label` values scored as annotation | nothing enumerated which inputs a metric may read |

Each is now a validation rule, and every rule below names the failure it exists for. The harness's
job is to say *"I scored 0 of 17 pages"* loudly, rather than print a mean.

## Shape of a run

```text
eval/benchmark-results/<run-id>/
├── manifest.json      # host, written BEFORE the device run
├── records.jsonl      # device, one terminal record per arm/case/stage invocation
├── COMPLETE           # host, written AFTER extraction
├── benchmark.log
├── logcat.log         # diagnostic only; the scorer never reads it
└── scored-results.json
```

The device writes into `files/yomu-benchmark/<run-id>/` and the host extracts it with
`adb exec-out run-as com.yomu.app tar c -C files yomu-benchmark/<run-id>`.

**Logcat is not a transport.** Its chatty filter drops multi-kilobyte lines, which silently lost 12
of 22 repetition-probe bubbles the one time a sweep relied on it (#152), and a dropped bubble scores
as an empty output — which reads as harm.

**There are no shared `eval/**/actual*` files.** A run is scored where it was produced. That is what
makes #58 structurally impossible rather than merely unlikely.

## `manifest.json`

The host's independent statement of the run it expects. Written before the device starts, from the
corpus and the fixtures on disk — never from anything the device reports.

```json
{
  "schema_version": 1,
  "run_id": "20260911-120000",
  "started_at": "2026-09-11T12:00:00Z",
  "app_apk_sha256": "…", "test_apk_sha256": "…",
  "device": { "model": "Pixel 7", "android_api": "34" },
  "corpus_sha256": "…",
  "contract_sha256": "…",
  "stages": ["detection", "context_assembly", "translation"],
  "cases": {
    "bourei-p04": { "bubble_ids": [0, 1, 2, 3, 4, 5, 6, 7], "requested_ids": [0, 1, 2, 3, 4, 5, 6, 7],
                    "image_width": 828, "image_height": 1170 }
  },
  "arms": [ … ]
}
```

- `corpus_sha256` covers every **scored input**: each case's `page.jpg`, `expected.json`,
  `source.txt` and `reference.txt`. Generated outputs are excluded on purpose — hashing them would
  make the hash a function of the run it is supposed to police.
- `contract_sha256` pins `eval/eval-contract.json`. A run planned under a different metric contract
  is rejected, not quietly compared (#44).
- `bubble_ids` is every ground-truth box. `requested_ids` is the subset whose source carries a letter
  or digit — the rest are punctuation-only bubbles the engine keeps verbatim without calling the
  model (`carriesText` in `TranslationEngine.kt`, mirrored in `run_records.py`).

### Arm metadata

```json
{
  "arm_id": "qwen25_1.5b", "stage": "translation",
  "provider": "llama.cpp", "provider_version": null,
  "model_id": "qwen25_1.5b_instruct_q4_k_m.gguf",
  "model_sha256": "…", "quantization": "Q4_K_M",
  "target_language": "en", "call_shape": "id_keyed_batch",
  "permits_fallback": false,
  "generation": { "temperature": 0.2, "penalty_repeat": 1.0 },
  "cases": ["…"]
}
```

- A repo-staged model artefact **requires** `model_sha256`. A managed provider (ML Kit, whose model
  Play services fetches on demand) may have a null hash **only** when `provider_version` records the
  provider package/model version.
- `call_shape` is one of `id_keyed_batch`, `per_line`, `per_bubble`, `page_image`.
- `generation` lists only the settings the run pins. The device records the full set; the scorer
  compares the declared keys.
- Actual prompt/completion **token counts are deliberately absent**: no runtime here returns them,
  and character lengths must not be called tokens. The configured `max_output_tokens` is recorded.

## `records.jsonl`

One JSON object per line, one **terminal** record per arm/case/stage invocation. Written as it is
produced, so a killed run loses only its unrun cases.

Every record carries:

| Field | |
| --- | --- |
| `run_id` | must equal the manifest's |
| `arm_id` | must be declared in the manifest |
| `case_id` | a manifest case, or `repetition-probe` |
| `stage` | `detection` \| `context_assembly` \| `translation` |
| `outcome` | see below |
| `duration_ms` | monotonic (`System.nanoTime`), not wall-clock |
| `error_code` | optional, machine-readable; never prose |

Per stage:

- **`detection`** adds `page_width`, `page_height`, `boxes` (`x`/`y`/`w`/`h`/`conf`),
  `nms_thresholded`, `nms_kept`, and `observed` naming the detector that ran.
- **`context_assembly`** adds `input_ids` and `output_block_ids` (one list per conversation block,
  in reading order).
- **`translation`** adds `requested_ids`, `results` (`[{bubble_id, text}]`, **raw provider output
  before `TranslationEngine` substitutes source text**), and `observed`.

`observed` is read at the execution boundary — for an LLM arm, off
`LlamaTranslationBridge.activeProfile` — and carries `provider`, `model_id`, `quantization`,
`target_language`, `call_shape`, `generation`. This duplicates the manifest **on purpose**:
manifest-only call-shape metadata is the provenance mistake ADR-0010 corrected.

### Outcomes

`success`, `blank`, `timeout`, `overflow`, `error` are **measured model outcomes**. The arm stays
valid and every expected id stays in the denominator. Detection finding zero boxes is likewise a
valid measured result whose recall fails.

`not_loaded` and `skipped_budget` mean **no model invocation happened**, so the requested arm is
invalidated. An unknown outcome invalidates the arm too.

`timeout` and `overflow` must stay **typed from the lowest boundary that knows the cause**
(`TranslationOutcome` in `core`, fed from `GenerationResult`). The harness never classifies them
from log or exception text. They are not yet distinguishable below the JNI, which collapses both to
an empty string and therefore to `blank`; #149 types them in `llama_jni.cpp` and they surface here
unchanged when it does.

## `COMPLETE`

```json
{ "run_id": "…", "record_count": 51, "records_sha256": "…",
  "arm_status": { "qwen25_1.5b": "complete", "opusmt": "incomplete" } }
```

Written by the host after extraction. A truncated extraction, or a records file appended to
afterwards, fails the count or the hash and the run is rejected.

## Validation

`run_records.validate_run` runs before any aggregate is computed.

**Fatal — nothing in the run is scoreable:** a missing or malformed manifest, records file or
completion marker; a `schema_version` mismatch; a run-id, record-count or records-hash mismatch; a
record carrying another run's id; a corpus or contract hash that no longer matches disk; a record
naming an arm the manifest never declared.

**Per arm — that arm is invalid, others still report:**

- a missing, duplicate, or unknown-stage record for a declared case;
- an unknown outcome, or `not_loaded` / `skipped_budget`;
- context `input_ids` that differ from the manifest's `bubble_ids`, blocks that do not cover them,
  or a context-assembly outcome other than `success`;
- `requested_ids` that differ from the manifest's (compared as a set: records are in manga reading
  order, the manifest in source-line order);
- observed `provider` / `model_id` / `quantization` / `target_language` / `generation` differing from
  the manifest's;
- an observed `call_shape` differing from the declared one (subject to the fallback rule below);
- a detection record that does not name the declared model.

**Measured failures, which do *not* invalidate:** a missing, extra, or duplicate returned translation
id. Those fail the **output-shape gate** while every expected id stays in the denominator. Discarding
the page instead is exactly what #41 did.

A valid arm stays reportable when another arm is invalid. An invalid arm gets **no aggregate at
all**, and `run-eval.py` exits nonzero when any requested arm is invalid.

### Declared per-line fallback (#146 amendment)

A page-level batch prompt that exceeds the decode budget falls back to `translatePerLine` for that
page, so one case legitimately observes two call shapes. The rule:

- translation records after an `overflow` for the same case are **expected**, and the arm stays valid
  and reportable as a degraded result;
- but only when the arm declared `permits_fallback: true`. An arm declaring no fallback that observes
  one is invalid;
- a second call shape with no preceding `overflow` is invalid, unchanged.

Disabling the fallback under eval was rejected in #146: it would make the harness measure something
production does not do, which is the shape of #36 and #58.

## `eval-contract.json`

The machine-readable metric registry: every metric the eval may report, the stage it belongs to,
whether it is gated, and the exact inputs it may read. `expected.json:boxes[].label` is listed under
`forbidden_inputs` — the labels are substring guesses from `generate-cases.py`, kept only for schema
stability (#44). chrF is registered as an explicitly **ungated diagnostic**.

The manifest carries this file's SHA-256. Changing the contract invalidates in-flight runs, which is
the intended cost of changing what the eval is allowed to measure.

## Running without a device

`eval/run-eval.py --stub` scores synthetic perfect outputs to exercise the scoring logic. It is the
only path that does not read a run directory, and it is labelled `"stub": true` in the results file.
