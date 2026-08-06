# Phase 0 — Manual Overlay Correctness

**Milestone:** #1 (closed)

## Goal

Prove that a user can manually capture the current screen, detect manga bubbles, run OCR and translation, and see positioned overlay text.

## Delivered

- Foreground overlay service and draggable floating button.
- MediaProjection-based screen capture.
- Bubble detection, OCR, translation-pipeline orchestration, and overlay coordinate mapping.
- Baseline ML Kit Japanese-to-English translation.
- Translation session/history persistence and manual pipeline progress/error behavior.

## Evidence

Delivered through the Phase 0 history, including `54b551e`.

## Closure policy

This milestone is closed from implementation evidence. No retrospective issues are needed because there is no unresolved Phase 0-only work.

## Follow-ups owned elsewhere

- Engine quality → [Phase 1](phase-1-translation-engines.md)
- Auto-detect pulse → [Phase 3](phase-3-auto-translate.md)
- Evaluation → [Phase 7](phase-7-evaluation.md)
- Text fitting → [Phase 8](phase-8-rendering.md)
