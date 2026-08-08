# Phase 7 — Quality Evaluation Harness

**Milestone:** #8

## Goal

Measure bubble-detection recall and translation quality instead of judging changes from isolated screenshots.

## Delivered foundation

- OpenMantra-based evaluation harness and derived case metadata.
- Bubble-detection scoring with IoU/recall and false-positive reporting.
- Translation scoring for untranslated output, artifacts, exact match, and readability heuristics.
- Stub/offline output support so device-generated engine results can be scored consistently.

## Evidence

Foundation delivered in `fb67a88`.

## Remaining work

- Collect real outputs from ML Kit, OPUS-MT, and CAT-Translate on device.
- Run comparisons to resolve [#7](https://github.com/artsaraiva/yomu/issues/7).
- Add difficult real-world cases: noisy OCR, slang, small bubbles, narration, SFX, and edge cases.
- Publish repeatable evaluation reports without shipping licensed source images in the app.

## Status

Foundation delivered; decision-grade results are pending.
