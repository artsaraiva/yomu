# Phase 1 — Functional Translation Stack

**Status:** Current foundation
**Owner:** Yomu app
**Milestone:** #11

## Goal
Deliver one reliable Japanese-to-English vertical path from manual capture through OCR, translation, and positioned overlay output.

## Scope
Stabilize the existing pipeline, translation bridge selection, readiness/error states, caching, and a usable manual single-page flow. Do not choose a long-term model or add automatic detection here.

## Exit criteria
The path works on a representative device and supported manga inputs, failures are surfaced without data loss, and baseline latency/output evidence is recorded for Phase 2.

## Dependencies and issue ownership
Builds on Phase 0 and existing translation bridges. App behavior belongs to Yomu; model-quality decisions wait for Phase 2.
