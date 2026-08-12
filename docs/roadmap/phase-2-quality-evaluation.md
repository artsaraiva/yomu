# Phase 2 — Quality Evaluation & Benchmarking

**Status:** Next
**Owner:** Yomu app, with reproducible evaluation assets
**Milestone:** #12

## Goal
Measure translation quality, OCR impact, latency, memory, and failure modes before selecting or replacing local models.

## Scope
Create a small representative corpus, repeatable on-device benchmarks, human-readable result capture, and comparison criteria for the current engines. Keep evaluation separate from model-management and training delivery.

## Exit criteria
Results are reproducible enough to identify the current bottlenecks and define acceptance thresholds and a documented model decision.

## Dependencies and issue ownership
Depends on the Phase 1 vertical path. Benchmark and app instrumentation issues belong to Yomu; corpus/training follow-ups may be handed to `yomu-training` after the decision.
