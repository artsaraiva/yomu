# Yomu Roadmap

This is the versioned product and engineering roadmap for Yomu. It is the contributor-facing source of truth.

- **GitHub milestones** track live delivery buckets.
- **GitHub issues** track unfinished, actionable work only.
- **Pull requests** implement current work and should use `Closes #N` when they complete an issue.
- `.slim/deepwork/` is local agent handoff state, not project documentation.

## Milestone policy

Completed milestones are closed from commit and merged-PR evidence. We do **not** create retrospective placeholder issues merely to populate past milestones. Create an issue only when work remains actionable or needs future tracking.

## Phase status

The reset sequence is intentional: establish one functional path, measure it, then make model, training, reliability, detection, product, business, and infrastructure decisions in order.

| Phase | Milestone | Status | Focus |
|---|---:|---|---|
| [0 — Manual Overlay Correctness](phase-0-manual-overlay.md) | #1 | Closed | Historical manual end-to-end baseline |
| [1 — Functional Translation Stack](phase-1-functional-translation-stack.md) | #11 | Current foundation | One usable Japanese-to-English vertical path |
| [2 — Quality Evaluation & Benchmarking](phase-2-quality-evaluation.md) | #12 | Next | Evidence before model decisions |
| [3 — Local Model Selection & Management](phase-3-local-models.md) | #13 | Planned | Model assets, selection, and lifecycle |
| [4 — Training Pipelines](phase-4-training-pipelines.md) | #14 | Planned | Training owned by `yomu-training` |
| [5 — Reliability, Observability & History](phase-5-reliability-history.md) | #15 | Planned | Durable operation and useful diagnostics |
| [6 — Automatic Detection](phase-6-automatic-detection.md) | #16 | Planned | Safe manga-region detection and auto mode |
| [7 — UI/UX Redesign](phase-7-ui-ux-redesign.md) | #17 | Planned | Paper Mario / paper-mache visual direction |
| [8 — Hybrid Providers & Monetization](phase-8-hybrid-monetization.md) | #18 | Planned | Local/cloud product and business model |
| [9 — Continuous Release Infrastructure](phase-9-release-infrastructure.md) | #19 | Planned | Continuous delivery and maintenance |

## Current priorities

Phase 1 is the active delivery target. Phase 2 must produce comparable quality and latency evidence before Phase 3 model choices or Phase 4 training work. Issues belong to the phase that owns the behavior; training issues belong in `yomu-training`, while app integration and release issues remain in Yomu.

## Delivery evidence

- Phase 0 baseline: `54b551e` and preceding foundational commits.
- Existing translation bridges, cache, and benchmark work are evidence for Phase 1 and Phase 2; they do not by themselves close either reset phase.
- Font scaling and quick overlay controls: `6d32c96`.
- Current overlay stability and information-architecture work: [PR #8](https://github.com/artsaraiva/yomu/pull/8), commit `08a0de9`.
- Earlier PRs and commits remain historical evidence, not broad completion claims.
