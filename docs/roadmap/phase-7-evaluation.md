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

- Collect real outputs from ML Kit, OPUS-MT, and CAT-Translate on device. OPUS-MT may remain skipped until its model asset is available.
- Run comparisons to resolve [#7](https://github.com/artsaraiva/yomu/issues/7).
- Add difficult real-world cases: noisy OCR, slang, small bubbles, narration, SFX, and edge cases.
- Publish repeatable evaluation reports without shipping licensed source images in the app.

## Status

Foundation delivered; decision-grade results are pending a successful local device run with ML Kit and CAT-Translate. Fine-tuned models are a later comparison tracked in [#12](https://github.com/artsaraiva/yomu/issues/12), not part of the base benchmark.
