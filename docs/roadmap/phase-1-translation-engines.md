# Phase 1 — Translation Engines & Performance

**Milestone:** #2

## Goal

Turn translation into a selectable capability rather than a hard-wired ML Kit experiment.

## Delivered

- Engine selection for ML Kit, OPUS-MT ONNX, and Qwen LLM.
- OPUS-MT ONNX integration and tokenizer support.
- Qwen decode-path repair: realistic deadline, chat template, batching, warm context, and cache handling.
- Persistent translation cache keyed by engine/model/normalized source text.
- Async OCR-first overlay rendering.
- Settings-level engine selector and model status foundations.

## Evidence

`fb67a88`, `4c3c3ef`, and `51b6fc1`.

## Current status

Core implementation is delivered, but the default-engine decision remains open. Qwen is explicitly experimental: it remains slow and low-quality in real use despite runtime fixes.

## Remaining work

- Resolve [#7](https://github.com/artsaraiva/yomu/issues/7) with measured engine quality and latency.
- Choose a default engine from evidence; likely keep Qwen behind an experimental path unless it meets the device budget.
- Validate OPUS-MT model asset download and readiness on target devices.
