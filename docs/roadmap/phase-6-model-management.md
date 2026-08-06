# Phase 6 — Model Management

**Milestone:** #7

## Goal

Make translation-engine assets understandable and manageable without a dedicated, confusing download-only screen.

## Current delivery

[PR #8](https://github.com/artsaraiva/yomu/pull/8) moves engine-specific model state into Settings:

- ML Kit is presented as the baseline.
- OPUS-MT is presented as the local-first target.
- Qwen is labeled experimental and slow.

## Remaining work

- Finish real OPUS-MT asset download/readiness UX.
- Add custom GGUF/ONNX file import.
- Add Hugging Face or manifest-backed downloads with checksum verification.
- Add model compatibility checks and a model health/latency test command.

## Status

In review for the Settings consolidation; advanced model management is planned.
