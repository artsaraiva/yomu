# Yomu Roadmap

This is the versioned product and engineering roadmap for Yomu. It is the contributor-facing source of truth.

- **GitHub milestones** track live delivery buckets.
- **GitHub issues** track unfinished, actionable work only.
- **Pull requests** implement current work and should use `Closes #N` when they complete an issue.
- `.slim/deepwork/` is local agent handoff state, not project documentation.

## Milestone policy

Completed milestones are closed from commit and merged-PR evidence. We do **not** create retrospective placeholder issues merely to populate past milestones. Create an issue only when work remains actionable or needs future tracking.

## Phase status

| Phase | Milestone | Status | Focus |
|---|---:|---|---|
| [0 — Manual Overlay Correctness](phase-0-manual-overlay.md) | #1 | Closed | Manual end-to-end baseline |
| [1 — Translation Engines & Performance](phase-1-translation-engines.md) | #2 | Engines operational | Default chosen (ML Kit), benchmark harness running, LLM fix pending merge |
| [2 — Observability & Live Logs](phase-2-observability.md) | #3 | Planned | In-app diagnostics |
| [3 — Auto-Translate & Detection](phase-3-auto-translate.md) | #4 | Blocked | Detection pulse and safe auto mode |
| [4 — Session & History](phase-4-session-history.md) | #5 | Partially delivered | History quality and context safety |
| [5 — UI & Navigation Polish](phase-5-ui-navigation.md) | #6 | In review | Simplified navigation, then visual polish |
| [6 — Model Management](phase-6-model-management.md) | #7 | In review | Engine assets now in Settings; advanced management later |
| [7 — Quality Evaluation Harness](phase-7-evaluation.md) | #8 | Operational | On-device benchmark running, results captured from logcat |
| [8 — Rendering & Typesetting](phase-8-rendering.md) | #9 | In progress | Legibility and overflow policy |
| [Infrastructure](infrastructure.md) | #10 | Planned | CI, review automation, dependency/security hygiene |

## Current priorities

1. Merge [PR #13](https://github.com/artsaraiva/yomu/pull/13): LLM native optimizations, chat template fix, benchmark infra. Closes [#11](https://github.com/artsaraiva/yomu/issues/11).
2. Investigate [#15](https://github.com/artsaraiva/yomu/issues/15): should we fine-tune CAT-Translate or replace the LLM model?
3. Resolve [#14](https://github.com/artsaraiva/yomu/issues/14): replace OPUS-MT with Android-compatible translation model.
4. Resolve [#6](https://github.com/artsaraiva/yomu/issues/6): implement manga auto-detect pulse before pursuing auto-translate.
5. Implement fine-tuning workflow [#12](https://github.com/artsaraiva/yomu/issues/12) after #15 resolves the model decision.
6. Add infrastructure issues [#1](https://github.com/artsaraiva/yomu/issues/1)–[#4](https://github.com/artsaraiva/yomu/issues/4).

## Delivery evidence

- Phase 0 baseline: `54b551e` and preceding foundational commits.
- Phase 1 engines, cache, and evaluation foundation: `fb67a88`, `4c3c3ef`, `51b6fc1`.
- Font scaling and quick overlay controls: `6d32c96`.
- Current overlay stability and information-architecture work: [PR #8](https://github.com/artsaraiva/yomu/pull/8), commit `08a0de9`.
- LLM native optimizations, benchmark infra, default engine decision: [PR #13](https://github.com/artsaraiva/yomu/pull/13).
