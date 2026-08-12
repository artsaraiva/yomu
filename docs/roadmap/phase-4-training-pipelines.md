# Phase 4 — Training Pipelines

**Status:** Planned
**Owner:** `yomu-training`
**Milestone:** #14 in Yomu; #1 in `yomu-training`

## Goal
Turn evaluation findings into reproducible, maintainable training and export workflows.

## Scope
Dataset preparation, fine-tuning experiments, evaluation integration, model export/quantization, provenance, and handoff artifacts. The repository boundary is deliberate: training is owned by `yomu-training`.

## Exit criteria
An experiment can be reproduced, evaluated against Phase 2 criteria, and handed to Yomu with versioned artifacts and compatibility notes.

## Dependencies and issue ownership
Depends on Phase 2 evidence and Phase 3 model contracts. Training issues belong to `yomu-training`; app consumption issues belong to Yomu.
