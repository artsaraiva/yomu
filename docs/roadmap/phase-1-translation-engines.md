# Phase 1 — Translation Engines & Performance

**Milestone:** #2

## Goal

Turn translation into a selectable capability rather than a hard-wired ML Kit experiment.

## Delivered

- Engine selection for ML Kit, OPUS-MT ONNX, and CAT-Translate LLM.
- OPUS-MT ONNX integration and tokenizer support.
- CAT-Translate decode-path repair: realistic deadline, chat template, batching, warm context, and cache handling.
- Persistent translation cache keyed by engine/model/normalized source text.
- Async OCR-first overlay rendering.
- Settings-level engine selector and model status foundations.
- Native optimizations: NEON dotprod SIMD + -O2 compiler flags, prompt template fix.
- On-device benchmark harness with logcat-based results capture.

## Evidence

`fb67a88`, `4c3c3ef`, `51b6fc1`. Benchmark results: [PR #13](https://github.com/artsaraiva/yomu/pull/13).

## Current status

Default engine chosen: **ML Kit** (23ms/line, best quality). CAT-Translate works at 1500ms/line but quality is poor for manga — needs domain investigation. OPUS-MT blocked by missing Android tokenizer native library.

## Remaining work

- Resolve [#15](https://github.com/artsaraiva/yomu/issues/15): investigate whether to fine-tune or replace the LLM model.
- Resolve [#14](https://github.com/artsaraiva/yomu/issues/14): replace OPUS-MT with Android-compatible alternative.
- Validate OPUS-MT alternative on target devices.

## Fine-tuning boundary

Fine-tuning is intentionally outside the Android app and the base-engine benchmark. The separate [`artsaraiva/yomu-training`](https://github.com/artsaraiva/yomu-training) repository owns dataset preparation, training, export/quantization, model publishing, and reproducible checkpoint evaluation. Yomu consumes only a versioned model artifact with a checksum. Track the workflow in [#12](https://github.com/artsaraiva/yomu/issues/12) after the base CAT-Translate benchmark establishes a comparison baseline.
