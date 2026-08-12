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

- Functional translation stack → [Phase 1](phase-1-functional-translation-stack.md)
- Evaluation → [Phase 2](phase-2-quality-evaluation.md)
- Auto-detect pulse → [Phase 6](phase-6-automatic-detection.md)
- Text fitting and visual language → [Phase 7](phase-7-ui-ux-redesign.md)
