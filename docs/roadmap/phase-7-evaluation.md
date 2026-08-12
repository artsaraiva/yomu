# Phase 7 — Quality Evaluation Harness

**Milestone:** #8

## Goal

Measure bubble-detection recall and translation quality instead of judging changes from isolated screenshots.

## Delivered foundation

- OpenMantra-based evaluation harness and derived case metadata.
- Bubble-detection scoring with IoU/recall and false-positive reporting.
- Translation scoring for untranslated output, artifacts, exact match, and readability heuristics.
- Stub/offline output support so device-generated engine results can be scored consistently.
- A repeatable on-device runner is available under `eval/run-benchmark.sh`; each run preserves its log, raw artifacts, and scored output.

## Evidence

Foundation delivered in `fb67a88`.

## Remaining work

- Collect OPUS-MT outputs once Android tokenizer issue is resolved ([#14](https://github.com/artsaraiva/yomu/issues/14)).
- Re-run comparisons after LLM model decision ([#15](https://github.com/artsaraiva/yomu/issues/15)).
- Add difficult real-world cases: noisy OCR, slang, small bubbles, narration, SFX, and edge cases.
- Publish repeatable evaluation reports without shipping licensed source images in the app.

## Status

Benchmark harness operational: `./eval/run-benchmark.sh` runs on-device instrumentation, captures results from logcat, scores with `run-eval.py`. ML Kit and CAT-Translate baseline collected. OPUS-MT pending Android tokenizer fix. Fine-tuned models tracked in [#12](https://github.com/artsaraiva/yomu/issues/12).
