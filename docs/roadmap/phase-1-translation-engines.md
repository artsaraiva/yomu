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

## Evidence

`fb67a88`, `4c3c3ef`, and `51b6fc1`.

## Current status

Core implementation is delivered, but the default-engine decision remains open. PR #10 adds repeatable on-device comparison; the result still depends on a local device run with downloaded models. CAT-Translate remains experimental until its runtime and quality meet the device budget.

## Remaining work

- Resolve [#7](https://github.com/artsaraiva/yomu/issues/7) with measured engine quality and latency.
- Resolve [#11](https://github.com/artsaraiva/yomu/issues/11) if CAT-Translate still exceeds the interactive latency budget after the bounded-generation fix.
- Choose a default engine from evidence; likely keep CAT-Translate behind an experimental path unless it meets the device budget.
- Validate OPUS-MT model asset download and readiness on target devices.

## Fine-tuning boundary

Fine-tuning is intentionally outside the Android app and the base-engine benchmark. The separate [`artsaraiva/yomu-training`](https://github.com/artsaraiva/yomu-training) repository owns dataset preparation, training, export/quantization, model publishing, and reproducible checkpoint evaluation. Yomu consumes only a versioned model artifact with a checksum. Track the workflow in [#12](https://github.com/artsaraiva/yomu/issues/12) after the base CAT-Translate benchmark establishes a comparison baseline.
